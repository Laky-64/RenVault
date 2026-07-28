export const TOTP_PERIOD = 30;

const GUARD_MS = 25;

function slotAt(now: number, period: number): number {
    return Math.floor(now / 1000 / period);
}

class Ticker {
    current = $state(0);

    constructor(private readonly period: number) {
        this.current = slotAt(Date.now(), period);
        this.schedule();
    }

    private schedule(): void {
        const now = Date.now();
        const next = (slotAt(now, this.period) + 1) * this.period * 1000;
        setTimeout(() => {
            this.current = slotAt(Date.now(), this.period);
            this.schedule();
        }, next - now + GUARD_MS);
    }
}

const tickers = new Map<number, Ticker>();

export function totpSlot(period: number = TOTP_PERIOD): number {
    if (typeof window === 'undefined') return slotAt(Date.now(), period);
    let ticker = tickers.get(period);
    if (!ticker) {
        ticker = new Ticker(period);
        tickers.set(period, ticker);
    }
    return ticker.current;
}
