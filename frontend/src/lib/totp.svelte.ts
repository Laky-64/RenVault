export const TOTP_PERIOD = 30;

const GUARD_MS = 25;

let second = $state(Math.floor(Date.now() / 1000));

function schedule(): void {
    const now = Date.now();
    setTimeout(() => {
        second = Math.floor(Date.now() / 1000);
        schedule();
    }, 1000 - (now % 1000) + GUARD_MS);
}

if (typeof window !== 'undefined') schedule();

export function totpSlot(period: number = TOTP_PERIOD): number {
    return Math.floor(second / period);
}
