<script lang="ts">
    import NavButton from "./NavButton.svelte";
    import {BACK_INSET, type Bounds} from "../lib/layout";
    import {nav} from "../navigation.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {m} from "../paraglide/messages";

    const {
        list,
        selecting = false,
        canDelete = false,
        canAdd = false,
        on_order,
        on_delete,
        on_add,
    } : {
        list: Bounds;
        selecting?: boolean;
        canDelete?: boolean;
        canAdd?: boolean;
        on_order?: () => void;
        on_delete?: () => void;
        on_add?: () => void;
    } = $props();

    const ORDER = 'M160-240q-17 0-28.5-11.5T120-280q0-17 11.5-28.5T160-320h160q17 0 28.5 11.5T360-280q0 17-11.5 28.5T320-240H160Zm0-200q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520h400q17 0 28.5 11.5T600-480q0 17-11.5 28.5T560-440H160Zm0-200q-17 0-28.5-11.5T120-680q0-17 11.5-28.5T160-720h640q17 0 28.5 11.5T840-680q0 17-11.5 28.5T800-640H160Z';
    const DELETE = 'M280-120q-33 0-56.5-23.5T200-200v-520q-17 0-28.5-11.5T160-760q0-17 11.5-28.5T200-800h160q0-17 11.5-28.5T400-840h160q17 0 28.5 11.5T600-800h160q17 0 28.5 11.5T800-760q0 17-11.5 28.5T760-720v520q0 33-23.5 56.5T680-120H280Zm400-600H280v520h400v-520ZM428.5-291.5Q440-303 440-320v-280q0-17-11.5-28.5T400-640q-17 0-28.5 11.5T360-600v280q0 17 11.5 28.5T400-280q17 0 28.5-11.5Zm160 0Q600-303 600-320v-280q0-17-11.5-28.5T560-640q-17 0-28.5 11.5T520-600v280q0 17 11.5 28.5T560-280q17 0 28.5-11.5ZM280-720v520-520Z';
    const ADD = 'M451.5-131.5Q440-143 440-160v-280H160q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520h280v-280q0-17 11.5-28.5T480-840q17 0 28.5 11.5T520-800v280h280q17 0 28.5 11.5T840-480q0 17-11.5 28.5T800-440H520v280q0 17-11.5 28.5T480-120q-17 0-28.5-11.5Z';

    const stack = $derived(isCompact());
    const onList = $derived(!stack || nav.depth === 1);
</script>

<div class="action-bar" class:stack style="--inset: {BACK_INSET}px">
    <div class="bar" style="--bar-left: {list.left}px; --bar-width: {list.width}px">
        <div class="slot">
            <NavButton
                shown={onList}
                padding="12px"
                morph={selecting}
                flash={selecting}
                disabled={selecting && !canDelete}
                dim={false}
                onclick={() => (selecting ? on_delete?.() : on_order?.())}
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

    svg {
        transition: opacity 180ms ease;
    }

    svg.faded {
        opacity: 0.35;
    }
</style>
