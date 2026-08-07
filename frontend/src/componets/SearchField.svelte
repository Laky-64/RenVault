<script lang="ts">
    import Button from "./Button.svelte";
    import {m} from "../paraglide/messages";
    import {CLOSE} from "../lib/icons";

    let {
        value = $bindable(''),
        placeholder,
        glass = false,
    } : {
        value?: string;
        placeholder: string;
        glass?: boolean;
    } = $props();

    let field: HTMLInputElement | undefined = $state();

    function clear() {
        value = '';
        field?.focus({preventScroll: true});
    }

    function onKey(event: KeyboardEvent) {
        if (event.key === 'Escape' && value !== '') {
            event.stopPropagation();
            clear();
        }
    }
</script>

<div class="search" class:glass-surface={glass} class:flat={!glass}>
    <svg class="glass" viewBox="0 -960 960 960" width="18" height="18" aria-hidden="true">
        <path d="M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l224 224q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z"
              fill="var(--subtitle-text-color)"/>
    </svg>

    <input
        bind:this={field}
        bind:value
        type="search"
        autocomplete="off"
        spellcheck="false"
        {placeholder}
        onkeydown={onKey}
    />

    {#if value !== ''}
        <div class="clear">
            <Button variant="plain" padding="4px" radius="999px" onclick={clear}>
                <svg viewBox="0 -960 960 960" width="14" height="14" role="img" aria-label={m.search_clear()}>
                    <path d={CLOSE} fill="var(--subtitle-text-color)"/>
                </svg>
            </Button>
        </div>
    {/if}
</div>

<style>
    .search {
        transition: background-color 200ms ease;
        display: flex;
        align-items: center;
        gap: 7px;
        width: 100%;
        height: 40px;
        padding-inline: 13px;
        border-radius: 999px;
    }

    .flat {
        background: color-mix(in srgb, var(--text-color) 8%, transparent);
    }

    .glass {
        flex-shrink: 0;
    }

    input {
        flex: 1;
        min-width: 0;
        border: 0;
        background: none;
        font: inherit;
        font-size: 15px;
        color: var(--text-color);
        caret-color: var(--button-color);
    }

    input:focus {
        outline: none;
    }

    input::placeholder {
        color: var(--subtitle-text-color);
    }

    input::-webkit-search-cancel-button {
        display: none;
    }

    .clear {
        display: flex;
        flex-shrink: 0;
    }
</style>
