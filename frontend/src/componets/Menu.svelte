<script lang="ts">
    import type {Snippet} from "svelte";
    import MorphSurface from "./MorphSurface.svelte";
    import {type MenuPlacement, type MenuSection} from "../lib/menu";
    import {type ContentAnchor, nearAnchor} from "../lib/morph";

    let {
        open = $bindable(false),
        active = $bindable(false),
        anchor,
        placement = 'top-start',
        sections,
        seed,
        on_select,
    } : {
        open?: boolean;
        active?: boolean;
        anchor?: HTMLElement;
        placement?: MenuPlacement;
        sections: MenuSection[];
        seed?: Snippet;
        on_select?: (id: string) => void;
    } = $props();

    const place = $derived(nearAnchor(placement));
    const contentAnchor = $derived(
        (placement.startsWith('top') ? 'bottom' : 'top')
        + (placement.endsWith('end') ? '-end' : '-start') as ContentAnchor);

    function pick(id: string) {
        on_select?.(id);
        open = false;
    }
</script>

<MorphSurface bind:open bind:active {anchor} {place} {contentAnchor} {seed} surface="glass-surface" pressScale>
    <div class="items" role="menu" tabindex="-1">
        {#each sections as section, index}
            {#if index > 0}
                <div class="divider"></div>
            {/if}
            {#each section as item (item.id)}
                <button class="row" role="menuitemradio" aria-checked={item.checked ?? false}
                        onclick={() => pick(item.id)}>
                    <span class="check" class:on={item.checked}>
                        <svg viewBox="0 -960 960 960" width="14" height="14" aria-hidden="true">
                            <path d="m382-354 339-339q12-12 28-12t28 12q12 12 12 28.5T777-636L410-268q-12 12-28 12t-28-12L182-440q-12-12-11.5-28.5T183-497q12-12 28.5-12t28.5 12l142 143Z"
                                  fill="var(--text-color)"/>
                        </svg>
                    </span>
                    {#if item.icon}
                        <svg class="icon" viewBox="0 -960 960 960" width="17" height="17" aria-hidden="true">
                            <path d={item.icon} fill="var(--text-color)"/>
                        </svg>
                    {:else}
                        <span class="icon glyph">{item.glyph ?? ''}</span>
                    {/if}
                    <span class="label">{item.label}</span>
                </button>
            {/each}
        {/each}
    </div>
</MorphSurface>

<style>
    .items {
        min-width: 190px;
        padding: 6px;
    }

    .divider {
        height: 1px;
        margin: 5px 10px;
        background: color-mix(in srgb, var(--text-color) 14%, transparent);
    }

    .row {
        display: flex;
        align-items: center;
        gap: 10px;
        width: 100%;
        padding: 8px 10px;
        border: none;
        border-radius: 12px;
        background: transparent;
        color: var(--text-color);
        font-family: inherit;
        font-size: 14px;
        letter-spacing: inherit;
        text-align: left;
        cursor: pointer;
        --wails-draggable: no-drag;
    }

    .row:hover {
        background: color-mix(in srgb, var(--text-color) 8%, transparent);
    }

    .row:active {
        background: color-mix(in srgb, var(--text-color) 14%, transparent);
    }

    .check {
        display: flex;
        width: 14px;
        flex-shrink: 0;
        opacity: 0;
    }

    /*noinspection CssUnusedSymbol*/
    .check.on {
        opacity: 1;
    }

    .icon {
        flex-shrink: 0;
        width: 17px;
        opacity: 0.85;
    }

    .glyph {
        font-size: 13px;
        font-weight: 600;
        text-align: center;
    }

    .label {
        white-space: nowrap;
    }
</style>
