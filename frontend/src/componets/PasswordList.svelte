<script lang="ts">
    import {type Zone, zoneText} from "./ZoneContainer";
    import type {Item, SecretSource} from "../lib/items";
    import {untrack} from "svelte";
    import PasswordItem from "./PasswordItem.svelte";
    import VirtualList from "./VirtualList.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {m} from "../paraglide/messages";
    import {ACTION_HEIGHT, BAR_HEIGHT} from "../lib/layout";
    import {observeSize} from "../lib/dom";

    let {
        zone,
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
    let scrollOffset = $state(0);
    let titleHeight = $state(0);
    const stack = $derived(isCompact());
    const name = $derived(zoneText(zone).name);
    const count = $derived(selecting
        ? m.list_selectedCount({count: checked?.size ?? 0})
        : m.zone_list_itemCount({count: zone.items.length}));

    $effect(() => {
        const offset = scrollOffset;
        const threshold = titleHeight - BAR_HEIGHT;
        if (stack || titleHeight === 0) return;
        untrack(() => {
            if (!stuck && offset > threshold + HYSTERESIS) stuck = true;
            else if (stuck && offset < threshold - HYSTERESIS) stuck = false;
        });
    });
</script>

<div class="container" style="--bar-height: {BAR_HEIGHT}px" class:stack use:observeSize={node => on_bounds?.(node.offsetLeft, node.offsetWidth)}>
    <VirtualList items={zone.items} resetKey={zone} tail={ACTION_HEIGHT} bind:scrollOffset>
        {#snippet header()}
            {#if stack}
                <div class="bar-spacer"></div>
            {:else}
                <div class="large-title" bind:clientHeight={titleHeight}>
                    <h1>{name}</h1>
                    <p>{count}</p>
                </div>
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
        right: 0;
        width: 1px;
        top: 0;
        bottom: 0;
        background: color-mix(in srgb, var(--text-color) 12%, transparent);
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

    .bar-spacer {
        height: var(--bar-height);
    }

</style>
