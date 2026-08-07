<script lang="ts">
    import {onMount} from "svelte";
    import {TOTP_PERIOD} from "../lib/totp.svelte";
    import {prefersReducedMotion} from "../lib/dom";

    const {
        size = 20,
        period = TOTP_PERIOD,
    } : {
        size?: number;
        period?: number;
    } = $props();

    const RADIUS = 6.4;
    const RING = 2 * Math.PI * RADIUS;
    const REFILL_MS = 480;

    const reduced = prefersReducedMotion();

    let now = $state(Date.now());
    let refilledAt = $state(0);

    const left = $derived(Math.max(0, Math.min(1, (period - ((now / 1000) % period)) / period)));

    const filled = $derived.by(() => {
        const gone = now - refilledAt;
        if (refilledAt === 0 || gone >= REFILL_MS) return left;
        const t = gone / REFILL_MS;
        return left * (1 - Math.pow(1 - t, 3));
    });

    const dash = $derived(RING * filled);

    onMount(() => {
        let frame = 0;
        let slot = Math.floor(Date.now() / 1000 / period);

        const tick = () => {
            const stamp = Date.now();
            const current = Math.floor(stamp / 1000 / period);
            if (current !== slot) {
                slot = current;
                if (!reduced) refilledAt = stamp;
            }
            now = stamp;
            frame = requestAnimationFrame(tick);
        };
        tick();

        return () => cancelAnimationFrame(frame);
    });
</script>

<svg class="ring" viewBox="0 0 20 20" width={size} height={size} aria-hidden="true">
    <circle class="track" cx="10" cy="10" r={RADIUS}/>
    <circle class="progress" cx="10" cy="10" r={RADIUS} stroke-dasharray="{dash} {RING}"/>
</svg>

<style>
    .ring {
        flex-shrink: 0;
        transform: rotate(-90deg);
    }

    .track {
        fill: none;
        stroke: color-mix(in srgb, var(--text-color) 14%, transparent);
        stroke-width: 2.8;
    }

    .progress {
        fill: none;
        stroke: var(--code-progress-color);
        stroke-width: 2.8;
        stroke-linecap: round;
    }
</style>
