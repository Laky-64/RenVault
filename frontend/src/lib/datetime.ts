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

export function formatDate(value: string | Date | null | undefined, style: DateStyle = 'medium'): string {
    if (!value) return '';
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime()) || date.getUTCFullYear() <= 1) return '';
    return formatter(style).format(date);
}
