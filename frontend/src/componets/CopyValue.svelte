<script lang="ts">
    import {untrack, type Snippet} from "svelte";
    import {m} from "../paraglide/messages";

    const SWAP_MS = 400;
    const {
        label,
        text,
        masked = false,
        copied = false,
        onCopy,
        onHover,
        face,
    } : {
        label: string;
        text: string;
        masked?: boolean;
        copied?: boolean;
        onCopy?: () => void;
        onHover?: (entering: boolean) => void;
        face?: Snippet;
    } = $props();

    let hovering = $state(false);
    let pointerOver = false;
    let keyFocus = false;

    function sync() {
        const next = pointerOver || keyFocus;
        if (next === hovering) return;
        floorWidth = next ? width : 0;
        hovering = next;
        onHover?.(next);
    }

    function pointer(entering: boolean) {
        pointerOver = entering;
        sync();
    }

    function focusChange(event: FocusEvent) {
        const target = event.currentTarget as HTMLElement;
        keyFocus = event.type === 'focus' && target.matches(':focus-visible');
        sync();
    }

    let valueWidth = $state(0);
    let confirmWidth = $state(0);
    const width = $derived(copied ? confirmWidth : valueWidth);
    let floorWidth = $state(0);
    const hitExtra = $derived(Math.max(0, floorWidth - width));
    let swapping = $state(false);
    let popping = $state(false);
    let settled = false;

    $effect(() => {
        copied;
        if (!settled) {
            settled = true;
            return;
        }
        swapping = true;
        popping = false;
        const frame = requestAnimationFrame(() => (popping = untrack(() => copied)));
        const done = setTimeout(() => (swapping = false), SWAP_MS);
        return () => {
            cancelAnimationFrame(frame);
            clearTimeout(done);
        };
    });

    $effect(() => {
        text;
        if (!untrack(() => hovering)) return;
        swapping = true;
        const done = setTimeout(() => (swapping = false), SWAP_MS);
        return () => clearTimeout(done);
    });
</script>

<button
    class="chip"
    class:held={copied}
    class:popping
    class:swapping
    type="button"
    aria-label={m.field_copyAction({name: label})}
    onclick={onCopy}
    onmouseenter={() => pointer(true)}
    onmouseleave={() => pointer(false)}
    onfocus={focusChange}
    onblur={focusChange}
>
    <span class="hit" style="left: {-hitExtra}px" aria-hidden="true"></span>
    <span class="swap" class:swapping style={width ? `width: ${width}px` : ''}>
        <span class="face" class:on={!copied} bind:clientWidth={valueWidth}>
            {#if face}
                {@render face()}
            {:else}
                <span class:masked>{text}</span>
            {/if}
        </span>
        <span class="face" class:on={copied} bind:clientWidth={confirmWidth} aria-hidden={!copied}>
            <svg viewBox="0 -960 960 960" width="15" height="15" fill="currentColor" aria-hidden="true">
                <path d="M368-240q-33 0-56.5-23.5T288-320v-480q0-33 23.5-56.5T368-880h360q33 0 56.5 23.5T808-800v480q0 33-23.5 56.5T728-240H368ZM232-104q-33 0-56.5-23.5T152-184v-520q0-17 11.5-28.5T192-744q17 0 28.5 11.5T232-704v520h400q17 0 28.5 11.5T672-144q0 17-11.5 28.5T632-104H232Z"/>
            </svg>
            {m.field_copied()}
        </span>
    </span>
</button>

<style>
    .chip {
        display: flex;
        position: relative;
        align-items: center;
        max-width: 100%;
        padding: 5px 10px;
        margin: -5px -10px -5px auto;
        border: 0;
        border-radius: 8px;
        background: transparent;
        font: inherit;
        font-size: 15px;
        color: var(--hint-color);
        cursor: pointer;
        transition: background 150ms ease;
    }

    .chip:hover,
    .chip:focus-visible {
        background: color-mix(in srgb, var(--text-color) 10%, transparent);
        outline: none;
    }

    .chip.held {
        background: color-mix(in srgb, var(--text-color) 10%, transparent);
        outline: none;
        transition: none;
    }

    .chip.popping {
        animation: pop 400ms cubic-bezier(0.22, 1, 0.36, 1);
    }

    @keyframes pop {
        0%   { transform: scale(1); }
        30%  { transform: scale(0.92); }
        65%  { transform: scale(1.035); }
        100% { transform: scale(1); }
    }

    .chip:active:not(.held) {
        background: color-mix(in srgb, var(--text-color) 14%, transparent);
    }

    .hit {
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
    }

    .swap {
        position: relative;
        display: block;
        height: 18px;
        max-width: 100%;
        overflow: hidden;
    }

    .swap.swapping {
        transition: width 340ms cubic-bezier(0.34, 1.42, 0.5, 1);
    }

    .face {
        position: absolute;
        top: 0;
        right: 0;
        display: flex;
        align-items: center;
        gap: 6px;
        width: max-content;
        height: 100%;
        white-space: nowrap;
        opacity: 0;
        transition: opacity 130ms ease;
    }

    .face.on {
        opacity: 1;
    }

    .masked {
        display: inline-block;
        font-weight: bold;
        font-size: 25px;
        line-height: 18px;
        letter-spacing: -0.05em;
    }

    @media (prefers-reduced-motion: reduce) {
        .chip,
        .swap,
        .face {
            transition: none;
        }

        .chip.popping {
            animation: none;
        }
    }
</style>
