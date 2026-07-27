export const TOTP_PERIOD = 30;

let slot = $state(Math.floor(Date.now() / 1000 / TOTP_PERIOD));

if (typeof window !== 'undefined') {
    setInterval(() => {
        const current = Math.floor(Date.now() / 1000 / TOTP_PERIOD);
        if (current !== slot) slot = current;
    }, 1000);
}

export function totpSlot(): number {
    return slot;
}
