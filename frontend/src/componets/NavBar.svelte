<script lang="ts">
    import {type Zone, zoneText} from "./ZoneContainer";
    import BackButton from "./BackButton.svelte";
    import SelectButton from "./SelectButton.svelte";
    import SearchField from "./SearchField.svelte";
    import TotpRing from "./TotpRing.svelte";
    import {BACK_INSET, BAR_HEIGHT, type Bounds} from "../lib/layout";
    import {currentItem, nav} from "../navigation.svelte";
    import {editableOf} from "../lib/items";
    import {isCompact} from "../lib/navigation.svelte";
    import {CROSS_MS, motionMs} from "../lib/motion";
    import {fade} from "svelte/transition";
    import {m} from "../paraglide/messages";

    let {
        zone,
        query = $bindable(''),
        list,
        detail,
        stuck = false,
        allSelected = false,
        selectedCount = 0,
        selecting = $bindable(false),
        editing = $bindable(false),
        canSave = true,
        on_selectAll,
        on_editSave,
        on_editCancel,
    } : {
        zone: Zone;
        query?: string;
        list: Bounds;
        detail: Bounds;
        stuck?: boolean;
        allSelected?: boolean;
        selectedCount?: number;
        selecting?: boolean;
        editing?: boolean;
        canSave?: boolean;
        on_selectAll?: () => void;
        on_editSave?: () => void;
        on_editCancel?: () => void;
    } = $props();

    const stack = $derived(isCompact());
    const onList = $derived(!stack || nav.depth === 1);
    const onDetail = $derived(stack && nav.depth > 1);
    const selectable = $derived(zone.kind !== 'passkeys' && zone.kind !== 'networks');
    const selectingList = $derived(selecting && onList);
    const revealed = $derived(stuck);
    const count = $derived(selectingList
        ? m.list_selectedCount({count: selectedCount})
        : m.zone_list_itemCount({count: zone.items.length}));
    const hasItem = $derived(currentItem() !== undefined);
    const canEdit = $derived(zone.kind !== 'deleted' && editableOf(currentItem()) !== null);
    const editingHere = $derived(editing && onDetail);
    const blocked = $derived(editing && !canSave);
    const codeClock = $derived(onList && zone.kind === 'codes' && !selectingList);
    const placeholder = $derived(zone.kind === 'all'
        ? m.search_placeholder()
        : m.search_placeholderIn({zone: zoneText(zone).name}));

    function backMode() {
        if (editingHere) return 'cancel' as const;
        if (selectingList) return allSelected ? 'deselect-all' as const : 'select-all' as const;
        return 'back' as const;
    }

    function onBack() {
        if (editingHere) on_editCancel?.();
        else if (selectingList) on_selectAll?.();
        else nav.back();
    }

    function onEdit() {
        if (!editing) editing = true;
        else if (canSave) on_editSave?.();
    }

    const crossfade = {duration: motionMs(CROSS_MS)};

    $effect(() => {
        currentItem();
        editing = false;
    });

    $effect(() => {
        if (nav.depth < 1 || !selectable) selecting = false;
    });
</script>

<div class="navbar" class:stack style="--inset: {BACK_INSET}px; --bar-height: {BAR_HEIGHT}px">
    <div class="bar" style="--bar-left: {list.left}px; --bar-width: {list.width}px">
        <div class="slot">
            <BackButton
                shown={selectingList || (stack && nav.depth > 0)}
                mode={backMode()}
                onclick={onBack}/>
        </div>
        <div class="compact-title" aria-hidden="true">
            {#if onList}
                <div class="title" class:revealed transition:fade={crossfade}>
                    <span class="compact-name">{zoneText(zone).name}</span>
                    <span class="compact-count">{count}</span>
                </div>
            {/if}
        </div>
        <div class="slot stacked">
            <SelectButton
                shown={!codeClock && ((onList && selectable) || (onDetail && canEdit))}
                label={onList ? m.list_select() : m.item_edit()}
                active={onList ? selecting : editing}
                disabled={!onList && blocked}
                onclick={() => (onList ? (selecting = !selecting) : onEdit())}/>
            {#if codeClock}
                <div class="clock" transition:fade={crossfade}>
                    <TotpRing size={28}/>
                </div>
            {/if}
        </div>
    </div>
    {#if !stack}
        <div class="bar detail" style="--bar-left: {detail.left}px; --bar-width: {detail.width}px">
            <div class="slot">
                <BackButton
                    shown={hasItem && editing}
                    mode="cancel"
                    onclick={() => on_editCancel?.()}/>
            </div>
            <div class="slot push">
                <SelectButton
                    shown={hasItem && canEdit}
                    label={m.item_edit()}
                    active={editing}
                    disabled={blocked}
                    onclick={onEdit}/>
            </div>
            <div class="search">
                <SearchField bind:value={query} {placeholder} glass/>
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

    .search {
        display: flex;
        flex: 1;
        min-width: 0;
        max-width: 220px;
        pointer-events: auto;
    }

    .slot.push {
        margin-left: auto;
    }

    .slot {
        display: flex;
        flex-shrink: 0;
        pointer-events: auto;
    }




    .slot.stacked {
        display: grid;
        justify-items: end;
        align-items: center;
    }

    .slot.stacked > :global(*) {
        grid-area: 1 / 1;
    }

    .clock {
        display: flex;
        align-items: center;
        padding-inline: 8px;
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
