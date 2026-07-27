<script lang="ts">
    import type {Snippet} from "svelte";

    let {open = false, children}: {open?: boolean; children: Snippet} = $props();
</script>

<div class="fold" class:open>
    <div class="inner">
        {@render children()}
    </div>
</div>

<style>
    .fold {
        display: grid;
        grid-template-rows: 0fr;
        opacity: 0;
        transition:
            grid-template-rows 340ms cubic-bezier(0.22, 1, 0.36, 1),
            opacity 240ms ease;
    }

    .fold.open {
        grid-template-rows: 1fr;
        opacity: 1;
    }

    .inner {
        min-height: 0;
        overflow: hidden;
    }

    @media (prefers-reduced-motion: reduce) {
        .fold {
            transition: none;
        }
    }
</style>
