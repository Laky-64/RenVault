import {cubicOut} from "svelte/easing";
import type {TransitionConfig} from "svelte/transition";
import {prefersReducedMotion} from "./dom";

export const POP_MS = 260;
export const CROSS_MS = 200;
export const ROW_MS = 190;

export function motionMs(ms: number): number {
    return prefersReducedMotion() ? 0 : ms;
}

export function foldRow(node: HTMLElement, {skip = false}: {skip?: boolean} = {}): TransitionConfig {
    const height = node.getBoundingClientRect().height;
    const style = getComputedStyle(node);
    const top = parseFloat(style.paddingTop) || 0;
    const bottom = parseFloat(style.paddingBottom) || 0;
    return {
        duration: skip ? 0 : motionMs(ROW_MS),
        easing: cubicOut,
        css: (t: number) => `
            height: ${t * height}px;
            padding-top: ${t * top}px;
            padding-bottom: ${t * bottom}px;
            opacity: ${t};
            overflow: hidden;
        `,
    };
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
