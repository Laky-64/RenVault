<script lang="ts">
    import {prefersReducedMotion} from "../lib/dom";
    import {fade} from "svelte/transition";
    import {m} from "../paraglide/messages";
    import {strengthOf} from "../lib/strength";
    import Fold from "./Fold.svelte";

    let {value = ''}: {value?: string} = $props();

    const reduced = prefersReducedMotion();

    const FILL = [0, 34, 67, 100];
    const LABEL = [m.strength_weak, m.strength_fair, m.strength_strong];

    const level = $derived(strengthOf(value));
    const label = $derived(level === 0 ? '' : LABEL[level - 1]());
</script>

<Fold open={value.length > 0}>
    <div class="strength" aria-hidden="true">
        <div class="track">
            <div class="fill" data-level={level} style="width: {FILL[level]}%"></div>
        </div>
        <span class="label-slot" class:filled={label !== ''}>
            {#key level}
                <span class="label" data-level={level}
                      transition:fade={{duration: reduced ? 0 : 200}}>{label}</span>
            {/key}
        </span>
    </div>
</Fold>

<style>
    .strength {
        display: flex;
        align-items: center;
        padding: 8px;
    }

    .track {
        flex: 1;
        height: 4px;
        border-radius: 2px;
        background: color-mix(in srgb, var(--text-color) 12%, transparent);
        overflow: hidden;
    }

    .fill {
        height: 100%;
        border-radius: 2px;
        background: var(--destructive-text-color);
        transition:
            width 480ms cubic-bezier(0.22, 1, 0.36, 1),
            background-color 320ms ease;
    }

    .fill[data-level="2"] { background: var(--strength-fair-color); }
    .fill[data-level="3"] { background: var(--strength-strong-color); }

    .label-slot {
        position: relative;
        width: 0;
        height: 13px;
        padding-left: 0;
        overflow: hidden;
        transition:
            width 380ms cubic-bezier(0.22, 1, 0.36, 1),
            padding-left 380ms cubic-bezier(0.22, 1, 0.36, 1);
    }

    .label-slot.filled {
        width: 52px;
        padding-left: 10px;
    }

    .label {
        position: absolute;
        top: 0;
        right: 0;
        font-size: 11px;
        font-weight: 500;
        line-height: 13px;
        white-space: nowrap;
        color: var(--destructive-text-color);
    }

    .label[data-level="2"] { color: var(--strength-fair-color); }
    .label[data-level="3"] { color: var(--strength-strong-color); }

    @media (prefers-reduced-motion: reduce) {
        .fill,
        .label-slot {
            transition: none;
        }
    }
</style>
