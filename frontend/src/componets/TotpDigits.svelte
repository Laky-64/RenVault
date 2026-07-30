<script lang="ts">
    import {fly} from "svelte/transition";

    const {
        code = '',
        group = 3,
        source,
    } : {
        code?: string;
        group?: number;
        source?: unknown;
    } = $props();

    const FLY_DURATION = 300;
    const FLY_STAGGER = 45;

    const chars = $derived([...code]);

    let animate = $state(false);

    $effect(() => {
        source;
        animate = false;
    });

    $effect(() => {
        if (code) animate = true;
    });
</script>

<span class="digits">
    {#each chars as char, i (i)}
        <span class="digit-slot" class:gap={group > 0 && i % group === group - 1 && i < chars.length - 1}>
            {#key code}
                <span
                    class="digit"
                    in:fly={{y: -8, duration: animate ? FLY_DURATION : 0, delay: animate ? i * FLY_STAGGER : 0}}
                    out:fly={{y: 8, duration: animate ? FLY_DURATION : 0, delay: animate ? i * FLY_STAGGER : 0}}
                >{char}</span>
            {/key}
        </span>
    {/each}
</span>

<style>
    .digits {
        display: flex;
        font-weight: 600;
        white-space: nowrap;
        font-variant-numeric: tabular-nums;
    }

    .digit-slot {
        display: grid;
        overflow: hidden;
    }

    .digit-slot.gap {
        margin-right: 5px;
    }

    .digit {
        grid-area: 1 / 1;
    }
</style>
