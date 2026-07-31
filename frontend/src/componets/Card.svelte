<script lang="ts">
    import type {Snippet} from "svelte";

    const {
        padding = '0',
        children,
    } : {
        padding?: string;
        children: Snippet;
    } = $props();
</script>

<div class="card" style="padding: {padding}">
    {@render children()}
</div>

<style>
    .card {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 100%;
        max-width: 600px;
        margin-inline: auto;
        border-radius: var(--zone-border-radius);
        background: var(--section-bg-color);
    }
</style>
