<script lang="ts" generics="T">
    import type {Snippet} from "svelte";
    import {SvelteMap} from "svelte/reactivity";
    import ElasticScroll from "./ElasticScroll.svelte";
    import {observeSize} from "../lib/dom";

    let {
        items,
        header,
        item,
        overscan = 4,
        tail = 0,
        resetKey,
        scrollOffset = $bindable(0),
    }: {
        items: T[];
        header?: Snippet;
        item: Snippet<[T, number]>;
        overscan?: number;
        tail?: number;
        resetKey?: unknown;
        scrollOffset?: number;
    } = $props();

    const BOOTSTRAP_GUESS = 32;

    let scroller: ElasticScroll | undefined = $state();
    let viewportHeight = $state(0);
    let headerHeight = $state(0);

    let measured = new SvelteMap<number, number>();
    let previousKey: unknown;
    let keyInitialized = false;
    let itemsVersion = $state(0);

    $effect(() => {
        const key = resetKey !== undefined ? resetKey : items;
        if (!keyInitialized) {
            previousKey = key;
            keyInitialized = true;
            return;
        }
        if (key === previousKey) return;
        previousKey = key;

        measured.clear();
        itemsVersion++;
        scroller?.reset();
    });

    const averageHeight = $derived.by(() => {
        if (measured.size === 0) return BOOTSTRAP_GUESS;
        let sum = 0;
        for (const h of measured.values()) sum += h;
        return sum / measured.size;
    });

    function heightOf(index: number): number {
        return measured.get(index) ?? averageHeight;
    }

    const offsets = $derived.by(() => {
        const result: number[] = new Array(items.length + 1);
        result[0] = 0;
        for (let i = 0; i < items.length; i++) {
            result[i + 1] = result[i] + heightOf(i);
        }
        return result;
    });

    function indexAt(pos: number): number {
        if (items.length === 0) return 0;
        let lo = 0, hi = items.length - 1, ans = 0;
        while (lo <= hi) {
            const mid = (lo + hi) >> 1;
            if (offsets[mid] <= pos) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    const contentHeight = $derived(headerHeight + offsets[items.length] + tail);

    const startIndex = $derived(Math.max(0, indexAt(Math.max(0, scrollOffset - headerHeight)) - overscan));
    const endIndex = $derived(Math.min(items.length, indexAt(Math.max(0, scrollOffset - headerHeight) + viewportHeight) + 1 + overscan));

    const visible = $derived(
        Array.from({length: Math.max(0, endIndex - startIndex)}, (_, i) => startIndex + i)
    );

    function applyRowHeight(index: number, height: number) {
        if (height <= 0) return;
        const previous = measured.get(index) ?? averageHeight;
        if (height === previous) return;
        const itemTop = headerHeight + offsets[index];
        measured.set(index, height);
        if (itemTop < scrollOffset - 0.5) {
            scroller?.nudge(height - previous);
        }
    }

    function applyHeaderHeight(height: number) {
        if (height <= 0) return;
        const previous = headerHeight;
        if (height === previous) return;
        headerHeight = height;
        if (previous < scrollOffset - 0.5) {
            scroller?.nudge(height - previous);
        }
    }

</script>

<ElasticScroll
    bind:this={scroller}
    contentHeight={contentHeight}
    bind:scrollOffset
    bind:viewportHeight
>
    <div class="header" use:observeSize={node => applyHeaderHeight(node.getBoundingClientRect().height)}>
        {@render header?.()}
    </div>
    {#each visible as index (itemsVersion + ':' + index)}
        <div class="row" style="top: {headerHeight + offsets[index]}px" use:observeSize={node => applyRowHeight(index, node.getBoundingClientRect().height)}>
            {@render item(items[index], index)}
        </div>
    {/each}
</ElasticScroll>

<style>
    .row {
        position: absolute;
        left: 0;
        right: 0;
    }
</style>