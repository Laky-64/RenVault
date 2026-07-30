<script lang="ts">
    import {onMount} from "svelte";
    import CopyValue from "./CopyValue.svelte";
    import TotpDigits from "./TotpDigits.svelte";
    import {totpSlot} from "../lib/totp.svelte";

    const {
        label,
        load,
        period = 30,
        copied = false,
        onCopy,
    } : {
        label: string;
        load: () => Promise<string>;
        period?: number;
        copied?: boolean;
        onCopy?: () => void;
    } = $props();

    const RADIUS = 6.4;
    const RING = 2 * Math.PI * RADIUS;

    let code = $state('');
    let remaining = $state(0);

    const dash = $derived(RING * Math.max(0, Math.min(1, remaining / period)));

    let source: (() => Promise<string>) | null = null;
    const slot = $derived(totpSlot(period));

    $effect(() => {
        slot;
        if (load !== source) {
            source = load;
            code = '';
        }
        let alive = true;
        load()
            .then(value => {
                if (alive) code = value;
            })
            .catch(() => {
                if (alive) code = '';
            });
        return () => (alive = false);
    });

    onMount(() => {
        let frame = 0;

        const tick = () => {
            remaining = period - ((Date.now() / 1000) % period);
            frame = requestAnimationFrame(tick);
        };
        tick();

        return () => cancelAnimationFrame(frame);
    });
</script>

<CopyValue {label} text={code} {copied} {onCopy}>
    {#snippet face()}
        <span class="totp">
            <svg class="ring" viewBox="0 0 20 20" width="20" height="20" aria-hidden="true">
                <circle class="track" cx="10" cy="10" r={RADIUS}/>
                <circle
                    class="progress"
                    cx="10"
                    cy="10"
                    r={RADIUS}
                    stroke-dasharray="{dash} {RING}"
                />
            </svg>
            <TotpDigits {code} source={load}/>
        </span>
    {/snippet}
</CopyValue>

<style>
    .totp {
        display: flex;
        align-items: center;
        gap: 5px;
    }

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
