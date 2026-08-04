<script lang="ts">
    import DialogSurface from "./DialogSurface.svelte";
    import Button from "./Button.svelte";
    import {m} from "../paraglide/messages";

    let {
        open = $bindable(false),
        active = $bindable(false),
        title,
        desc,
        confirm,
        danger = false,
        on_confirm,
    } : {
        open?: boolean;
        active?: boolean;
        title: string;
        desc: string;
        confirm: string;
        danger?: boolean;
        on_confirm?: () => void;
    } = $props();

    function run() {
        open = false;
        on_confirm?.();
    }
</script>

<DialogSurface bind:open bind:active compactSheet={false}>
    <div class="dialog" role="alertdialog" aria-modal="true" aria-label={title}>
        <p class="heading">{title}</p>
        <p class="desc">{desc}</p>

        <div class="actions">
            <Button variant="ghost" radius="100px" padding="13px" block onclick={run}>
                <span class="label" class:danger>{confirm}</span>
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

    .actions {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    .label {
        font-size: 15px;
        font-weight: 500;
        color: var(--text-color);
    }

    /*noinspection CssUnusedSymbol*/
    .label.danger {
        color: var(--destructive-text-color);
    }
</style>
