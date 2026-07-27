<script lang="ts">
    import {onMount} from "svelte";
    import {fly} from "svelte/transition";

    const {
        code = '',
        group = 3,
    } : {
        code?: string;
        group?: number;
    } = $props();

    const FLY_DURATION = 300;
    const FLY_STAGGER = 45;

    const chars = $derived([...code]);

    let mounted = $state(false);
    onMount(() => {
        mounted = true;
    });
</script>

<span class="digits">
    {#each chars as char, i (i)}
        <span class="digit-slot" class:gap={group > 0 && i % group === group - 1 && i < chars.length - 1}>
            {#key code}
                <span
                    class="digit"
                    in:fly={{y: -8, duration: mounted ? FLY_DURATION : 0, delay: mounted ? i * FLY_STAGGER : 0}}
                    out:fly={{y: 8, duration: FLY_DURATION, delay: i * FLY_STAGGER}}
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
