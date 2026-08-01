<script lang="ts">
    import {untrack} from "svelte";
    import {type MenuPlacement, type MenuSection} from "../lib/menu";
    import {motionMs} from "../lib/motion";
    import {observeSize} from "../lib/dom";

    let {
        open = $bindable(false),
        active = $bindable(false),
        anchor,
        placement = 'top-start',
        sections,
        on_select,
    } : {
        open?: boolean;
        active?: boolean;
        anchor?: HTMLElement;
        placement?: MenuPlacement;
        sections: MenuSection[];
        on_select?: (id: string) => void;
    } = $props();

    const OPEN_MS = 300;
    const CLOSE_MS = 280;
    const RADIUS = 20;

    const above = $derived(placement.startsWith('top'));
    const fromEnd = $derived(placement.endsWith('end'));

    let seed = $state({left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0});
    let natural = $state({width: 0, height: 0});
    let grown = $state(false);

    function measure() {
        if (!anchor) return;
        const rect = anchor.getBoundingClientRect();
        seed = {
            left: rect.left,
            top: rect.top,
            right: window.innerWidth - rect.right,
            bottom: window.innerHeight - rect.bottom,
            width: rect.width,
            height: rect.height,
        };
    }

    $effect(() => {
        if (!open) {
            grown = false;
            const timer = setTimeout(() => untrack(() => (active = false)), motionMs(CLOSE_MS));
            return () => clearTimeout(timer);
        }
        untrack(() => (active = true));
        measure();
        const frame = requestAnimationFrame(() => (grown = true));
        const onResize = () => measure();
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') open = false;
        };
        window.addEventListener('resize', onResize);
        window.addEventListener('keydown', onKey);
        return () => {
            cancelAnimationFrame(frame);
            window.removeEventListener('resize', onResize);
            window.removeEventListener('keydown', onKey);
        };
    });

    const shape = $derived.by(() => {
        const side = fromEnd ? `right: ${seed.right}px` : `left: ${seed.left}px`;
        const edge = above ? `bottom: ${seed.bottom}px` : `top: ${seed.top}px`;
        const size = grown
            ? `width: ${natural.width}px; height: ${natural.height}px; border-radius: ${RADIUS}px`
            : `width: ${seed.width}px; height: ${seed.height}px; border-radius: ${seed.height / 2}px`;
        return `${side}; ${edge}; ${size}`;
    });

    function pick(id: string) {
        on_select?.(id);
        open = false;
    }
</script>

{#if active}
    {#if open}
        <!-- svelte-ignore a11y_no_static_element_interactions -->
        <div class="scrim" onpointerdown={() => (open = false)}></div>
    {/if}
    <div
        class="menu"
        class:grown
        class:above
        class:from-end={fromEnd}
        style="{shape}; --open-ms: {motionMs(OPEN_MS)}ms; --close-ms: {motionMs(CLOSE_MS)}ms"
        role="menu"
        tabindex="-1"
    >
        <div class="items" use:observeSize={node => (natural = {width: node.offsetWidth, height: node.offsetHeight})}>
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
    </div>
{/if}

<style>
    .scrim {
        position: fixed;
        inset: 0;
        z-index: 40;
    }

    .menu {
        position: fixed;
        z-index: 41;
        overflow: hidden;
        background: color-mix(in srgb, var(--text-color) 8%, transparent);
        backdrop-filter: blur(20px) saturate(180%);
        -webkit-backdrop-filter: blur(20px) saturate(180%);
        box-shadow:
            0 6px 10px rgb(0 0 0 / 8%),
            inset 0 1px 0 color-mix(in srgb, var(--text-color) 20%, transparent);
        color: var(--text-color);
    }

    .menu:not(:has(.row:active)) {
        transform: scale(1);
        transition:
            width 240ms cubic-bezier(0.2, 1.34, 0.36, 1),
            height 240ms cubic-bezier(0.2, 1.34, 0.36, 1) 40ms,
            transform 250ms ease,
            border-radius 160ms ease 60ms;
    }

    .menu:has(.row:active) {
        transform: scale(1.04);
        transition: transform 250ms ease;
    }

    /*noinspection CssUnusedSymbol*/
    .menu.grown {
        transition:
            height var(--open-ms) cubic-bezier(0.2, 1.34, 0.4, 1),
            width var(--open-ms) cubic-bezier(0.16, 1.36, 0.36, 1) 30ms,
            border-radius calc(var(--open-ms) * 0.35) ease-out;
    }

    .items {
        position: absolute;
        width: max-content;
        min-width: 190px;
        padding: 6px;
        opacity: 0;
        filter: blur(7px);
        transition:
            opacity calc(var(--close-ms) * 0.6) ease,
            filter calc(var(--close-ms) * 0.8) ease;
    }

    .above > .items {
        bottom: 0;
    }

    /*noinspection CssUnusedSymbol*/
    .menu:not(.above) > .items {
        top: 0;
    }

    .from-end > .items {
        right: 0;
    }

    /*noinspection CssUnusedSymbol*/
    .menu:not(.from-end) > .items {
        left: 0;
    }

    /*noinspection CssUnusedSymbol*/
    .grown > .items {
        opacity: 1;
        filter: none;
        transition:
            opacity calc(var(--open-ms) * 0.45) ease calc(var(--open-ms) * 0.1),
            filter calc(var(--open-ms) * 0.55) ease calc(var(--open-ms) * 0.1);
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
        transition: background 150ms ease;
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

    @media (prefers-reduced-motion: reduce) {
        .menu,
        .menu.grown,
        .items,
        .grown > .items {
            transition: none;
        }
    }
</style>
