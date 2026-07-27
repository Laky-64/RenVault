import {m} from "../paraglide/messages";

export interface Failure {
    code: number | null;
    status: number | null;
    message: string;
    raw: string;
}

const REJECTED = /\bec=(-?\d+)\s+em=("(?:[^"\\]|\\.)*")/;
const KNOWN: Record<number, () => string> = {
    [-20101]: m.failure_badCredentials,
    [-20751]: m.failure_accountLocked,
};

const BAD_MASTER = /message authentication failed/i;
const ESCROW = /\bescrow:.*?\b(?:http status code|status) (\d{3})\b/;
const ESCROW_KNOWN: Record<number, () => string> = {
    409: m.failure_wrongPasscode,
};

function unquote(quoted: string): string {
    try {
        return JSON.parse(quoted) as string;
    } catch {
        return quoted.slice(1, -1).replace(/\\(.)/g, (_, char: string) => char);
    }
}

function textOf(cause: unknown): string {
    if (typeof cause === 'string') return cause;
    if (cause instanceof Error) return cause.message;
    return String(cause ?? '');
}

export function describeFailure(cause: unknown): Failure {
    const raw = textOf(cause);

    const found = REJECTED.exec(raw);
    if (found) {
        const code = Number(found[1]);
        const known = KNOWN[code];
        return {
            code,
            status: null,
            message: known?.() || unquote(found[2]).trim() || m.failure_generic(),
            raw,
        };
    }

    const escrow = ESCROW.exec(raw);
    if (escrow) {
        const status = Number(escrow[1]);
        return {code: null, status, message: (ESCROW_KNOWN[status] ?? m.failure_generic)(), raw};
    }

    if (BAD_MASTER.test(raw)) {
        return {code: null, status: null, message: m.failure_wrongMasterPassword(), raw};
    }

    return {code: null, status: null, message: m.failure_generic(), raw};
}
