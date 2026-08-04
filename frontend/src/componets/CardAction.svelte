<script lang="ts">
    import type {Snippet} from "svelte";
    import Button from "./Button.svelte";

    const {
        text = '',
        danger = false,
        centred = false,
        edge = 'top',
        padding = '15px',
        inset = '15px',
        radius,
        onclick,
        children,
    } : {
        text?: string;
        danger?: boolean;
        centred?: boolean;
        edge?: 'top' | 'bottom' | 'none';
        padding?: string;
        inset?: string;
        radius?: string;
        onclick?: () => void;
        children?: Snippet;
    } = $props();

    const CORNERS = 'var(--zone-border-radius) var(--zone-border-radius)';
    const corner = $derived(radius ?? (edge === 'top' ? `0 0 ${CORNERS}` : `${CORNERS} 0 0`));
</script>

<div class="slot" class:top={edge === 'top'} class:bottom={edge === 'bottom'}
     style="--inset: {inset}">
    <Button
        block
        variant="plain"
        {padding}
        radius={corner}
        {onclick}
    >
        {#if children}
            {@render children()}
        {:else}
            <span class="action" class:danger class:centred>{text}</span>
        {/if}
    </Button>
</div>

<style>
    .slot {
        display: flex;
        position: relative;
        width: 100%;
    }

    /*noinspection CssUnusedSymbol*/
    .slot.top::before,
    .slot.bottom::after {
        content: '';
        position: absolute;
        left: var(--inset);
        right: var(--inset);
        height: 1px;
        background: var(--hairline-color);
    }

    /*noinspection CssUnusedSymbol*/
    .slot.top::before {
        top: 0;
    }

    /*noinspection CssUnusedSymbol*/
    .slot.bottom::after {
        bottom: 0;
    }

    .action {
        flex: 1;
        font-size: 15px;
        text-align: left;
        color: var(--accent-text-color);
        white-space: nowrap;
    }

    /*noinspection CssUnusedSymbol*/
    .action.danger {
        color: var(--destructive-text-color);
    }

    /*noinspection CssUnusedSymbol*/
    .action.centred {
        text-align: center;
    }
</style>
