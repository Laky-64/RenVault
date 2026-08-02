<script lang="ts">
    import type {Snippet} from "svelte";

    let {
        open = false,
        bleed = false,
        children,
    }: {
        open?: boolean;
        bleed?: boolean;
        children: Snippet;
    } = $props();
</script>

<div class="fold" class:open class:bleed inert={!open}>
    <div class="inner">
        {@render children()}
    </div>
</div>

<style>
    .fold {
        display: grid;
        width: 100%;
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

    /*noinspection CssUnusedSymbol*/
    .fold.bleed {
        width: calc(100% + 20px);
        margin-inline: -10px;
        margin-block: -5px;
    }

    .inner {
        min-height: 0;
        min-width: 0;
        overflow: hidden;
    }

    /*noinspection CssUnusedSymbol*/
    .fold.bleed > .inner {
        padding: 5px 10px;
    }

    @media (prefers-reduced-motion: reduce) {
        .fold {
            transition: none;
        }
    }
</style>
