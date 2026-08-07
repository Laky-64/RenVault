<script lang="ts">
    import {type Zone, zoneText} from "./ZoneContainer";
    import type {Item, SecretSource} from "../lib/items";
    import {untrack} from "svelte";
    import PasswordItem from "./PasswordItem.svelte";
    import VirtualList from "./VirtualList.svelte";
    import SearchField from "./SearchField.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {m} from "../paraglide/messages";
    import {ACTION_HEIGHT, BAR_HEIGHT, SEARCH_HEIGHT} from "../lib/layout";
    import {observeSize} from "../lib/dom";

    let {
        zone,
        query = $bindable(''),
        secrets,
        selected,
        on_selected,
        selecting = false,
        checked,
        on_toggle,
        stuck = $bindable(false),
        on_bounds,
    } : {
        zone: Zone;
        query?: string;
        secrets?: SecretSource;
        selected?: Item | null;
        on_selected?: (item: Item) => void;
        selecting?: boolean;
        checked?: ReadonlySet<string>;
        on_toggle?: (item: Item) => void;
        stuck?: boolean;
        on_bounds?: (left: number, width: number) => void;
    } = $props();

    const previewCode = $derived.by(() => {
        if (zone.kind !== 'codes' || !secrets) return undefined;
        const source = secrets;
        return async (item: Item) =>
            item.kind === 'web' && item.hasTotp ? source.totp(item.id) : '';
    });

    const notice = $derived(zone.kind === 'security' ? m.item_compromised() : undefined);

    const HYSTERESIS = 6;
    let list: VirtualList<Item> | undefined = $state();
    let shownOffset = $state(0);
    let titleHeight = $state(0);
    let placed = false;
    const restOffset = $derived(Math.max(0, titleHeight - BAR_HEIGHT));
    const searchTop = $derived(Math.max(BAR_HEIGHT, titleHeight - shownOffset));
    const pinned = $derived(searchTop <= BAR_HEIGHT + 0.5);
    const titleFade = $derived(restOffset > 0
        ? Math.max(0, Math.min(1, 1 - shownOffset / restOffset))
        : 1);

    const stack = $derived(isCompact());
    const startAt = $derived(stack ? restOffset : 0);

    $effect(() => {
        if (placed || startAt <= 0) return;
        placed = true;
        list?.jumpTo(startAt);
    });

    $effect(() => {
        zone.kind;
        stack;
        placed = false;
    });

    const name = $derived(zoneText(zone).name);
    const count = $derived(selecting
        ? m.list_selectedCount({count: checked?.size ?? 0})
        : m.zone_list_itemCount({count: zone.items.length}));
    const placeholder = $derived(zone.kind === 'all'
        ? m.search_placeholder()
        : m.search_placeholderIn({zone: name}));
    const noResults = $derived(zone.items.length === 0 && query.trim() !== '');

    $effect(() => {
        const offset = shownOffset;
        const threshold = restOffset - HYSTERESIS;
        if (restOffset <= 0) {
            untrack(() => (stuck = false));
            return;
        }
        untrack(() => {
            if (!stuck && offset >= threshold) stuck = true;
            else if (stuck && offset < threshold - HYSTERESIS) stuck = false;
        });
    });
</script>

<div class="container" style="--bar-height: {BAR_HEIGHT}px; --search-height: {stack ? SEARCH_HEIGHT : 0}px" class:stack use:observeSize={node => on_bounds?.(node.offsetLeft, node.offsetWidth)}>
    <VirtualList bind:this={list} items={zone.items} resetKey={zone.kind} tail={ACTION_HEIGHT}
                 minReach={startAt} startOffset={startAt} bind:shownOffset>
        {#snippet header()}
            <div class="large-title" style="opacity: {titleFade}" bind:clientHeight={titleHeight}>
                <h1>{name}</h1>
                <p>{count}</p>
            </div>
            {#if stack}
                <div class="search-slot" style="height: {SEARCH_HEIGHT}px"></div>
            {/if}
        {/snippet}
        {#snippet item(entry, index)}
            <PasswordItem
                item={entry}
                loadCode={previewCode}
                {notice}
                onclick={() => (selecting ? on_toggle?.(entry) : on_selected?.(entry))}
                selectable={selecting}
                checked={checked?.has(entry.id) ?? false}
                selected={entry === selected && !stack && !selecting}
                last={index === zone.items.length - 1}/>
        {/snippet}
    </VirtualList>

    {#if noResults}
        <div class="no-results">
            <svg viewBox="0 -960 960 960" width="46" height="46" aria-hidden="true">
                <path d="M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l224 224q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z"
                      fill="var(--hint-pass-selection)"/>
            </svg>
            <p class="no-title">{m.search_empty({query: query.trim()})}</p>
            <p class="no-desc">{m.search_emptyDesc()}</p>
        </div>
    {/if}

    <div class="scrim" class:revealed={pinned}></div>

    {#if stack}
        <div class="search-dock" style="transform: translateY({searchTop}px)">
            <SearchField bind:value={query} {placeholder} glass={pinned}/>
        </div>
    {/if}
</div>

<style>
    .container {
        position: relative;
        flex: 1;
        min-width: 0;
        height: 100%;
    }

    .container:not(.stack) {
        max-width: var(--list-max, 350px);
    }

    .container:not(.stack)::after {
        content: '';
        position: absolute;
        z-index: 6;
        right: 0;
        width: 1px;
        top: 0;
        bottom: 0;
        background: var(--hairline-color);
    }

    .large-title {
        padding-top: var(--bar-height);
        padding-bottom: 15px;
        padding-inline: 15px;
    }

    .large-title > h1 {
        font-size: 30px;
        font-weight: bold;
        color: var(--text-color);
        margin: 0;
    }

    .large-title > p {
        margin: 0;
        font-size: 12px;
        font-weight: 500;
        color: var(--subtitle-text-color);
    }

    .no-results {
        position: absolute;
        top: calc(var(--bar-height) + var(--search-height));
        left: 0;
        right: 0;
        bottom: 0;
        z-index: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 0 30px 15%;
        pointer-events: none;
    }

    .no-title {
        margin: 22px 0 0;
        font-size: 20px;
        font-weight: bold;
        text-align: center;
        color: var(--hint-pass-selection);
    }

    .no-desc {
        margin: 8px 0 0;
        font-size: 14px;
        text-align: center;
        color: var(--hint-pass-selection);
    }

    .scrim {
        --reach: calc(var(--bar-height) + var(--search-height));
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        z-index: 4;
        height: var(--reach);
        opacity: 0;
        transition: opacity 200ms ease;
        pointer-events: none;
        background: linear-gradient(
            to bottom,
            var(--secondary-bg-color) 0,
            color-mix(in srgb, var(--secondary-bg-color) 96%, transparent) 55%,
            color-mix(in srgb, var(--secondary-bg-color) 74%, transparent) 84%,
            color-mix(in srgb, var(--secondary-bg-color) 0%, transparent) 100%
        );
    }

    .scrim.revealed {
        opacity: 1;
    }

    .search-dock {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        z-index: 7;
        padding: 0 15px 12px;
        pointer-events: auto;
    }

</style>
