import type {MenuPlacement} from "./menu";

export interface Size {
    width: number;
    height: number;
}

export interface Box {
    x: {side: 'left' | 'right'; value: number};
    y: {side: 'top' | 'bottom'; value: number};
    width: number;
    height: number;
}

export type Placer = (natural: Size, anchor: DOMRect) => Box;

export type ContentAnchor = 'center' | 'top-start' | 'top-end' | 'bottom-start' | 'bottom-end';

export function seedBox(anchor: DOMRect, target: Box): Box {
    return {
        x: {
            side: target.x.side,
            value: target.x.side === 'left' ? anchor.left : window.innerWidth - anchor.right,
        },
        y: {
            side: target.y.side,
            value: target.y.side === 'top' ? anchor.top : window.innerHeight - anchor.bottom,
        },
        width: anchor.width,
        height: anchor.height,
    };
}

export function nearAnchor(placement: MenuPlacement): Placer {
    const above = placement.startsWith('top');
    const fromEnd = placement.endsWith('end');
    return (natural, anchor) => ({
        x: fromEnd
            ? {side: 'right', value: window.innerWidth - anchor.right}
            : {side: 'left', value: anchor.left},
        y: above
            ? {side: 'bottom', value: window.innerHeight - anchor.bottom}
            : {side: 'top', value: anchor.top},
        width: natural.width,
        height: natural.height,
    });
}

export function centred(margin = 24): Placer {
    return (natural) => {
        const width = Math.min(natural.width, window.innerWidth - margin * 2);
        const height = Math.min(natural.height, window.innerHeight - margin * 2);
        return {
            x: {side: 'left', value: Math.round((window.innerWidth - width) / 2)},
            y: {side: 'top', value: Math.round((window.innerHeight - height) / 2)},
            width,
            height,
        };
    };
}

export function sheet(gap: number): Placer {
    return () => ({
        x: {side: 'left', value: 0},
        y: {side: 'bottom', value: 0},
        width: window.innerWidth,
        height: Math.max(0, window.innerHeight - gap),
    });
}
