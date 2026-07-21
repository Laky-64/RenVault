<script lang="ts">
    import type {Password, Zone} from "./ZoneContainer";
    import {untrack} from "svelte";
    import PasswordItem from "./PasswordItem.svelte";
    import VirtualList from "./VirtualList.svelte";

    const {
        zone,
        on_selected,
    } : {
        zone: Zone;
        on_selected?: (password: Password) => void;
    } = $props();

    const BAR_HEIGHT = 60;
    const HYSTERESIS = 6;
    let scrollOffset = $state(0);
    let titleHeight = $state(0);
    let stuck = $state(false);
    let selectedPassword: Password | null = $state(null);

    $effect(() => {
        const offset = scrollOffset;
        const threshold = titleHeight - BAR_HEIGHT;
        if (titleHeight === 0) return;
        untrack(() => {
            if (!stuck && offset > threshold + HYSTERESIS) stuck = true;
            else if (stuck && offset < threshold - HYSTERESIS) stuck = false;
        });
    });
</script>

<div class="container" style="--bar-height: {BAR_HEIGHT}px">
    <VirtualList items={zone.passwords} resetKey={zone} bind:scrollOffset>
        {#snippet header()}
            <div class="large-title" bind:clientHeight={titleHeight}>
                <h1>{zone.text}</h1>
                <p>{zone.passwords.length} elementi</p>
            </div>
        {/snippet}
        {#snippet item(entry, index)}
            <PasswordItem
                password={entry}
                onclick={() => {
                    if (on_selected) {
                        on_selected(entry);
                        selectedPassword = entry;
                    }
                }}
                selected={entry == selectedPassword}
                last={index === zone.passwords.length - 1}/>
        {/snippet}
    </VirtualList>
    <div class="top-bar" class:stuck aria-hidden="true">
        <div class="bar-backdrop"></div>
        <div class="compact-title">
            <span class="compact-name">{zone.text}</span>
            <span class="compact-count">{zone.passwords.length} elementi</span>
        </div>
    </div>
</div>

<style>
    .container {
        position: relative;
        flex: 1;
        max-width: 350px;
        min-width: 0;
        height: 100%;
    }

    .container::after {
        content: '';
        position: absolute;
        right: 0;
        width: 1px;
        top: 0;
        bottom: 0;
        background: color-mix(in srgb, var(--text-color) 12%, transparent);
    }

    .large-title {
        padding-top: 15px;
        padding-bottom: 15px;
        padding-inline: 15px;
    }

    .large-title > h1 {
        font-size: 30px;
        font-weight: bold;
        color: var(--text-color);
        margin: 0;
    }

    .large-title > p {
        margin: 0;
        font-size: 12px;
        font-weight: 500;
        color: var(--subtitle-text-color);
    }

    .top-bar {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: var(--bar-height);
        display: flex;
        align-items: center;
        justify-content: center;
        pointer-events: none;
    }

    .bar-backdrop {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: var(--bar-height);
        opacity: 0;
        transition: opacity 200ms ease;
        background: linear-gradient(
            to bottom,
            var(--secondary-bg-color) 0%,
            color-mix(in srgb, var(--secondary-bg-color) 95%, transparent) 30%,
            color-mix(in srgb, var(--secondary-bg-color) 78%, transparent) 60%,
            color-mix(in srgb, var(--secondary-bg-color) 0%, transparent) 90%
        );
    }

    .stuck .bar-backdrop {
        opacity: 1;
    }

    .compact-title {
        position: relative;
        display: flex;
        flex-direction: column;
        align-items: center;
        line-height: 1.15;
    }

    .compact-name,
    .compact-count {
        opacity: 0;
        transform: translateY(9px);
        filter: blur(4px);
        margin-bottom: 2px;
        transition:
            opacity 260ms ease,
            transform 260ms cubic-bezier(0.22, 1, 0.36, 1),
            filter 260ms ease;
        will-change: opacity, transform, filter;
    }

    .compact-name {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-color);
        transition-delay: 50ms;
    }

    .compact-count {
        font-size: 11px;
        font-weight: 500;
        color: var(--subtitle-text-color);
        transition-delay: 0ms;
    }

    .stuck .compact-name,
    .stuck .compact-count {
        opacity: 1;
        transform: none;
        filter: none;
    }

    .stuck .compact-name {
        transition-delay: 0ms;
    }

    .stuck .compact-count {
        transition-delay: 90ms;
    }
</style>
