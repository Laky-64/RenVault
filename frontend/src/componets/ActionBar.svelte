<script lang="ts">
    import NavButton from "./NavButton.svelte";
    import Menu from "./Menu.svelte";
    import PasswordEditor, {type Draft} from "./PasswordEditor.svelte";
    import {BACK_INSET, type Bounds} from "../lib/layout";
    import type {MenuSection} from "../lib/menu";
    import type {SortField} from "../lib/sort";
    import {nav} from "../navigation.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {m} from "../paraglide/messages";
    import {ADD, ARROW_DOWN, ARROW_UP, CLOCK, DELETE, EDIT, LINK, ORDER} from "../lib/icons";

    const {
        list,
        selecting = false,
        canDelete = false,
        canAdd = false,
        sortField = 'title',
        sortAscending = true,
        on_sort,
        on_delete,
        on_add,
    } : {
        list: Bounds;
        selecting?: boolean;
        canDelete?: boolean;
        canAdd?: boolean;
        sortField?: SortField;
        sortAscending?: boolean;
        on_sort?: (field: SortField, ascending: boolean) => void;
        on_delete?: () => void;
        on_add?: (draft: Draft) => void;
    } = $props();

    let orderAnchor: HTMLElement | undefined = $state();
    let orderOpen = $state(false);
    let orderMorphing = $state(false);
    let addAnchor: HTMLElement | undefined = $state();
    let addOpen = $state(false);
    let addMorphing = $state(false);

    const sortSections: MenuSection[] = $derived([
        [
            {id: 'desc', label: m.sort_descending(), icon: ARROW_DOWN, checked: !sortAscending},
            {id: 'asc', label: m.sort_ascending(), icon: ARROW_UP, checked: sortAscending},
        ],
        [
            {id: 'modified', label: m.sort_modified(), icon: EDIT, checked: sortField === 'modified'},
            {id: 'created', label: m.sort_created(), icon: CLOCK, checked: sortField === 'created'},
            {id: 'website', label: m.sort_website(), icon: LINK, checked: sortField === 'website'},
            {id: 'title', label: m.sort_title(), glyph: 'Aa', checked: sortField === 'title'},
        ],
    ]);

    function chooseSort(id: string) {
        if (id === 'asc' || id === 'desc') {
            on_sort?.(sortField, id === 'asc');
            return;
        }
        on_sort?.(id as SortField, sortAscending);
    }

    const stack = $derived(isCompact());
    const onList = $derived(!stack || nav.depth === 1);

    $effect(() => {
        if (selecting || !onList) orderOpen = false;
    });
</script>

<div class="action-bar" class:stack style="--inset: {BACK_INSET}px">
    <div class="bar" style="--bar-left: {list.left}px; --bar-width: {list.width}px">
        <div class="slot" bind:this={orderAnchor} class:swallowed={orderMorphing}>
            <NavButton
                shown={onList}
                padding="12px"
                morph={selecting}
                flash={selecting}
                disabled={selecting && !canDelete}
                dim={false}
                onclick={() => (selecting ? on_delete?.() : (orderOpen = !orderOpen))}
            >
                <svg viewBox="0 -960 960 960" width="22" height="22" role="img"
                     class:faded={selecting && !canDelete}
                     aria-label={selecting ? m.list_delete() : m.list_order()}>
                    <path d={selecting ? DELETE : ORDER} fill="var(--text-color)"/>
                </svg>
            </NavButton>
        </div>
        <div class="slot push" bind:this={addAnchor} class:swallowed={addMorphing}>
            <NavButton
                shown={onList && canAdd && !selecting}
                align="end"
                padding="12px"
                onclick={() => (addOpen = true)}
            >
                <svg viewBox="0 -960 960 960" width="22" height="22" role="img" aria-label={m.list_add()}>
                    <path d={ADD} fill="var(--text-color)"/>
                </svg>
            </NavButton>
        </div>
    </div>
</div>

<Menu bind:open={orderOpen} bind:active={orderMorphing} anchor={orderAnchor}
      placement="top-start" sections={sortSections} on_select={chooseSort}>
    {#snippet seed()}
        <svg viewBox="0 -960 960 960" width="22" height="22" aria-hidden="true">
            <path d={ORDER} fill="var(--text-color)"/>
        </svg>
    {/snippet}
</Menu>

<PasswordEditor bind:open={addOpen} bind:active={addMorphing} anchor={addAnchor} on_save={on_add}>
    {#snippet seed()}
        <svg viewBox="0 -960 960 960" width="22" height="22" aria-hidden="true">
            <path d={ADD} fill="var(--text-color)"/>
        </svg>
    {/snippet}
</PasswordEditor>

<style>
    .action-bar {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        display: flex;
        z-index: 5;
        pointer-events: none;
    }

    .bar {
        position: absolute;
        bottom: 0;
        left: var(--bar-left);
        width: var(--bar-width);
        display: flex;
        align-items: center;
        gap: 8px;
        padding: var(--inset);
    }

    .action-bar.stack > .bar {
        left: 0;
        width: auto;
        right: 0;
    }

    .slot {
        display: flex;
        flex-shrink: 0;
        pointer-events: auto;
    }

    .slot.push {
        margin-left: auto;
    }

    .slot.swallowed {
        opacity: 0;
    }

    svg {
        transition: opacity 180ms ease;
    }

    svg.faded {
        opacity: 0.35;
    }
</style>
