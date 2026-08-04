<script lang="ts">
    import type {Snippet} from "svelte";

    const {
        children,
        invalid = false,
        radius = '20px',
        left = '20px',
        right = '20px',
    }: {
        children: Snippet;
        invalid?: boolean;
        radius?: string;
        left?: string;
        right?: string;
    } = $props();
</script>

<div class="card" class:invalid style="border-radius: {radius};--left-margin: {left};--right-margin: {right};">
    {@render children()}
</div>

<style>
    .card {
        display: flex;
        flex-direction: column;
        width: 100%;
        background: color-mix(in srgb, var(--text-color) 8%, transparent);
        overflow: hidden;
        border: 0 solid transparent;
        transition: border 200ms ease;
    }

    .card.invalid {
        border: 2px solid var(--destructive-text-color);
    }

    .card > :global(:not(:first-child)) {
        position: relative;
    }

    .card > :global(:not(:first-child))::after {
        content: '';
        position: absolute;
        left: var(--left-margin);
        right: var(--right-margin);
        top: 0;
        height: 1px;
        background: var(--hairline-color);
    }
</style>
