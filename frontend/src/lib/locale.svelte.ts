import {getLocale, locales, setLocale, type Locale} from "../paraglide/runtime";

let current: Locale = $state(getLocale());

export function locale(): Locale {
    return current;
}

export function localeChoices(): readonly Locale[] {
    return locales;
}

export function chooseLocale(next: Locale) {
    if (next === current) return;
    setLocale(next, {reload: false});
    current = next;
}

export function localeName(value: Locale): string {
    const names = new Intl.DisplayNames([value], {type: 'language'});
    const name = names.of(value) ?? value;
    return name.charAt(0).toLocaleUpperCase(value) + name.slice(1);
}
