export interface MenuItem {
    id: string;
    label: string;
    icon?: string;
    glyph?: string;
    checked?: boolean;
}

export type MenuSection = MenuItem[];

export type MenuPlacement = 'top-start' | 'top-end' | 'bottom-start' | 'bottom-end';
