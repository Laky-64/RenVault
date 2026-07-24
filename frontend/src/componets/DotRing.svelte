<script lang="ts">
    import type {Snippet} from "svelte";

    const {
        size = 180,
        rings = 4,
        slots = 24,
        gap = false,
        innerRatio = 0.695,
        dotRatio = 0.104,
        hueOffset = 0,
        saturation = 88,
        lightness = 15,
        spinDuration = 0,
        entrance = 640,
        stagger = 620,
        shimmer = 3600,
        children,
    }: {
        size?: number;
        rings?: number;
        slots?: number;
        gap?: boolean;
        innerRatio?: number;
        dotRatio?: number;
        hueOffset?: number;
        saturation?: number;
        lightness?: number;
        spinDuration?: number;
        entrance?: number;
        stagger?: number;
        shimmer?: number;
        children?: Snippet;
    } = $props();

    const HUE = [192, 196, 206, 233, 273, 294, 325, 346, 362, 373, 384, 397, 552];
    const LUM = [59, 55, 56, 59, 56, 50, 51, 55, 60, 63, 66, 64, 59];
    const SEAM_SATURATION = 8;

    function sample(table: number[], t: number): number {
        const pos = t * (table.length - 1);
        const i = Math.min(Math.floor(pos), table.length - 2);
        return table[i] + (table[i + 1] - table[i]) * (pos - i);
    }

    type Dot = {
        x: number;
        y: number;
        r: number;
        color: string;
        delay: number;
        duration: number;
        phase: number;
    };

    function scatter(a: number, b: number): number {
        const v = Math.sin(a * 12.9898 + b * 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    const dots: Dot[] = $derived.by(() => {
        const outer = size / 2 / (1 + dotRatio / 2);
        const step = 360 / slots;
        const seam = slots - 1;
        const count = gap ? slots - 1 : slots;
        const shrink = rings > 1 ? Math.pow(innerRatio, 1 / (rings - 1)) : 1;
        const result: Dot[] = [];

        for (let ring = 0; ring < rings; ring++) {
            const radius = outer * Math.pow(shrink, rings - 1 - ring);
            const offset = (ring % 2) * (step / 2);

            for (let i = 0; i < count; i++) {
                const angle = ((i * step + offset) * Math.PI) / 180;
                const t = i / slots;
                const away = Math.min(Math.abs(i - seam), slots - Math.abs(i - seam));
                const ramp = Math.min(1, away / 2.2);
                const sat = SEAM_SATURATION + (saturation - SEAM_SATURATION) * ramp;
                result.push({
                    x: Math.sin(angle) * radius,
                    y: -Math.cos(angle) * radius,
                    r: (radius * dotRatio) / 2,
                    color: `hsl(${(sample(HUE, t) + hueOffset) % 360} ${sat.toFixed(1)}% ${sample(LUM, t) + lightness}%)`,
                    delay: stagger * (1 - Math.pow(1 - t, 1.7)) + ring * 70,
                    duration: entrance * (0.85 + 0.35 * scatter(i + 1, ring + 1)),
                    phase: t * (shimmer || 0) + ring * 110,
                });
            }
        }
        return result;
    });

    const shimmerStart = $derived(stagger + rings * 70 + entrance * 1.2);

    function spring(x: number): number {
        if (x >= 1) return 1;
        return 1 - Math.exp(-6 * x) * Math.cos(x * Math.PI * 2.2);
    }

    let canvas: HTMLCanvasElement | undefined = $state();

    $effect(() => {
        const el = canvas;
        if (!el) return;
        const ctx = el.getContext("2d");
        if (!ctx) return;

        const list = dots;
        const side = size;
        const start = shimmerStart;
        const still = typeof matchMedia !== "undefined"
            && matchMedia("(prefers-reduced-motion: reduce)").matches;

        const last = list.reduce((m, d) => Math.max(m, d.delay + d.duration), 0);
        let frame = 0;
        const t0 = performance.now();

        function paint(now: number) {
            const t = still ? last : now - t0;
            const dpr = window.devicePixelRatio || 1;
            const backing = Math.round(side * dpr);
            const scale = backing / side;
            if (el!.width !== backing) {
                el!.width = backing;
                el!.height = backing;
            }
            ctx!.setTransform(1, 0, 0, 1, 0, 0);
            ctx!.clearRect(0, 0, backing, backing);
            ctx!.setTransform(scale, 0, 0, scale, 0, 0);
            ctx!.save();
            ctx!.translate(side / 2, side / 2);
            const fit = (side / 2 - 1 / scale) / (side / 2);
            ctx!.scale(fit, fit);
            if (spinDuration > 0 && !still) {
                ctx!.rotate(((t % spinDuration) / spinDuration) * Math.PI * 2);
            }

            for (const dot of list) {
                const p = (t - dot.delay) / dot.duration;
                if (p <= 0) continue;

                let scale = spring(p);
                let alpha = Math.min(1, p / 0.3);

                if (p >= 1 && shimmer > 0 && !still) {
                    const b = (t - start - dot.phase) / shimmer;
                    if (b > 0) {
                        const u = 0.5 - 0.5 * Math.cos(b * Math.PI * 2);
                        alpha = 1 - 0.18 * u;
                        scale = 1 - 0.06 * u;
                    }
                }

                ctx!.globalAlpha = alpha;
                ctx!.fillStyle = dot.color;
                ctx!.beginPath();
                ctx!.arc(dot.x, dot.y, dot.r * scale, 0, Math.PI * 2);
                ctx!.fill();
            }
            ctx!.restore();

            const done = still || (t > last && shimmer <= 0 && spinDuration <= 0);
            if (!done) frame = requestAnimationFrame(paint);
        }

        frame = requestAnimationFrame(paint);
        return () => cancelAnimationFrame(frame);
    });
</script>

<div class="ring" style="--size: {size}px">
    <canvas bind:this={canvas} style="width: {size}px; height: {size}px" aria-hidden="true"></canvas>

    {#if children}
        <div class="center" style="--center-delay: {(shimmerStart * 0.5).toFixed(0)}ms">
            {@render children()}
        </div>
    {/if}
</div>

<style>
    .ring {
        position: relative;
        width: var(--size);
        height: var(--size);
        flex-shrink: 0;
    }

    canvas {
        display: block;
    }

    .center {
        position: absolute;
        left: 50%;
        top: 50%;
        translate: -50% -50%;
        display: grid;
        place-items: center;
        width: calc(var(--size) * 0.52);
        height: calc(var(--size) * 0.52);
        animation: rise 620ms var(--center-delay) cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    @keyframes rise {
        from {
            opacity: 0;
            scale: 0.6;
        }
        to {
            opacity: 1;
            scale: 1;
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .center {
            animation: none;
        }
    }
</style>
