import {bareHost, domainOf, isDomain, labelOf, type WebItem} from "./items";

export interface Draft {
    title: string;
    titleHint: string;
    username: string;
    password: string;
    websites: string[];
    note: string;
    totpKey: string | null;
    totpRemoved: boolean;
}

export function draftOf(item: WebItem): Draft {
    const hosts = item.website ? [item.domain, ...(item.domains ?? [])].filter(Boolean) : [];
    return {
        title: '',
        titleHint: labelOf(item),
        username: item.username,
        password: '',
        websites: [...new Set(hosts)],
        note: item.note,
        totpKey: null,
        totpRemoved: false,
    };
}

function filled(websites: string[]): string[] {
    return websites.filter(entry => entry.trim().length > 0);
}

export function websitesOk(websites: string[]): boolean {
    return filled(websites).every(entry => isDomain(bareHost(entry)));
}

export function tidyWebsites(websites: string[]): string[] {
    const hosts = filled(websites).map(domainOf).filter(Boolean);
    return [...new Set(hosts)];
}

export function titleOf(draft: Draft): string {
    return draft.title.trim() || draft.titleHint;
}

export function canSave(draft: Draft): boolean {
    if (!websitesOk(draft.websites)) return false;
    if (tidyWebsites(draft.websites).length === 0 && titleOf(draft).length === 0) return false;
    return draft.password.length > 0 || draft.username.trim().length > 0;
}
