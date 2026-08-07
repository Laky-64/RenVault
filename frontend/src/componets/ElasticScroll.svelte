<script lang="ts">
    import type {Snippet} from "svelte";
    import {onDestroy, untrack} from "svelte";

    let {
        contentHeight,
        scrollOffset = $bindable(0),
        shownOffset = $bindable(0),
        viewportHeight = $bindable(0),
        children,
    }: {
        contentHeight: number;
        scrollOffset?: number;
        shownOffset?: number;
        viewportHeight?: number;
        children: Snippet;
    } = $props();

    const RUBBER_BAND_COEFFICIENT = 0.55;
    const SNAP_IDLE_MS = 120;
    const FRICTION = 0.0025;

    let viewportEl: HTMLDivElement | undefined = $state();

    let rawOffset = $state(0);
    let dragging = $state(false);

    let dragStartY = 0;
    let dragStartOffset = 0;
    let lastMoveY = 0;
    let lastMoveT = 0;
    let velocity = 0;

    let rafId: number | null = null;
    let idleTimer: ReturnType<typeof setTimeout> | null = null;

    const maxScroll = $derived(Math.max(0, contentHeight - viewportHeight));

    function rubberBand(overscroll: number, dimension: number): number {
        if (dimension <= 0 || overscroll <= 0) return 0;
        return (overscroll * RUBBER_BAND_COEFFICIENT * dimension) / (dimension + RUBBER_BAND_COEFFICIENT * overscroll);
    }

    function clampRaw(value: number): number {
        const limit = viewportHeight * 1.5;
        return Math.min(Math.max(value, -limit), maxScroll + limit);
    }

    const displayOffset = $derived.by(() => {
        if (rawOffset < 0) return -rubberBand(-rawOffset, viewportHeight);
        if (rawOffset > maxScroll) return maxScroll + rubberBand(rawOffset - maxScroll, viewportHeight);
        return rawOffset;
    });

    const clampedOffset = $derived(Math.min(Math.max(rawOffset, 0), maxScroll));

    $effect(() => {
        scrollOffset = clampedOffset;
    });

    $effect(() => {
        shownOffset = displayOffset;
    });

    let previousMaxScroll: number | undefined;
    $effect(() => {
        const limit = maxScroll;
        if (previousMaxScroll === limit) return;
        previousMaxScroll = limit;
        if (dragging) return;
        untrack(() => {
            const clamped = Math.min(Math.max(rawOffset, 0), limit);
            if (clamped === rawOffset) return;
            cancelMomentum();
            if (idleTimer) clearTimeout(idleTimer);
            rawOffset = clamped;
        });
    });

    function cancelMomentum() {
        if (rafId !== null) {
            cancelAnimationFrame(rafId);
            rafId = null;
        }
    }

    function snapBack() {
        const target = rawOffset < 0 ? 0 : rawOffset > maxScroll ? maxScroll : null;
        if (target === null) return;
        cancelMomentum();
        const step = () => {
            rawOffset += (target - rawOffset) * 0.22;
            if (Math.abs(target - rawOffset) < 0.5) {
                rawOffset = target;
                rafId = null;
                return;
            }
            rafId = requestAnimationFrame(step);
        };
        rafId = requestAnimationFrame(step);
    }

    function startMomentum(initialVelocity: number) {
        let v = initialVelocity;
        let last = performance.now();
        const step = () => {
            const now = performance.now();
            const dt = now - last;
            last = now;
            rawOffset = clampRaw(rawOffset + v * dt);
            if (rawOffset < 0 || rawOffset > maxScroll) v *= 0.85;
            v *= Math.exp(-FRICTION * dt);
            if (Math.abs(v) < 0.02) {
                rafId = null;
                snapBack();
                return;
            }
            rafId = requestAnimationFrame(step);
        };
        rafId = requestAnimationFrame(step);
    }

    function normalizedDeltaY(e: WheelEvent): number {
        if (e.deltaMode === 1) return e.deltaY * 16;
        if (e.deltaMode === 2) return e.deltaY * viewportHeight;
        return e.deltaY;
    }

    function onWheel(e: WheelEvent) {
        e.preventDefault();
        cancelMomentum();
        rawOffset = clampRaw(rawOffset + normalizedDeltaY(e));
        if (idleTimer) clearTimeout(idleTimer);
        idleTimer = setTimeout(snapBack, SNAP_IDLE_MS);
    }

    let activePointerId: number | null = null;
    let pendingPointerId: number | null = null;
    const DRAG_THRESHOLD = 3;

    function onPointerDown(e: PointerEvent) {
        pendingPointerId = e.pointerId;
        cancelMomentum();
        if (idleTimer) clearTimeout(idleTimer);
        dragStartY = lastMoveY = e.clientY;
        dragStartOffset = rawOffset;
        lastMoveT = performance.now();
        velocity = 0;
    }

    const MAX_VELOCITY = 3;
    function onPointerMove(e: PointerEvent) {
        if (!dragging && pendingPointerId === e.pointerId) {
            if (Math.abs(e.clientY - dragStartY) < DRAG_THRESHOLD) return;
            dragging = true;
            activePointerId = e.pointerId;
            viewportEl?.setPointerCapture(e.pointerId);
        }
        if (!dragging) return;
        const now = performance.now();
        const dt = now - lastMoveT;
        if (dt > 0) velocity = Math.max(-MAX_VELOCITY, Math.min(MAX_VELOCITY, (e.clientY - lastMoveY) / dt));
        lastMoveY = e.clientY;
        lastMoveT = now;
        rawOffset = clampRaw(dragStartOffset + (dragStartY - e.clientY));
    }

    function endDrag() {
        pendingPointerId = null;
        if (!dragging) return;
        dragging = false;
        if (activePointerId !== null) {
            try {
                viewportEl?.releasePointerCapture(activePointerId);
            } catch {}
        }
        activePointerId = null;
        startMomentum(-velocity);
    }

    function onPointerUp() {
        endDrag();
    }

    function onPointerLeave() {
        endDrag();
    }

    onDestroy(() => {
        cancelMomentum();
        if (idleTimer) clearTimeout(idleTimer);
    });

    $effect(() => {
        const onBlur = () => endDrag();
        window.addEventListener("blur", onBlur);
        return () => window.removeEventListener("blur", onBlur);
    });

    // noinspection JSUnusedGlobalSymbols
    export function nudge(delta: number) {
        if (delta === 0) return;
        rawOffset += delta;
    }

    // noinspection JSUnusedGlobalSymbols
    export function reset(to = 0) {
        cancelMomentum();
        if (idleTimer) clearTimeout(idleTimer);
        dragging = false;
        pendingPointerId = null;
        activePointerId = null;
        velocity = 0;
        rawOffset = Math.max(0, to);
    }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
    class="viewport"
    bind:this={viewportEl}
    bind:clientHeight={viewportHeight}
    onwheel={onWheel}
    onpointerdown={onPointerDown}
    onpointermove={onPointerMove}
    onpointerup={onPointerUp}
    onpointercancel={onPointerUp}
    onpointerleave={onPointerLeave}
>
    <div class="content" style="height: {contentHeight}px; transform: translateY({-displayOffset}px)">
        {@render children()}
    </div>
</div>

<style>
    .viewport {
        position: relative;
        height: 100%;
        overflow: hidden;
        touch-action: none;
    }

    .content {
        position: relative;
        width: 100%;
        will-change: transform;
    }
</style>
