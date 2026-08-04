export function observeSize(node: HTMLElement, report: (node: HTMLElement) => void) {
    const send = () => report(node);
    send();
    const observer = typeof ResizeObserver !== 'undefined'
        ? new ResizeObserver(send)
        : undefined;
    observer?.observe(node);
    return {
        destroy() {
            observer?.disconnect();
        },
    };
}

export function portal(node: HTMLElement) {
    document.body.appendChild(node);
    return {
        destroy() {
            node.remove();
        },
    };
}

export function prefersReducedMotion(): boolean {
    return typeof window !== 'undefined'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
