import type {Icon} from "./ZoneIcon";
import type {Item} from "../lib/items";
import {m} from "../paraglide/messages";

export type ZoneColor = 'blue' | 'green' | 'yellow' | 'teal' | 'red' | 'orange'
export type ZoneKind = 'all' | 'passkeys' | 'codes' | 'networks' | 'security' | 'deleted';

export interface Zone {
    kind: ZoneKind;
    icon: Icon;
    color: ZoneColor;
    items: Item[];
    label?: string;
}

export interface ZoneText {
    name: string;
    emptyTitle: string;
    emptyDescription: string;
}

const CATALOG: Record<ZoneKind, {
    name: () => string;
    empty: () => string;
    noSelection: () => string;
    description: () => string;
}> = {
    all: {
        name: m.zone_all_name,
        empty: m.zone_all_empty,
        noSelection: m.zone_all_noSelection,
        description: m.zone_all_desc,
    },
    passkeys: {
        name: m.zone_passkeys_name,
        empty: m.zone_passkeys_empty,
        noSelection: m.zone_passkeys_noSelection,
        description: m.zone_passkeys_desc,
    },
    codes: {
        name: m.zone_codes_name,
        empty: m.zone_codes_empty,
        noSelection: m.zone_codes_noSelection,
        description: m.zone_codes_desc,
    },
    networks: {
        name: m.zone_networks_name,
        empty: m.zone_networks_empty,
        noSelection: m.zone_networks_noSelection,
        description: m.zone_networks_desc,
    },
    security: {
        name: m.zone_security_name,
        empty: m.zone_security_empty,
        noSelection: m.zone_security_noSelection,
        description: m.zone_security_desc,
    },
    deleted: {
        name: m.zone_deleted_name,
        empty: m.zone_deleted_empty,
        noSelection: m.zone_deleted_noSelection,
        description: m.zone_deleted_desc,
    },
};

export function zoneText(zone: Zone): ZoneText {
    const entry = CATALOG[zone.kind];
    return {
        name: zone.label ?? entry.name(),
        emptyTitle: zone.items.length === 0 ? entry.empty() : entry.noSelection(),
        emptyDescription: entry.description(),
    };
}
