import type {Icon} from "./ZoneIcon";

export type ZoneColor = 'blue' | 'green' | 'yellow' | 'teal' | 'red' | 'orange'

export interface Zone {
    text: string;
    icon: Icon;
    color: ZoneColor;
    passwords: Password[];
}

export interface Password {
    name: string;
    email: string;
    password: string;
    domains: string[];
    icon: string;
    totp?: number;
}