<script lang="ts">
    import {fade} from "svelte/transition";
    import {observeSize} from "../lib/dom";

    const {
        padding,
        radius = '1000px',
        accent,
        onclick,
        disabled = false,
        variant = 'ghost',
        block = false,
        morph,
        flash,
        children
    } : {
        padding?: string,
        radius?: string,
        onclick?: () => void,
        accent?: string,
        disabled?: boolean,
        variant?: 'ghost' | 'plain' | 'glass' | 'primary' | 'tinted',
        block?: boolean,
        morph?: unknown,
        flash?: unknown,
        children: any
    } = $props();

    const MORPH_MS = 460;
    const FADE_MS = 150;

    const morphing = $state({on: false});
    const flashing = $state({on: false});
    let faceWidth = $state(0);
    let faceHeight = $state(0);
    let morphSettled = false;
    let flashSettled = false;

    $effect(() => {
        morph;
        if (!morphSettled) {
            morphSettled = true;
            return;
        }
        morphing.on = true;
        const done = setTimeout(() => (morphing.on = false), MORPH_MS);
        return () => clearTimeout(done);
    });

    $effect(() => {
        flash;
        if (!flashSettled) {
            flashSettled = true;
            return;
        }
        flashing.on = true;
        const done = setTimeout(() => (flashing.on = false), MORPH_MS);
        return () => clearTimeout(done);
    });

    const pad = $derived(padding ?? (variant === 'ghost' ? '4px' : '13px 20px'));
    const tint = $derived(accent?.length ? accent : 'var(--button-color)');

    let hasAccent = $derived(!!accent?.length);

    function onKey(e: KeyboardEvent) {
        if ((e.key === "Enter" || e.key === " ") && onclick && !disabled) {
            e.preventDefault();
            onclick();
        }
    }

    function handleClick(e: MouseEvent) {
        if (onclick && !disabled) {
            e.preventDefault();
            onclick();
        }
    }
</script>

<div
    style="padding: {pad}; border-radius: {radius}; --accent-color: {tint};"
    role="button"
    tabindex={disabled ? -1 : 0}
    aria-disabled={disabled}
    onclick={handleClick}
    onkeydown={onKey}
    class={variant}
    class:flashing={flashing.on}
    class:hasAccent
    class:disabled
    class:block
>
    {#if morph !== undefined}
        <span
            class="morph"
            class:morphing={morphing.on}
            style={faceWidth ? `width: ${faceWidth}px; height: ${faceHeight}px` : ''}
        >
            {#key morph}
                <span
                    class="face"
                    in:fade={{duration: FADE_MS}}
                    out:fade={{duration: FADE_MS}}
                >
                    {@render children?.()}
                </span>
            {/key}
            <span class="probe" aria-hidden="true" use:observeSize={node => { faceWidth = node.offsetWidth; faceHeight = node.offsetHeight; }}>
                {@render children?.()}
            </span>
        </span>
    {:else if children}
        {@render children?.()}
    {/if}
</div>

<style>
    .morph {
        position: relative;
        display: block;
    }

    .morph.morphing {
        transition:
            width 400ms cubic-bezier(0.34, 1.32, 0.5, 1),
            height 400ms cubic-bezier(0.34, 1.32, 0.5, 1);
    }

    .face {
        position: absolute;
        top: 50%;
        left: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        width: max-content;
        height: max-content;
        transform: translate(-50%, -50%);
    }

    .probe {
        display: flex;
        align-items: center;
        justify-content: center;
        width: max-content;
        height: max-content;
        visibility: hidden;
        pointer-events: none;
    }

    div {
        transition: background 200ms ease, opacity 200ms ease, transform 200ms ease;
        position: relative;
        display: flex;
        overflow: hidden;
        align-items: center;
        justify-content: center;
        --wails-draggable: no-drag;
    }

    div.block {
        width: 100%;
    }

    div.disabled {
        opacity: 0.4;
        cursor: default;
    }

    /*noinspection CssUnusedSymbol*/
    div.ghost.hasAccent {
        background: var(--accent-color);
    }

    /*noinspection CssUnusedSymbol*/
    div.ghost.hasAccent:not(.disabled):hover {
        background: color-mix(in srgb, var(--text-color) 13%, var(--accent-color));
    }

    /*noinspection CssUnusedSymbol*/
    div.ghost.hasAccent:not(.disabled):active {
        background: color-mix(in srgb, var(--text-color) 25%, var(--accent-color));
    }

    /*noinspection CssUnusedSymbol*/
    div.ghost:not(.hasAccent) {
        background: color-mix(in srgb, var(--text-color) 3%, transparent);
    }

    /*noinspection CssUnusedSymbol*/
    div.ghost:not(.hasAccent):not(.disabled):hover {
        background: color-mix(in srgb, var(--text-color) 8%, transparent);
    }

    /*noinspection CssUnusedSymbol*/
    div.ghost:not(.hasAccent):not(.disabled):active {
        background: color-mix(in srgb, var(--text-color) 14%, transparent);
    }

    /*noinspection CssUnusedSymbol*/
    div.plain {
        background: transparent;
    }

    /*noinspection CssUnusedSymbol*/
    div.plain:not(.disabled):hover {
        background: color-mix(in srgb, var(--text-color) 4%, transparent);
    }

    /*noinspection CssUnusedSymbol*/
    div.plain:not(.disabled):active {
        background: color-mix(in srgb, var(--text-color) 8%, transparent);
    }

    /*noinspection CssUnusedSymbol*/
    div.glass {
        --glass-blur: 20px;
        --glass-saturate: 180%;
        background: color-mix(in srgb, var(--text-color) 10%, transparent);
        backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
        -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
        box-shadow:
            0 6px 10px rgb(0 0 0 / 8%),
            inset 0 1px 0 color-mix(in srgb, var(--text-color) 20%, transparent);
        color: var(--text-color);
        cursor: pointer;
    }

    /*noinspection CssUnusedSymbol*/
    div.glass.hasAccent {
        background: var(--accent-color);
        color: var(--button-text-color);
    }

    /*noinspection CssUnusedSymbol*/
    div.glass.hasAccent:not(.disabled):hover {
        background: color-mix(in srgb, var(--text-color) 12%, var(--accent-color));
    }

    /*noinspection CssUnusedSymbol*/
    div.glass.hasAccent:not(.disabled):active {
        background: color-mix(in srgb, var(--text-color) 22%, var(--accent-color));
    }

    /*noinspection CssUnusedSymbol*/
    div.glass:not(.hasAccent):not(.disabled):hover {
        background: color-mix(in srgb, var(--text-color) 16%, transparent);
    }

    /*noinspection CssUnusedSymbol*/
    div.glass:not(.hasAccent):not(.disabled):active {
        background: color-mix(in srgb, var(--text-color) 22%, transparent);
    }

    /*noinspection CssUnusedSymbol*/
    div.glass:not(.disabled):active {
        transform: scale(1.08);
    }

    /*noinspection CssUnusedSymbol*/
    div.glass.flashing {
        animation: flare 460ms ease-out;
    }

    @keyframes flare {
        0% {
            backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
            -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
        }
        30% {
            backdrop-filter: blur(var(--glass-blur)) saturate(460%) brightness(112%);
            -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(460%) brightness(112%);
        }
        100% {
            backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
            -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
        }
    }

    @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
        /*noinspection CssUnusedSymbol*/
        div.glass {
            background: color-mix(in srgb, var(--text-color) 14%, var(--section-bg-color));
        }
    }

    /*noinspection CssUnusedSymbol*/
    div.primary,
    div.tinted {
        font-size: 15px;
        font-weight: 600;
        cursor: pointer;
    }

    /*noinspection CssUnusedSymbol*/
    div.primary {
        background: var(--accent-color);
        color: var(--button-text-color);
    }

    /*noinspection CssUnusedSymbol*/
    div.primary:not(.disabled):hover {
        background: color-mix(in srgb, var(--text-color) 12%, var(--accent-color));
    }

    /*noinspection CssUnusedSymbol*/
    div.primary:not(.disabled):active {
        background: color-mix(in srgb, var(--text-color) 22%, var(--accent-color));
        transform: scale(1.06);
    }

    /*noinspection CssUnusedSymbol*/
    div.tinted {
        background: color-mix(in srgb, var(--accent-color) 16%, transparent);
        color: var(--accent-color);
    }

    /*noinspection CssUnusedSymbol*/
    div.tinted:not(.disabled):hover {
        background: color-mix(in srgb, var(--accent-color) 24%, transparent);
    }

    /*noinspection CssUnusedSymbol*/
    div.tinted:not(.disabled):active {
        background: color-mix(in srgb, var(--accent-color) 32%, transparent);
        transform: scale(1.06);
    }

    @media (prefers-reduced-motion: reduce) {
        div {
            transition: background 150ms ease;
        }

        .morph.morphing {
            transition: none;
        }

        div.glass.flashing {
            animation: none;
        }

        /*noinspection CssUnusedSymbol*/
        div.primary:not(.disabled):active,
        div.tinted:not(.disabled):active,
        div.glass:not(.disabled):active {
            transform: none;
        }
    }
</style>
