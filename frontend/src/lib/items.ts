import {m} from "../paraglide/messages";
import {formatDate} from "./datetime";
import type {PasskeyMeta, WebMeta, WiFiMeta} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";

// noinspection JSUnusedGlobalSymbols
export type ItemKind = 'web' | 'wifi' | 'passkey';

export interface WebItem extends WebMeta {
    kind: 'web';
    passkey?: PasskeyItem;
}

export interface WiFiItem extends WiFiMeta {
    kind: 'wifi';
}

export interface PasskeyItem extends PasskeyMeta {
    kind: 'passkey';
    linked?: WebItem;
}

export type Item = WebItem | WiFiItem | PasskeyItem;

export function webItem(meta: WebMeta): WebItem {
    return {...meta, kind: 'web'};
}

export function wifiItem(meta: WiFiMeta): WiFiItem {
    return {...meta, kind: 'wifi'};
}

export function passkeyItem(meta: PasskeyMeta): PasskeyItem {
    return {...meta, kind: 'passkey'};
}

function normalized(value: string): string {
    return value.trim().toLowerCase();
}

function hostOf(value: string): string {
    return normalized(value).replace(/^www\./, '');
}

const HOST = /^(?!-)[\p{L}\p{N}-]{1,63}(?<!-)(\.(?!-)[\p{L}\p{N}-]{1,63}(?<!-))*\.\p{L}{2,63}$/u;
const IPV4 = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/;

export function isDomain(value: string): boolean {
    if (value.length === 0 || value.length > 253) return false;
    const parts = IPV4.exec(value);
    if (parts) return parts.slice(1).every(n => Number(n) <= 255 && (n === '0' || !n.startsWith('0')));
    return HOST.test(value);
}

export function bareHost(value: string): string {
    return normalized(value)
        .replace(/^[a-z][a-z0-9+.-]*:\/*/, '')
        .replace(/^www\./, '')
        .replace(/\/+$/, '');
}

export function domainOf(value: string): string {
    let host = normalized(value)
        .replace(/^[a-z][a-z0-9+.-]*:\/\//, '')
        .replace(/^[^/@]*@/, '');
    host = host.split(/[/?#\\]/, 1)[0]
        .replace(/:\d+$/, '')
        .replace(/^\.+|\.+$/g, '');
    return hostOf(host);
}

function hostsOf(item: WebItem): string[] {
    return [item.domain, ...(item.domains ?? [])].map(hostOf).filter(Boolean);
}

function linkKey(host: string, username: string): string {
    return `${host}\n${normalized(username)}`;
}

export function linkItems(web: WebItem[], passkeys: PasskeyItem[]): {web: WebItem[]; passkeys: PasskeyItem[]} {
    const byHost = new Map<string, WebItem>();
    for (const entry of web) {
        if (entry.isDeleted) continue;
        for (const host of hostsOf(entry)) {
            const key = linkKey(host, entry.username);
            if (!byHost.has(key)) byHost.set(key, entry);
        }
    }

    const byWebId = new Map<string, PasskeyItem>();
    const linkedPasskeys = passkeys.map(passkey => {
        if (passkey.isDeleted) return passkey;
        let match = byHost.get(linkKey(hostOf(passkey.relyingParty), passkey.username));
        if (!match) return passkey;
        match.passkey = passkey;
        if (!byWebId.has(match.id)) byWebId.set(match.id, passkey);
        return {...passkey, linked: match};
    });

    const linkedWeb = web.map(entry => {
        const match = byWebId.get(entry.id);
        return match ? {...entry, passkey: match} : entry;
    });

    return {web: linkedWeb, passkeys: linkedPasskeys};
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
                title: item.passkey?.title || item.title || item.domain,
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
        case 'passkey':
            return {
                title: item.title,
                subtitle: item.username,
                icon: {source: 'favicon', domain: item.relyingParty, fallback: item.title},
                hasTotp: false,
            }
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

function pushPasskeyCreated(fields: DetailField[], created: string): void {
    const date = formatDate(created);
    if (date) fields.push({label: m.field_passkey(), value: {shown: m.field_passkeyCreationDate({date})}});
}

export function detailOf(item: Item, secrets: SecretSource): ItemDetail {
    switch (item.kind) {
        case 'web': {
            const fields: DetailField[] = [
                {label: m.field_username(), value: {shown: item.username}, copyable: true},
            ];
            if (item.hasPassword) {
                fields.push({label: m.field_password(), value: {secret: () => secrets.password(item.id)}, copyable: true});
            }
            if (item.passkey) {
                pushPasskeyCreated(fields, item.passkey.created);
            }
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
            return {title: item.passkey?.title || item.title || item.domain, fields};
        }
        case 'wifi': {
            const fields: DetailField[] = [
                {label: m.field_networkName(), value: {shown: item.ssid}, copyable: true},
                {label: m.field_password(), value: {secret: () => secrets.password(item.id)}, copyable: true},
            ];
            pushModified(fields, item.modified);
            return {title: item.ssid, fields};
        }
        case 'passkey': {
            const linked = item.linked;
            const fields: DetailField[] = [
                {label: m.field_username(), value: {shown: item.username}, copyable: true},
            ]
            if (linked) {
                fields.push({
                    label: m.field_password(),
                    value: {secret: () => secrets.password(linked.id)},
                    copyable: true,
                });
            }
            pushPasskeyCreated(fields, item.created);
            if (linked) {
                if (linked.website) {
                    fields.push({label: m.field_website(), value: {shown: websiteOf(linked)}});
                }
                if (linked.hasTotp) {
                    fields.push({
                        label: m.field_verificationCode(),
                        value: {code: () => secrets.totp(linked.id)},
                        copyable: true,
                    });
                }
                pushModified(fields, linked.modified);
            } else {
                pushModified(fields, item.modified);
            }
            return {title: item.title, fields};
        }
    }
}
