import {m} from "../paraglide/messages";

export interface Failure {
    code: number | null;
    message: string;
    raw: string;
}

const REJECTED = /\bec=(-?\d+)\s+em=("(?:[^"\\]|\\.)*")/;
const KNOWN: Record<number, () => string> = {
    [-20101]: m.failure_badCredentials,
    [-20751]: m.failure_accountLocked,
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
    if (!found) return {code: null, message: m.failure_generic(), raw};

    const code = Number(found[1]);
    const known = KNOWN[code];
    return {code, message: known?.() || unquote(found[2]).trim() || m.failure_generic(), raw};
}
