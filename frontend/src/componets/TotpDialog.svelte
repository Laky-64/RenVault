<script lang="ts">
    import DialogSurface from "./DialogSurface.svelte";
    import Button from "./Button.svelte";
    import FieldCard from "./FieldCard.svelte";
    import {m} from "../paraglide/messages";

    let {
        open = $bindable(false),
        active = $bindable(false),
        domain,
        on_key,
    } : {
        open?: boolean;
        active?: boolean;
        domain: string;
        on_key?: (key: string) => void;
    } = $props();

    let key = $state('');

    $effect(() => {
        if (open) key = '';
    });

    const ready = $derived(key.length > 0);

    function use() {
        on_key?.(key);
        open = false;
    }
</script>

<DialogSurface bind:open bind:active compactSheet={false}>
    <div class="dialog" role="dialog" aria-modal="true" aria-label={m.totp_title()}>
        <p class="heading">{m.totp_title()}</p>
        <p class="desc">{m.totp_desc({domain})}</p>

        <FieldCard radius="100px">
            <input
                bind:value={key}
                placeholder={m.totp_key()}
                autocomplete="off"
                spellcheck="false"
                autocapitalize="characters"
            />
        </FieldCard>

        <div class="actions">
            <Button variant="ghost" radius="100px" padding="13px" block disabled={!ready} onclick={use}>
                <span class="label">{m.totp_use()}</span>
            </Button>
            <Button variant="ghost" radius="100px" padding="13px" block onclick={() => (open = false)}>
                <span class="label">{m.editor_cancel()}</span>
            </Button>
        </div>
    </div>
</DialogSurface>

<style>
    .dialog {
        display: flex;
        flex-direction: column;
        width: 320px;
        max-width: calc(100vw - 48px);
        padding: 16px;
        gap: 10px;
    }

    .heading {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: var(--text-color);
    }

    .desc {
        margin: 0 0 6px;
        font-size: 13px;
        line-height: 1.35;
        color: var(--hint-color);
    }

    input {
        width: 100%;
        height: 46px;
        padding-inline: 20px;
        border: none;
        background: transparent;
        font-family: inherit;
        font-size: 15px;
        font-weight: 500;
        letter-spacing: normal;
        color: var(--text-color);
        caret-color: var(--button-color);
        --wails-draggable: no-drag;
    }

    input:focus {
        outline: none;
    }

    input::placeholder {
        font-weight: normal;
        color: var(--hint-color);
    }

    .actions {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-top: 4px;
    }

    .label {
        font-size: 15px;
        font-weight: 500;
        color: var(--text-color);
    }
</style>
