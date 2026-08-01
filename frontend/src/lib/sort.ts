import {type Item, viewOf} from "./items";

export type SortField = 'title' | 'website' | 'modified' | 'created';

function websiteOf(item: Item): string {
    switch (item.kind) {
        case 'web':
            return item.domain;
        case 'passkey':
            return item.relyingParty;
        case 'wifi':
            return item.ssid;
    }
}

function timeOf(value: string): number {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : 0;
}

function compare(a: Item, b: Item, field: SortField): number {
    switch (field) {
        case 'title':
            return viewOf(a).title.localeCompare(viewOf(b).title, undefined, {sensitivity: 'base'});
        case 'website':
            return websiteOf(a).localeCompare(websiteOf(b), undefined, {sensitivity: 'base'});
        case 'modified':
            return timeOf(a.modified) - timeOf(b.modified);
        case 'created':
            return timeOf(a.created) - timeOf(b.created);
    }
}

export function sortItems(items: Item[], field: SortField, ascending: boolean): Item[] {
    const sign = ascending ? 1 : -1;
    return [...items].sort((a, b) => sign * compare(a, b, field));
}
