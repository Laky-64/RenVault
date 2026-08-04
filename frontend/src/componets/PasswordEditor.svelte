<script lang="ts">
    import MorphDialog from "./MorphDialog.svelte";
    import PasswordIcon from "./PasswordIcon.svelte";
    import Button from "./Button.svelte";
    import RevealToggle from "./RevealToggle.svelte";
    import {bareHost, domainOf, isDomain} from "../lib/items";
    import {generatePassword} from "../lib/password";
    import {m} from "../paraglide/messages";
    import {CHECK, CLOSE} from "../lib/icons";

    export interface Draft {
        title: string;
        username: string;
        password: string;
        website: string;
        notes: string;
    }

    let {
        open = $bindable(false),
        active = $bindable(false),
        anchor,
        seed,
        on_save,
    } : {
        open?: boolean;
        active?: boolean;
        anchor?: HTMLElement;
        seed?: import('svelte').Snippet;
        on_save?: (draft: Draft) => void;
    } = $props();

    let draft: Draft = $state({title: '', username: '', password: '', website: '', notes: ''});
    let secretShown = $state(false);

    $effect(() => {
        if (open) {
            draft = {title: '', username: '', password: generatePassword(), website: '', notes: ''};
            secretShown = true;
        }
    });

    const masked = $derived(!secretShown && draft.password.length > 0);

    const domain = $derived(domainOf(draft.website));
    const typed = $derived(bareHost(draft.website));
    const hasWebsite = $derived(draft.website.trim().length > 0);
    const websiteOk = $derived(hasWebsite ? isDomain(typed) : draft.title.trim().length > 0);
    const canSave = $derived(websiteOk
        && (draft.password.length > 0 || draft.username.trim().length > 0));

    function tidyWebsite() {
        draft.website = typed;
    }

    function save() {
        if (!canSave) return;
        on_save?.({...draft, website: domain});
        open = false;
    }
</script>

<MorphDialog bind:open bind:active {anchor} {seed}>
    {#snippet children(stack: boolean)}
        <div class="editor" class:stack role="dialog" aria-modal="true" aria-label={m.editor_newPassword()}>
            <div class="bar">
                <Button variant="glass" padding="8px" radius="999px" onclick={() => (open = false)}>
                    <svg viewBox="0 -960 960 960" width="20" height="20" role="img" aria-label={m.editor_cancel()}>
                        <path d={CLOSE} fill="var(--text-color)"/>
                    </svg>
                </Button>
                <p class="heading">{m.editor_newPassword()}</p>
                <Button variant="glass" accent="var(--button-color)" padding="8px" radius="999px"
                        disabled={!canSave} onclick={save}>
                    <svg viewBox="0 -960 960 960" width="20" height="20" role="img" aria-label={m.editor_save()}>
                        <path d={CHECK} fill="var(--button-text-color)"/>
                    </svg>
                </Button>
            </div>

            <div class="card">
                <PasswordIcon icon={{source: 'favicon', domain, fallback: draft.title || '?'}} width="50px"/>
                <input class="title" bind:value={draft.title} placeholder={m.editor_title()}
                       autocomplete="off" spellcheck="false"/>

                <div class="fields">
                    <label class="field">
                        <span class="name">{m.field_username()}</span>
                        <input bind:value={draft.username} placeholder={m.editor_usernameHint()}
                               autocomplete="off" spellcheck="false"/>
                    </label>
                    <label class="field">
                        <span class="name">{m.field_password()}</span>
                        <input class:masked-text={masked} type={secretShown ? 'text' : 'password'}
                               bind:value={draft.password} autocomplete="new-password"/>
                        <RevealToggle bind:shown={secretShown} padding="0 0 0 10px"/>
                    </label>
                    <label class="field">
                        <span class="name">{m.field_website()}</span>
                        <input bind:value={draft.website} placeholder={m.editor_websiteHint()}
                               autocomplete="off" spellcheck="false" onblur={tidyWebsite}/>
                    </label>
                    <label class="field notes">
                        <textarea bind:value={draft.notes} rows="3" placeholder={m.editor_notes()}></textarea>
                    </label>
                </div>
            </div>
        </div>
    {/snippet}
</MorphDialog>

<style>
    .editor {
        display: flex;
        flex-direction: column;
        width: 420px;
        max-width: 100vw;
        padding: 12px;
        gap: 12px;
    }

    .editor.stack {
        width: 100%;
        height: 100%;
    }

    .bar {
        display: flex;
        align-items: center;
        gap: 8px;
        flex-shrink: 0;
    }

    .heading {
        flex: 1;
        margin: 0;
        text-align: center;
        font-size: 15px;
        font-weight: 600;
        color: var(--text-color);
    }

    .card {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 15px;
        border-radius: var(--section-border-radius);
        background: var(--bg-color);
    }

    .title {
        width: 100%;
        margin: 10px 0 5px;
        padding-inline: 4px;
        border: none;
        background: transparent;
        text-align: center;
        font-family: inherit;
        font-size: 22px;
        font-weight: bold;
        color: var(--text-color);
    }

    .fields {
        display: flex;
        flex-direction: column;
        width: 100%;
    }

    .field {
        position: relative;
        display: flex;
        align-items: center;
        width: 100%;
        padding-block: 12px;
    }

    .field:not(:last-child)::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 1px;
        background: var(--hairline-color);
    }

    .name {
        color: var(--text-color);
        margin-right: 15px;
        font-size: 15px;
        flex-shrink: 0;
    }

    input,
    textarea {
        min-width: 0;
        border: none;
        background: transparent;
        font-family: inherit;
        font-size: 15px;
        font-weight: 500;
        color: var(--text-color);
        caret-color: var(--button-color);
        --wails-draggable: no-drag;
    }

    input:not(.masked-text),
    textarea {
        letter-spacing: normal;
    }

    .field > input {
        flex: 1;
        text-align: right;
        padding-right: 4px;
    }

    .notes > textarea {
        width: 100%;
        padding-right: 4px;
        resize: none;
        line-height: 1.35;
    }

    input:focus,
    textarea:focus {
        outline: none;
    }

    input::placeholder,
    textarea::placeholder {
        color: var(--hint-color);
    }
</style>
