#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

bold=$'\e[1m' dim=$'\e[2m' green=$'\e[32m' yellow=$'\e[33m' red=$'\e[31m' reset=$'\e[0m'
say() { printf '%s\n' "$*"; }
fail() { say "${red}✗ $*${reset}" >&2; exit 1; }

[ -z "$(git status --porcelain)" ] || fail "Uncommitted changes, commit or stash them first."

branch=$(git rev-parse --abbrev-ref HEAD)
git fetch --quiet --tags origin "$branch" || fail "Could not reach origin."
behind=$(git rev-list --count "HEAD..origin/$branch" 2>/dev/null || echo 0)
ahead=$(git rev-list --count "origin/$branch..HEAD" 2>/dev/null || echo 0)
[ "$behind" = 0 ] || fail "$branch is $behind commit(s) behind origin, pull first."

base=$(sed -n 's/^renvault\.version=//p' gradle.properties | tr -d '\r')
last=$(git tag -l "v$base-beta.*" | sed "s/^v$base-beta\.//" | sort -n | tail -1)

say "${bold}🏷️  RenVault release tag${reset}"
say "${dim}Branch $branch · HEAD $(git rev-parse --short HEAD) · version $base${reset}"
[ "$ahead" = 0 ] || say "${yellow}• $ahead local commit(s) will be pushed together with the tag${reset}"
say
say "  1) 🥚 Beta"
say "  2) 🚀 Stable"
read -r -p "Channel [1]: " channel
case "${channel:-1}" in
  1) kind=beta suggested="v$base-beta.$(( ${last:-0} + 1 ))" ;;
  2) kind=stable suggested="v$base" ;;
  *) fail "Unknown channel." ;;
esac
read -r -p "Tag [${suggested}]: " tag
tag=${tag:-$suggested}
[[ "$tag" == v* ]] || tag="v$tag"
git rev-parse -q --verify "refs/tags/$tag" > /dev/null && fail "$tag already exists."
if [ "$kind" = stable ] && [[ "$tag" == *-* ]]; then fail "A stable tag can't contain a suffix like -beta."; fi
if [ "$kind" = beta ] && [[ "$tag" != *-* ]]; then fail "A beta tag needs a suffix like -beta.1."; fi

draft=$(mktemp)
trap 'rm -f "$draft"' EXIT
{
  python3 .github/scripts/telegram_upload.py draft HEAD "$kind"
  say
  say "# Write the notes testers will see for $tag."
  say "# Lines starting with # are ignored. Leave the file empty to abort."
} > "$draft"

"${VISUAL:-${EDITOR:-nano}}" "$draft"
notes=$(grep -v '^#' "$draft" | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')
[ -n "$(printf '%s' "$notes" | tr -d '[:space:]')" ] || fail "Empty notes, nothing tagged."

say
say "${bold}Preview${reset}"
say "${dim}────────────────────────────────────────${reset}"
if [ "$kind" = stable ]; then
  say "🚀 RenVault ${tag#v} #stable ($(git rev-parse --short HEAD))"
else
  say "🥚 Beta ${tag#v} #beta ($(git rev-parse --short HEAD))"
fi
say
say "$notes"
say "${dim}────────────────────────────────────────${reset}"
say
read -r -p "Create and push $tag? [y/N] " answer
[[ "$answer" =~ ^[YySs]$ ]] || fail "Aborted, nothing tagged."

git tag -a "$tag" -F <(printf '%s\n' "$notes")
[ "$ahead" = 0 ] || git push --quiet origin "$branch"
git push --quiet origin "$tag"

repo=$(git remote get-url origin | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')
say "${green}✓ $tag pushed, the build is starting${reset}"
[ "$kind" = beta ] || say "${dim}Next: bump renvault.version in gradle.properties for the next cycle${reset}"
say "${dim}https://github.com/$repo/actions${reset}"
