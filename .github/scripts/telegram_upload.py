import asyncio
import html
import os
import pathlib
import subprocess
import sys

CAPTION_LIMIT = 1024
MAX_COMMITS = 30
SECTIONS = (
    ("✨ What's new?", ("Added", "Add", "Implemented", "Introduced")),
    ("🐞 Bug fixes", ("Fixed", "Fix")),
    ("🔧 Improvements", ()),
)
HIDDEN = ("Version Bump", "Merge ")


def git(*args: str) -> str:
    return subprocess.run(["git", *args], capture_output=True, text=True, check=False).stdout.strip()


def stable(tag: str) -> bool:
    return "-" not in tag


def tag_notes(tag: str) -> list[str] | None:
    if git("cat-file", "-t", f"refs/tags/{tag}") != "tag":
        return None
    text = git("tag", "-l", "--format=%(contents)", tag).split("-----BEGIN PGP")[0].strip()
    return text.splitlines() if text else None


def commit_notes(tag: str, kind: str = "") -> list[str]:
    only_stable = ["--exclude", "*-*"] if kind == "stable" or (not kind and stable(tag)) else []
    previous = git("describe", "--tags", "--abbrev=0", *only_stable, f"{tag}^")
    target = f"{previous}..{tag}" if previous else tag
    subjects = [
        s for s in git("log", target, "--no-merges", f"--max-count={MAX_COMMITS}", "--format=%s").splitlines()
        if not s.startswith(HIDDEN)
    ]
    lines: list[str] = []
    taken: set[str] = set()
    for title, verbs in SECTIONS:
        picked = [s for s in subjects if s not in taken and (not verbs or s.split(" ", 1)[0] in verbs)]
        if not picked:
            continue
        taken.update(picked)
        if lines:
            lines.append("")
        lines.append(title)
        lines.extend(f"• {s}" for s in picked)
    return lines


def caption(version: str, tag: str) -> str:
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    sha = os.environ.get("GITHUB_SHA") or git("rev-parse", "HEAD")
    commit = f'<a href="https://github.com/{repo}/commit/{sha}">{sha[:7]}</a>'
    release = f"https://github.com/{repo}/releases/tag/{tag}"
    if stable(tag):
        head = f"🚀 RenVault {html.escape(version, quote=False)} #stable ({commit})"
    else:
        head = f"🥚 Beta {html.escape(version, quote=False)} #beta ({commit})"
    body = tag_notes(tag) or commit_notes(tag)

    def build(lines: list[str], cut: int) -> str:
        text = "\n".join([head, "", *(html.escape(line, quote=False) for line in lines)])
        if cut:
            text += f'\n… <a href="{release}">{cut} more</a>'
        return text

    lines = list(body)
    cut = 0
    while lines and len(build(lines, cut)) > CAPTION_LIMIT:
        lines.pop()
        cut += 1
    return build(lines, cut)


def release_notes(version: str, tag: str) -> str:
    return "\n".join([f"RenVault {version}", "", *(tag_notes(tag) or commit_notes(tag))])


async def send(folder: str, version: str, tag: str) -> None:
    from telethon import TelegramClient, utils
    from telethon.sessions import StringSession

    files = sorted(str(path) for path in pathlib.Path(folder).iterdir() if path.is_file())
    captions = [""] * (len(files) - 1) + [caption(version, tag)]

    client = TelegramClient(StringSession(), int(os.environ["TELEGRAM_API_ID"]), os.environ["TELEGRAM_API_HASH"])
    await client.start(bot_token=os.environ["TELEGRAM_BOT_TOKEN"])
    async with client:
        chat_id, peer = utils.resolve_id(int(os.environ["TELEGRAM_CHAT_ID"]))
        chat = await client.get_input_entity(peer(chat_id))
        uploads = await asyncio.gather(*(client.upload_file(path) for path in files))
        await client.send_file(chat, uploads, caption=captions, parse_mode="html", force_document=True)


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    if command == "draft":
        print("\n".join(commit_notes(*args)))
    elif command == "notes":
        print(release_notes(*args))
    elif command == "preview":
        print(caption(*args))
    else:
        asyncio.run(send(*args))
