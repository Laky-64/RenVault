import {m} from "../paraglide/messages";
import {formatDate} from "./datetime";
import type {WebMeta, WiFiMeta} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";

// noinspection JSUnusedGlobalSymbols
export type ItemKind = 'web' | 'wifi';

export interface WebItem extends WebMeta {
    kind: 'web';
}

export interface WiFiItem extends WiFiMeta {
    kind: 'wifi';
}

export type Item = WebItem | WiFiItem;

export function webItem(meta: WebMeta): WebItem {
    return {...meta, kind: 'web'};
}

export function wifiItem(meta: WiFiMeta): WiFiItem {
    return {...meta, kind: 'wifi'};
}

export type ItemIcon =
    | {source: 'favicon'; domain: string; fallback: string}
    | {source: 'glyph'; name: 'wifi'};

export interface ItemView {
    title: string;
    subtitle: string;
    icon: ItemIcon;
    hasTotp: boolean;
}

export function viewOf(item: Item): ItemView {
    switch (item.kind) {
        case 'web':
            return {
                title: item.title || item.domain,
                subtitle: item.username,
                icon: {source: 'favicon', domain: item.domain, fallback: item.title || item.domain},
                hasTotp: item.hasTotp,
            };
        case 'wifi':
            return {
                title: item.ssid,
                subtitle: m.item_wifiNetwork(),
                icon: {source: 'glyph', name: 'wifi'},
                hasTotp: false,
            };
    }
}

export type FieldValue =
    | {shown: string}
    | {secret: () => Promise<string>}
    | {code: () => Promise<string>};

export interface DetailField {
    label: string;
    value: FieldValue;
    copyable?: boolean;
}

export interface ItemDetail {
    title: string;
    fields: DetailField[];
}

export interface SecretSource {
    password: (id: string) => Promise<string>;
    totp: (id: string) => Promise<string>;
}

export function secretOf(field: DetailField): (() => Promise<string>) | null {
    return 'secret' in field.value ? field.value.secret : null;
}

export function plainOf(field: DetailField): string | undefined {
    return 'shown' in field.value ? field.value.shown : undefined;
}

export function codeOf(field: DetailField): (() => Promise<string>) | null {
    return 'code' in field.value ? field.value.code : null;
}

function websiteOf(item: WebItem): string {
    const others = (item.domains ?? []).filter(d => d && d !== item.domain);
    if (others.length === 0) return item.domain;
    return m.field_websiteMore({domain: item.domain, count: others.length});
}

function pushModified(fields: DetailField[], modified: string): void {
    const shown = formatDate(modified);
    if (shown) fields.push({label: m.field_modified(), value: {shown}});
}

export function detailOf(item: Item, secrets: SecretSource): ItemDetail {
    switch (item.kind) {
        case 'web': {
            const fields: DetailField[] = [
                {label: m.field_username(), value: {shown: item.username}, copyable: true},
                {label: m.field_password(), value: {secret: () => secrets.password(item.id)}, copyable: true},
            ];
            if (item.website) {
                fields.push({label: m.field_website(), value: {shown: websiteOf(item)}});
            }
            if (item.hasTotp) {
                fields.push({
                    label: m.field_verificationCode(),
                    value: {code: () => secrets.totp(item.id)},
                    copyable: true,
                });
            }
            pushModified(fields, item.modified);
            return {title: item.title || item.domain, fields};
        }
        case 'wifi': {
            const fields: DetailField[] = [
                {label: m.field_networkName(), value: {shown: item.ssid}, copyable: true},
                {label: m.field_password(), value: {secret: () => secrets.password(item.id)}, copyable: true},
            ];
            pushModified(fields, item.modified);
            return {title: item.ssid, fields};
        }
    }
}
