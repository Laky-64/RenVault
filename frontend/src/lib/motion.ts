import {cubicOut} from "svelte/easing";
import type {TransitionConfig} from "svelte/transition";
import {prefersReducedMotion} from "./dom";

export const POP_MS = 260;

export const CROSS_MS = 200;

export function motionMs(ms: number): number {
    return prefersReducedMotion() ? 0 : ms;
}

export type PopAlign = 'start' | 'end';

export function popIn(node: HTMLElement, {align = 'start'}: {align?: PopAlign} = {}): TransitionConfig {
    const width = node.getBoundingClientRect().width;
    return {
        duration: motionMs(POP_MS),
        easing: cubicOut,
        css: (t: number) => `
            width: ${t * width}px;
            opacity: ${t};
            transform: scale(${0.7 + 0.3 * t});
            transform-origin: ${align === 'end' ? t * width - width / 2 : width / 2}px 50%;
        `,
    };
}
