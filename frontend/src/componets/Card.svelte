<script lang="ts">
    import type {Snippet} from "svelte";

    const {
        padding = '0',
        radius = "var(--zone-border-radius)",
        children,
    } : {
        padding?: string;
        radius?: string;
        children: Snippet;
    } = $props();
</script>

<div class="card" style="padding: {padding}; border-radius: {radius}">
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
        overflow: hidden;
        background: var(--section-bg-color);
    }
</style>
