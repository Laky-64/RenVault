<script lang="ts">
    const {
        padding = '4px',
        accent,
        onclick,
        disabled,
        children
    } : {
        padding?: string,
        onclick?: () => void,
        accent?: string,
        disabled?: boolean,
        children: any
    } = $props();

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

<div style="padding: {padding}; --accent-color: {accent}" role="button" tabindex="0" onclick={handleClick} onkeydown={onKey} class:hasAccent>
    {#if children}
        {@render children?.()}
    {/if}
</div>

<style>
    div {
        border-radius: 1000px;
        transition: background 150ms ease;
        display: flex;
        align-items: center;
        justify-content: center;
        --wails-draggable: no-drag;
    }

    div.hasAccent {
        background: var(--accent-color);
    }

    div.hasAccent:hover {
        background: color-mix(in srgb, var(--text-color) 13%, var(--accent-color));
    }

    div.hasAccent:active {
        background: color-mix(in srgb, var(--text-color) 25%, var(--accent-color));
    }

    div:not(.hasAccent) {
        background: color-mix(in srgb, var(--text-color) 3%, transparent);
    }

    div:not(.hasAccent):hover {
        background: color-mix(in srgb, var(--text-color) 8%, transparent);
    }

    div:not(.hasAccent):active {
        background: color-mix(in srgb, var(--text-color) 14%, transparent);
    }
</style>