import {Navigation} from "./lib/navigation.svelte";
import type {Password, Zone} from "./componets/ZoneContainer";

export type Level =
    | {kind: 'zone'; zone: Zone}
    | {kind: 'password'; password: Password};

export const ZONE = 0;
export const PASSWORD = 1;

export const nav = new Navigation<Level>();

function levelAt<K extends Level['kind']>(index: number, kind: K) {
    const level = nav.at(index);
    return level?.kind === kind ? (level as Extract<Level, {kind: K}>) : undefined;
}

export function currentZone() {
    return levelAt(ZONE, 'zone')?.zone;
}

export function currentPassword() {
    return levelAt(PASSWORD, 'password')?.password;
}

export function openZone(zone: Zone) {
    nav.replace(ZONE, {kind: 'zone', zone});
}

export function openPassword(password: Password) {
    nav.replace(PASSWORD, {kind: 'password', password});
}