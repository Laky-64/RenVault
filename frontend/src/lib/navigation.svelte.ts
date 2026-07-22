export const NARROW_MAX = 800;
const PARALLAX = 0.3;

export interface NavigationOptions {
    narrowMax?: number;
    parallax?: number;
    stacked?: boolean;
}

export class Navigation<Level> {
    levels = $state<Level[]>([]);
    narrow = $state(false);

    private readonly parallax: number;

    constructor(options: NavigationOptions = {}) {
        this.parallax = options.parallax ?? PARALLAX;
        if (options.stacked) {
            this.narrow = true;
            return;
        }
        if (typeof window === 'undefined') return;
        const query = window.matchMedia(`(max-width: ${options.narrowMax ?? NARROW_MAX}px)`);
        this.narrow = query.matches;
        query.addEventListener('change', (event) => (this.narrow = event.matches));
    }

    depth = $derived(this.levels.length);

    top = $derived<Level | undefined>(this.levels.at(-1));

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

    reset() {
        this.levels = [];
    }

    offsetOf(index: number): number {
        if (!this.narrow) return 0;
        const delta = index - this.depth;
        return delta >= 0 ? delta * 100 : delta * 100 * this.parallax;
    }

    isActive(index: number): boolean {
        return !this.narrow || index === this.depth;
    }
}

