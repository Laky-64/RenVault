<script lang="ts">
    import Button from "./Button.svelte";
    import {popIn, type PopAlign} from "../lib/motion";

    const {
        shown = true,
        align = 'start',
        padding = '8px',
        label,
        accent,
        morph,
        flash,
        disabled = false,
        dim = true,
        onclick,
        children,
    } : {
        shown?: boolean;
        align?: PopAlign;
        padding?: string;
        label?: string;
        accent?: string;
        morph?: unknown;
        flash?: unknown;
        disabled?: boolean;
        dim?: boolean;
        onclick: () => void;
        children?: any;
    } = $props();
</script>

{#if shown}
    <div class="dock" class:end={align === 'end'} transition:popIn={{align}}>
        <span class="keep">
            <Button variant="glass" {padding} radius="999px" {accent} {morph} {flash} {disabled} {dim} {onclick}>
                {#if label}
                    <span class="label">{label}</span>
                {:else}
                    {@render children?.()}
                {/if}
            </Button>
        </span>
    </div>
{/if}

<style>
    .dock {
        display: flex;
        flex-shrink: 0;
    }

    .dock.end {
        justify-content: flex-end;
    }

    .keep {
        display: flex;
        flex: 0 0 auto;
    }

    .label {
        display: block;
        padding-inline: 6px;
        height: 20px;
        line-height: 20px;
        font-size: 15px;
        font-weight: 500;
        color: var(--text-color);
        white-space: nowrap;
    }
</style>
