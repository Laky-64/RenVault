<script lang="ts">
    import Button from "./Button.svelte";
    import {CHEVRON} from "../lib/icons";

    const {
        label,
        value = '',
        chevron = false,
        tone = 'plain',
        edge = 'middle',
        onclick,
    } : {
        label: string;
        value?: string;
        chevron?: boolean;
        tone?: 'plain' | 'accent' | 'danger';
        edge?: 'top' | 'bottom' | 'middle' | 'only';
        onclick?: () => void;
    } = $props();

    const CORNERS = 'var(--zone-border-radius)';
    const radius = $derived(
        edge === 'only' ? CORNERS
            : edge === 'top' ? `${CORNERS} ${CORNERS} 0 0`
                : edge === 'bottom' ? `0 0 ${CORNERS} ${CORNERS}`
                    : '0',
    );
</script>

{#snippet body()}
    <div class="row">
        <span class="label" class:accent={tone === 'accent'} class:danger={tone === 'danger'}>{label}</span>
        {#if value}<span class="value">{value}</span>{/if}
        {#if chevron}
            <svg class="chevron" viewBox="0 -960 960 960" width="15" height="15" aria-hidden="true">
                <path d={CHEVRON} fill="var(--subtitle-text-color)"/>
            </svg>
        {/if}
    </div>
{/snippet}

{#if onclick}
    <Button block variant="plain" padding="0" {radius} {onclick}>
        {@render body()}
    </Button>
{:else}
    {@render body()}
{/if}

<style>
    .row {
        display: flex;
        align-items: center;
        gap: 10px;
        width: 100%;
        min-height: 48px;
        padding: 6px 15px;
    }

    .label {
        flex: 1;
        min-width: 0;
        text-align: left;
        font-size: 15px;
        color: var(--text-color);
    }

    .label.accent {
        color: var(--accent-text-color);
    }

    .label.danger {
        color: var(--destructive-text-color);
    }

    .value {
        flex-shrink: 0;
        font-size: 15px;
        color: var(--subtitle-text-color);
    }

    .chevron {
        flex-shrink: 0;
        rotate: 180deg;
    }
</style>
