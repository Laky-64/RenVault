import {Navigation} from "./lib/navigation.svelte";
import type {Zone} from "./componets/ZoneContainer";
import type {Item} from "./lib/items";

export type Level =
    | {kind: 'zone'; zone: Zone}
    | {kind: 'item'; item: Item};

export const ZONE = 0;
export const ITEM = 1;

export const nav = new Navigation<Level>();

function levelAt<K extends Level['kind']>(index: number, kind: K) {
    const level = nav.at(index);
    return level?.kind === kind ? (level as Extract<Level, {kind: K}>) : undefined;
}

export function currentZone() {
    return levelAt(ZONE, 'zone')?.zone;
}

export function currentItem() {
    return levelAt(ITEM, 'item')?.item;
}

export function openZone(zone: Zone) {
    nav.replace(ZONE, {kind: 'zone', zone});
}

export function openItem(item: Item) {
    nav.replace(ITEM, {kind: 'item', item});
}