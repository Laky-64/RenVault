<script lang="ts">
    const {
        padding,
        radius = '1000px',
        accent,
        onclick,
        disabled = false,
        variant = 'ghost',
        block = false,
        children
    } : {
        padding?: string,
        radius?: string,
        onclick?: () => void,
        accent?: string,
        disabled?: boolean,
        variant?: 'ghost' | 'plain' | 'primary' | 'tinted',
        block?: boolean,
        children: any
    } = $props();

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
    style="padding: {pad}; border-radius: {radius}; --accent-color: {tint}"
    role="button"
    tabindex={disabled ? -1 : 0}
    aria-disabled={disabled}
    onclick={handleClick}
    onkeydown={onKey}
    class={variant}
    class:hasAccent
    class:disabled
    class:block
>
    {#if children}
        {@render children?.()}
    {/if}
</div>

<style>
    div {
        transition: background 150ms ease, opacity 150ms ease, transform 150ms ease;
        display: flex;
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
        transform: scale(0.98);
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
        transform: scale(0.98);
    }

    @media (prefers-reduced-motion: reduce) {
        div {
            transition: background 150ms ease;
        }

        /*noinspection CssUnusedSymbol*/
        div.primary:not(.disabled):active,
        div.tinted:not(.disabled):active {
            transform: none;
        }
    }
</style>
