<script lang="ts">
    import {fade} from "svelte/transition";
    import LoadingProgress from "./LoadingProgress.svelte";
    import {prefersReducedMotion} from "../lib/dom";

    const {label = ''}: {label?: string} = $props();

    const reduced = prefersReducedMotion();
</script>

<div class="veil" role="status" aria-live="polite" aria-label={label} transition:fade={{duration: reduced ? 0 : 250}}>
    <div class="tile">
        <LoadingProgress/>
    </div>
</div>

<style>
    .veil {
        position: absolute;
        inset: 0;
        z-index: 30;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgb(0 0 0 / 30%);
    }

    .tile {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 95px;
        height: 95px;
        border-radius: 25px;
        background: color-mix(in srgb, var(--secondary-bg-color) 55%, transparent);
        backdrop-filter: saturate(180%) blur(20px);
        -webkit-backdrop-filter: saturate(180%) blur(20px);
    }

    @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
        .tile {
            background: color-mix(in srgb, var(--text-color) 14%, var(--section-bg-color));
        }
    }
</style>
