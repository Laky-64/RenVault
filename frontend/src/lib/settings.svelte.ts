let open = $state(false);

export function settingsOpen(): boolean {
    return open;
}

export function keepSettingsOpen(value: boolean) {
    open = value;
}
