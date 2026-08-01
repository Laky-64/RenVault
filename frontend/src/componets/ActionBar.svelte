<script lang="ts">
    import NavButton from "./NavButton.svelte";
    import Menu from "./Menu.svelte";
    import {BACK_INSET, type Bounds} from "../lib/layout";
    import type {MenuSection} from "../lib/menu";
    import type {SortField} from "../lib/sort";
    import {nav} from "../navigation.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {m} from "../paraglide/messages";

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
        on_add?: () => void;
    } = $props();

    const ARROW_UP = 'M440-647 244-451q-12 12-28 11.5T188-452q-11-12-11.5-28t11.5-28l264-264q6-6 13-8.5t15-2.5q8 0 15 2.5t13 8.5l264 264q11 11 11 27.5T772-452q-12 12-28.5 12T715-452L520-647v447q0 17-11.5 28.5T480-160q-17 0-28.5-11.5T440-200v-447Z';
    const ARROW_DOWN = 'M440-313v-447q0-17 11.5-28.5T480-800q17 0 28.5 11.5T520-760v447l196-196q12-12 28-11.5t28 12.5q11 12 11.5 28T772-452L508-188q-6 6-13 8.5t-15 2.5q-8 0-15-2.5t-13-8.5L188-452q-11-11-11-27.5t11-28.5q12-12 28.5-12t28.5 12l195 195Z';
    const EDIT = 'M200-200h57l391-391-57-57-391 391v57Zm-40 80q-17 0-28.5-11.5T120-160v-97q0-16 6-30.5t17-25.5l505-504q12-11 26.5-17t30.5-6q16 0 31 6t26 18l55 56q12 11 17.5 26t5.5 30q0 16-5.5 30.5T817-647L313-143q-11 11-25.5 17t-30.5 6h-97Zm600-584-56-56 56 56Zm-141 85-28-29 57 57-29-28Z';
    const CLOCK = 'M520-496v-144q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640v159q0 8 3 15.5t9 13.5l132 132q11 11 28 11t28-11q11-11 11-28t-11-28L520-496ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-400Zm0 320q133 0 226.5-93.5T800-480q0-133-93.5-226.5T480-800q-133 0-226.5 93.5T160-480q0 133 93.5 226.5T480-160Z';
    const LINK = 'M318-120q-82 0-140-58t-58-140q0-40 15-76t43-64l105-105q12-12 28.5-12t28.5 12q12 12 12 28t-12 28L234-401q-17 17-25.5 38.5T200-318q0 49 34.5 83.5T318-200q23 0 45-8.5t39-25.5l105-106q12-11 28-11t28 12q12 12 12 28t-12 28L458-178q-28 28-64 43t-76 15Zm50-248q-12-12-12-28.5t12-28.5l167-167q12-12 28.5-12t28.5 12q12 12 12 28.5T592-535L425-368q-12 12-28.5 12T368-368Zm252-29q-12-12-12-28t12-28l106-105q17-17 25-38t8-44q0-50-34-85t-84-35q-23 0-44.5 8.5T558-726L453-620q-12 12-28 12t-28-12q-12-12-12-28.5t12-28.5l105-105q28-28 64-43t76-15q82 0 139.5 58T839-641q0 39-14.5 75T782-502L677-397q-12 12-28.5 12T620-397Z';

    let orderAnchor: HTMLElement | undefined = $state();
    let orderOpen = $state(false);
    let orderMorphing = $state(false);

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

    const ORDER = 'M160-240q-17 0-28.5-11.5T120-280q0-17 11.5-28.5T160-320h160q17 0 28.5 11.5T360-280q0 17-11.5 28.5T320-240H160Zm0-200q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520h400q17 0 28.5 11.5T600-480q0 17-11.5 28.5T560-440H160Zm0-200q-17 0-28.5-11.5T120-680q0-17 11.5-28.5T160-720h640q17 0 28.5 11.5T840-680q0 17-11.5 28.5T800-640H160Z';
    const DELETE = 'M280-120q-33 0-56.5-23.5T200-200v-520q-17 0-28.5-11.5T160-760q0-17 11.5-28.5T200-800h160q0-17 11.5-28.5T400-840h160q17 0 28.5 11.5T600-800h160q17 0 28.5 11.5T800-760q0 17-11.5 28.5T760-720v520q0 33-23.5 56.5T680-120H280Zm400-600H280v520h400v-520ZM428.5-291.5Q440-303 440-320v-280q0-17-11.5-28.5T400-640q-17 0-28.5 11.5T360-600v280q0 17 11.5 28.5T400-280q17 0 28.5-11.5Zm160 0Q600-303 600-320v-280q0-17-11.5-28.5T560-640q-17 0-28.5 11.5T520-600v280q0 17 11.5 28.5T560-280q17 0 28.5-11.5ZM280-720v520-520Z';
    const ADD = 'M451.5-131.5Q440-143 440-160v-280H160q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520h280v-280q0-17 11.5-28.5T480-840q17 0 28.5 11.5T520-800v280h280q17 0 28.5 11.5T840-480q0 17-11.5 28.5T800-440H520v280q0 17-11.5 28.5T480-120q-17 0-28.5-11.5Z';

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
        <div class="slot push">
            <NavButton
                shown={onList && canAdd && !selecting}
                align="end"
                padding="12px"
                onclick={() => on_add?.()}
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
