export const COMPACT_MAX = 950;
const PARALLAX = 0.3;

let width = $state(typeof window === 'undefined' ? Number.POSITIVE_INFINITY : window.innerWidth);

if (typeof window !== 'undefined') {
    window.addEventListener('resize', () => (width = window.innerWidth));
}

export function isCompact(max: number = COMPACT_MAX): boolean {
    return width <= max;
}

export interface NavigationOptions {
    compactMax?: number;
    parallax?: number;
    stacked?: boolean;
}

export class Navigation<Level> {
    levels = $state<Level[]>([]);
    private readonly parallax: number = PARALLAX;
    private readonly compactMax: number = COMPACT_MAX;
    private readonly stacked: boolean = false;

    constructor(options: NavigationOptions = {}) {
        this.parallax = options.parallax ?? PARALLAX;
        this.compactMax = options.compactMax ?? COMPACT_MAX;
        this.stacked = options.stacked ?? false;
    }

    compact = $derived(this.stacked || isCompact(this.compactMax));

    depth = $derived(this.levels.length);

    // noinspection JSUnusedGlobalSymbols
    top = $derived<Level | undefined>(this.levels.at(-1));

    // noinspection JSUnusedGlobalSymbols
    canGoBack = $derived(this.levels.length > 0);

    at(index: number): Level | undefined {
        return this.levels[index];
    }

    push(level: Level) {
        this.levels.push(level);
    }

    back() {
        this.levels.pop();
    }

    truncate(length: number) {
        if (length < this.levels.length) this.levels.length = Math.max(0, length);
    }

    replace(index: number, level: Level) {
        this.truncate(index);
        this.levels[index] = level;
    }

    // noinspection JSUnusedGlobalSymbols
    reset() {
        this.levels = [];
    }

    offsetOf(index: number): number {
        if (!this.compact) return 0;
        const delta = index - this.depth;
        return delta >= 0 ? delta * 100 : delta * 100 * this.parallax;
    }

    isActive(index: number): boolean {
        return !this.compact || index === this.depth;
    }
}

