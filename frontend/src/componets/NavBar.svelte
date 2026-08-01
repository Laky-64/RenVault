<script lang="ts">
    import {type Zone, zoneText} from "./ZoneContainer";
    import BackButton from "./BackButton.svelte";
    import SelectButton from "./SelectButton.svelte";
    import {BACK_INSET, BAR_HEIGHT, type Bounds} from "../lib/layout";
    import {currentItem, nav} from "../navigation.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {CROSS_MS, motionMs} from "../lib/motion";
    import {fade} from "svelte/transition";
    import {m} from "../paraglide/messages";

    let {
        zone,
        list,
        detail,
        stuck = false,
        allSelected = false,
        selectedCount = 0,
        selecting = $bindable(false),
        editing = $bindable(false),
        on_selectAll,
    } : {
        zone: Zone;
        list: Bounds;
        detail: Bounds;
        stuck?: boolean;
        allSelected?: boolean;
        selectedCount?: number;
        selecting?: boolean;
        editing?: boolean;
        on_selectAll?: () => void;
    } = $props();

    const stack = $derived(isCompact());
    const onList = $derived(!stack || nav.depth === 1);
    const onDetail = $derived(stack && nav.depth > 1);
    const selectingList = $derived(selecting && onList);
    const revealed = $derived(stack || stuck);
    const count = $derived(selectingList
        ? m.list_selectedCount({count: selectedCount})
        : m.zone_list_itemCount({count: zone.items.length}));
    const hasItem = $derived(currentItem() !== undefined);

    const crossfade = {duration: motionMs(CROSS_MS)};

    $effect(() => {
        currentItem();
        editing = false;
    });

    $effect(() => {
        if (nav.depth < 1) selecting = false;
    });
</script>

<div class="navbar" class:stack style="--inset: {BACK_INSET}px; --bar-height: {BAR_HEIGHT}px">
    <div class="bar" style="--bar-left: {list.left}px; --bar-width: {list.width}px">
        <div class="top-bar">
            {#if onList}
                <div class="bar-backdrop" class:revealed transition:fade={crossfade}></div>
            {/if}
        </div>
        <div class="slot">
            <BackButton
                shown={selectingList || (stack && nav.depth > 0)}
                mode={selectingList ? (allSelected ? 'deselect-all' : 'select-all') : 'back'}
                onclick={() => (selectingList ? on_selectAll?.() : nav.back())}/>
        </div>
        <div class="compact-title" aria-hidden="true">
            {#if onList}
                <div class="title" class:revealed transition:fade={crossfade}>
                    <span class="compact-name">{zoneText(zone).name}</span>
                    <span class="compact-count">{count}</span>
                </div>
            {/if}
        </div>
        <div class="slot">
            <SelectButton
                shown={onList || onDetail}
                label={onList ? m.list_select() : m.item_edit()}
                active={onList ? selecting : editing}
                onclick={() => (onList ? (selecting = !selecting) : (editing = !editing))}/>
        </div>
    </div>
    {#if !stack}
        <div class="bar detail" style="--bar-left: {detail.left}px; --bar-width: {detail.width}px">
            <div class="slot push">
                <SelectButton
                    shown={hasItem}
                    label={m.item_edit()}
                    active={editing}
                    onclick={() => (editing = !editing)}/>
            </div>
        </div>
    {/if}
</div>

<style>
    .navbar {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: var(--bar-height);
        display: flex;
        z-index: 5;
        pointer-events: none;
    }

    .bar {
        position: absolute;
        top: 0;
        bottom: 0;
        left: var(--bar-left);
        width: var(--bar-width);
        display: flex;
        align-items: center;
        gap: 8px;
        padding-inline: var(--inset);
    }

    .navbar.stack > .bar {
        left: 0;
        width: auto;
        right: 0;
    }

    .slot.push {
        margin-left: auto;
    }

    .slot {
        display: flex;
        flex-shrink: 0;
        pointer-events: auto;
    }

    .top-bar {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: var(--bar-height);
        display: flex;
        align-items: center;
        justify-content: center;
        pointer-events: none;
    }

    .bar-backdrop {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: var(--bar-height);
        opacity: 0;
        transition: opacity 200ms ease;
        background: linear-gradient(
            to bottom,
            var(--secondary-bg-color) 0%,
            color-mix(in srgb, var(--secondary-bg-color) 96%, transparent) 35%,
            color-mix(in srgb, var(--secondary-bg-color) 72%, transparent) 65%,
            color-mix(in srgb, var(--secondary-bg-color) 28%, transparent) 85%,
            color-mix(in srgb, var(--secondary-bg-color) 0%, transparent) 100%
        );
    }

    .bar-backdrop.revealed {
        opacity: 1;
    }

    .compact-title {
        position: relative;
        flex: 1;
        min-width: 0;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .title {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        min-width: 0;
        line-height: 1.15;
    }

    .compact-name,
    .compact-count {
        opacity: 0;
        transform: translateY(9px);
        filter: blur(4px);
        margin-bottom: 2px;
        text-shadow:
            0 0 5px var(--secondary-bg-color),
            0 0 12px var(--secondary-bg-color);
        transition:
            opacity 260ms ease,
            transform 260ms cubic-bezier(0.22, 1, 0.36, 1),
            filter 260ms ease;
        will-change: opacity, transform, filter;
    }

    .compact-name {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-color);
        transition-delay: 50ms;
    }

    .compact-count {
        font-size: 11px;
        font-weight: 500;
        color: var(--subtitle-text-color);
        transition-delay: 0ms;
    }

    .revealed .compact-name,
    .revealed .compact-count {
        opacity: 1;
        transform: none;
        filter: none;
    }

    .revealed .compact-name {
        transition-delay: 0ms;
    }

    .revealed .compact-count {
        transition-delay: 90ms;
    }
</style>
