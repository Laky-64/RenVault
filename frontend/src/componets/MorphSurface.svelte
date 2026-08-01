<script lang="ts">
    import {type Snippet, untrack} from "svelte";
    import {motionMs} from "../lib/motion";
    import {observeSize} from "../lib/dom";
    import {type Box, type ContentAnchor, type Placer, seedBox} from "../lib/morph";

    let {
        open = $bindable(false),
        active = $bindable(false),
        anchor,
        place,
        radius = 20,
        scrim = false,
        contentAnchor = 'center',
        dismissable = true,
        pressScale = false,
        surface = 'glass-surface',
        mode = 'box',
        openMs,
        closeMs,
        seed: seedFace,
        children,
    } : {
        open?: boolean;
        active?: boolean;
        anchor?: HTMLElement;
        place: Placer;
        radius?: number | string;
        scrim?: boolean;
        contentAnchor?: ContentAnchor;
        dismissable?: boolean;
        pressScale?: boolean;
        surface?: string;
        mode?: 'box' | 'zoom';
        openMs?: number;
        closeMs?: number;
        seed?: Snippet;
        children: Snippet;
    } = $props();

    const zoom = $derived(mode === 'zoom');
    const OPEN_MS = $derived(openMs ?? (zoom ? 190 : 300));
    const CLOSE_MS = $derived(closeMs ?? (zoom ? 240 : 280));

    let rect: DOMRect | undefined = $state();
    let natural = $state({width: 0, height: 0});
    let grown = $state(false);
    let viewport = $state({width: 0, height: 0});

    function measure() {
        if (!anchor) return;
        rect = anchor.getBoundingClientRect();
        viewport = {width: window.innerWidth, height: window.innerHeight};
    }

    $effect(() => {
        if (!open) {
            grown = false;
            const timer = setTimeout(() => untrack(() => (active = false)), motionMs(CLOSE_MS));
            return () => clearTimeout(timer);
        }
        untrack(() => (active = true));
        measure();
        const frame = requestAnimationFrame(() => (grown = true));
        const onResize = () => measure();
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && dismissable) open = false;
        };
        window.addEventListener('resize', onResize);
        window.addEventListener('keydown', onKey);
        return () => {
            cancelAnimationFrame(frame);
            window.removeEventListener('resize', onResize);
            window.removeEventListener('keydown', onKey);
        };
    });

    const target = $derived.by(() => {
        viewport;
        return rect ? place(natural, rect) : undefined;
    });

    function css(box: Box, corner: string): string {
        return `${box.x.side}: ${box.x.value}px; ${box.y.side}: ${box.y.value}px;`
            + ` width: ${box.width}px; height: ${box.height}px; border-radius: ${corner}`;
    }

    const corner = $derived(typeof radius === 'number' ? `${radius}px` : radius);

    const shape = $derived.by(() => {
        if (!target || !rect) return '';
        return grown
            ? css(target, corner)
            : css(seedBox(rect, target), `${rect.height / 2}px`);
    });

    const contentSize = $derived(zoom && target
        ? `width: ${target.width}px; height: ${target.height}px`
        : '');

    const MIN_SEED_SCALE = 0.6;
    const seedScale = $derived.by(() => {
        if (!target || !rect || !zoom) return 1;
        const ratio = Math.min(rect.width / target.width, rect.height / target.height);
        return Math.max(ratio, MIN_SEED_SCALE);
    });
</script>

{#if active}
    {#if scrim}
        <div class="scrim" class:lit={grown}></div>
    {/if}
    {#if open && dismissable}
        <!-- svelte-ignore a11y_no_static_element_interactions -->
        <div class="catcher" onpointerdown={() => (open = false)}></div>
    {/if}
    <div
        class="surface {grown ? surface : 'glass-surface'}"
        class:grown
        class:zoom
        class:press={pressScale}
        style="{shape}; --open-ms: {motionMs(OPEN_MS)}ms; --close-ms: {motionMs(CLOSE_MS)}ms;
               --seed-scale: {seedScale}"
    >
        {#if seedFace}
            <div class="seed" aria-hidden="true">{@render seedFace()}</div>
        {/if}
        <div
            class="content {zoom ? 'center' : contentAnchor}"
            style={contentSize}
            use:observeSize={node => (natural = {width: node.offsetWidth, height: node.offsetHeight})}
        >
            {@render children()}
        </div>
    </div>
{/if}

<style>
    .scrim,
    .catcher {
        position: fixed;
        inset: 0;
        z-index: 40;
    }

    .scrim {
        background: rgb(0 0 0 / 45%);
        opacity: 0;
        transition: opacity var(--close-ms, 280ms) ease;
        pointer-events: none;
    }

    /*noinspection CssUnusedSymbol*/
    .scrim.lit {
        opacity: 1;
        transition: opacity var(--open-ms, 300ms) ease;
    }

    .surface {
        position: fixed;
        z-index: 41;
        overflow: hidden;
        color: var(--text-color);
        transform: scale(1);
        transition:
            transform 250ms ease,
            width calc(var(--close-ms) * 0.86) cubic-bezier(0.2, 1.34, 0.36, 1),
            height calc(var(--close-ms) * 0.86) cubic-bezier(0.2, 1.34, 0.36, 1) calc(var(--close-ms) * 0.14),
            left calc(var(--close-ms) * 0.86) cubic-bezier(0.2, 1.34, 0.36, 1),
            right calc(var(--close-ms) * 0.86) cubic-bezier(0.2, 1.34, 0.36, 1),
            top calc(var(--close-ms) * 0.86) cubic-bezier(0.2, 1.34, 0.36, 1) calc(var(--close-ms) * 0.14),
            bottom calc(var(--close-ms) * 0.86) cubic-bezier(0.2, 1.34, 0.36, 1) calc(var(--close-ms) * 0.14),
            border-radius calc(var(--close-ms) * 0.57) ease calc(var(--close-ms) * 0.21),
            background-color calc(var(--close-ms) * 0.54) ease calc(var(--close-ms) * 0.32),
            box-shadow calc(var(--close-ms) * 0.54) ease calc(var(--close-ms) * 0.32);
    }

    /*noinspection CssUnusedSymbol*/
    .surface.grown {
        transition:
            transform 250ms ease,
            height var(--open-ms) cubic-bezier(0.2, 1.34, 0.4, 1),
            width var(--open-ms) cubic-bezier(0.16, 1.36, 0.36, 1) 30ms,
            top var(--open-ms) cubic-bezier(0.2, 1.34, 0.4, 1),
            bottom var(--open-ms) cubic-bezier(0.2, 1.34, 0.4, 1),
            left var(--open-ms) cubic-bezier(0.16, 1.36, 0.36, 1) 30ms,
            right var(--open-ms) cubic-bezier(0.16, 1.36, 0.36, 1) 30ms,
            border-radius calc(var(--open-ms) * 0.35) ease-out,
            background-color calc(var(--open-ms) * 0.5) ease,
            box-shadow calc(var(--open-ms) * 0.5) ease;
    }

    /*noinspection CssUnusedSymbol*/
    .surface.zoom {
        transition:
            width var(--close-ms) cubic-bezier(0.2, 1.34, 0.36, 1),
            height var(--close-ms) cubic-bezier(0.2, 1.34, 0.36, 1),
            left var(--close-ms) cubic-bezier(0.2, 1.34, 0.36, 1),
            right var(--close-ms) cubic-bezier(0.2, 1.34, 0.36, 1),
            top var(--close-ms) cubic-bezier(0.2, 1.34, 0.36, 1),
            bottom var(--close-ms) cubic-bezier(0.2, 1.34, 0.36, 1),
            border-radius calc(var(--close-ms) * 0.7) ease,
            background-color calc(var(--close-ms) * 0.6) ease calc(var(--close-ms) * 0.35),
            box-shadow calc(var(--close-ms) * 0.6) ease calc(var(--close-ms) * 0.35);
    }

    /*noinspection CssUnusedSymbol*/
    .surface.zoom.grown {
        transition:
            width var(--open-ms) cubic-bezier(0.22, 0.7, 0.45, 1),
            height var(--open-ms) cubic-bezier(0.22, 0.7, 0.45, 1),
            left var(--open-ms) cubic-bezier(0.22, 0.7, 0.45, 1),
            right var(--open-ms) cubic-bezier(0.22, 0.7, 0.45, 1),
            top var(--open-ms) cubic-bezier(0.22, 0.7, 0.45, 1),
            bottom var(--open-ms) cubic-bezier(0.22, 0.7, 0.45, 1),
            border-radius calc(var(--open-ms) * 0.5) ease-out,
            background-color calc(var(--open-ms) * 0.5) ease,
            box-shadow calc(var(--open-ms) * 0.5) ease;
    }

    /*noinspection CssUnusedSymbol*/
    .surface.zoom > .content {
        opacity: 0;
        filter: none;
        transform: translate(-50%, -50%) scale(var(--seed-scale));
        transition:
            transform var(--close-ms) cubic-bezier(0.2, 1.34, 0.36, 1),
            opacity calc(var(--close-ms) * 0.3) ease;
    }

    /*noinspection CssUnusedSymbol*/
    .surface.zoom.grown > .content {
        opacity: 1;
        transform: translate(-50%, -50%) scale(1);
        transition:
            transform var(--open-ms) cubic-bezier(0.22, 0.7, 0.45, 1),
            opacity calc(var(--open-ms) * 0.4) ease calc(var(--open-ms) * 0.45);
    }

    /*noinspection CssUnusedSymbol*/
    .surface.press:has(:global(button:active)) {
        transform: scale(1.04);
    }

    .seed {
        position: absolute;
        inset: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        opacity: 1;
        transition: opacity 130ms ease 90ms;
    }

    /*noinspection CssUnusedSymbol*/
    .grown > .seed {
        opacity: 0;
        transition: opacity 90ms ease;
    }

    .content {
        position: absolute;
        width: max-content;
        opacity: 0;
        filter: blur(7px);
        transition:
            opacity calc(var(--close-ms) * 0.6) ease,
            filter calc(var(--close-ms) * 0.8) ease;
    }

    /*noinspection CssUnusedSymbol*/
    .grown > .content {
        opacity: 1;
        filter: none;
        transition:
            opacity calc(var(--open-ms) * 0.45) ease calc(var(--open-ms) * 0.1),
            filter calc(var(--open-ms) * 0.55) ease calc(var(--open-ms) * 0.1);
    }

    /*noinspection CssUnusedSymbol*/
    .content.center {
        left: 50%;
        top: 50%;
        transform: translate(-50%, -50%);
    }

    /*noinspection CssUnusedSymbol*/
    .content.top-start {
        top: 0;
        left: 0;
    }

    /*noinspection CssUnusedSymbol*/
    .content.top-end {
        top: 0;
        right: 0;
    }

    /*noinspection CssUnusedSymbol*/
    .content.bottom-start {
        bottom: 0;
        left: 0;
    }

    /*noinspection CssUnusedSymbol*/
    .content.bottom-end {
        bottom: 0;
        right: 0;
    }

    @media (prefers-reduced-motion: reduce) {
        .surface,
        .surface.grown,
        .surface.zoom,
        .surface.zoom.grown,
        .content,
        .grown > .content,
        .scrim,
        .scrim.lit {
            transition: none;
        }
    }
</style>
