<script lang="ts">
    import type {Snippet} from "svelte";

    const {children, invalid = false}: {children: Snippet; invalid?: boolean} = $props();
</script>

<div class="card" class:invalid>
    {@render children()}
</div>

<style>
    .card {
        display: flex;
        flex-direction: column;
        width: 100%;
        border-radius: 20px;
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
        right: 20px;
        left: 20px;
        top: 0;
        height: 1px;
        background: color-mix(in srgb, var(--text-color) 10%, transparent);
    }
</style>
