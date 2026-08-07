import {getLocale} from "../paraglide/runtime";
export type DateStyle = 'short' | 'medium' | 'long';
const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(style: DateStyle): Intl.DateTimeFormat {
    const locale = getLocale();
    const key = `${locale}:${style}`;
    let found = formatters.get(key);
    if (!found) {
        found = new Intl.DateTimeFormat(locale, {dateStyle: style});
        formatters.set(key, found);
    }
    return found;
}

const DAY_MS = 86_400_000;

export function daysLeft(value: string | Date | null | undefined, span: number): number {
    if (!value) return 0;
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) return 0;
    const gone = Math.floor((Date.now() - date.getTime()) / DAY_MS);
    return Math.max(0, span - gone);
}

export function formatDate(value: string | Date | null | undefined, style: DateStyle = 'medium'): string {
    if (!value) return '';
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime()) || date.getUTCFullYear() <= 1) return '';
    return formatter(style).format(date);
}

const relatives = new Map<string, Intl.RelativeTimeFormat>();

function relative(): Intl.RelativeTimeFormat {
    const locale = getLocale();
    let found = relatives.get(locale);
    if (!found) {
        found = new Intl.RelativeTimeFormat(locale, {numeric: 'auto'});
        relatives.set(locale, found);
    }
    return found;
}

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const MONTH_MS = 30 * DAY_MS;
const YEAR_MS = 365 * DAY_MS;
const JUST_NOW_MS = 45_000;

export function formatRelative(value: string | Date | null | undefined, now = Date.now()): string {
    if (!value) return '';
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime()) || date.getUTCFullYear() <= 1) return '';

    const gone = Math.max(0, now - date.getTime());
    const format = relative();
    if (gone < JUST_NOW_MS) return format.format(0, 'second');
    if (gone < HOUR_MS) return format.format(-Math.floor(gone / MINUTE_MS), 'minute');
    if (gone < DAY_MS) return format.format(-Math.floor(gone / HOUR_MS), 'hour');
    if (gone < MONTH_MS) return format.format(-Math.floor(gone / DAY_MS), 'day');
    if (gone < YEAR_MS) return format.format(-Math.floor(gone / MONTH_MS), 'month');
    return format.format(-Math.floor(gone / YEAR_MS), 'year');
}
