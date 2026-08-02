<script lang="ts">
    import {m} from "../paraglide/messages";
    import NavButton from "./NavButton.svelte";

    const {
        shown = false,
        mode = 'back',
        onclick,
    } : {
        shown?: boolean;
        mode?: 'back' | 'select-all' | 'deselect-all' | 'cancel';
        onclick: () => void;
    } = $props();

    const CHEVRON = 'm382-480 294 294q15 15 14.5 35T675-116q-15 15-35 15t-35-15L297-423q-12-12-18-27t-6-30q0-15 6-30t18-27l308-308q15-15 35.5-14.5T676-844q15 15 15 35t-15 35L382-480Z';
    const CLOSE = 'M480-424 284-228q-11 11-28 11t-28-11q-11-11-11-28t11-28l196-196-196-196q-11-11-11-28t11-28q11-11 28-11t28 11l196 196 196-196q11-11 28-11t28 11q11 11 11 28t-11 28L536-480l196 196q11 11 11 28t-11 28q-11 11-28 11t-28-11L480-424Z';

    const label = $derived.by(() => {
        if (mode === 'select-all') return m.list_selectAll();
        if (mode === 'deselect-all') return m.list_deselectAll();
        return undefined;
    });

    const glyph = $derived(mode === 'cancel' ? CLOSE : CHEVRON);
    const glyphLabel = $derived(mode === 'cancel' ? m.editor_cancel() : m.nav_back());
</script>

<NavButton
    {shown}
    {label}
    morph={mode}
    flash={mode}
    {onclick}
>
    <svg viewBox="0 -960 960 960" width="20" height="20" role="img" aria-label={glyphLabel}>
        <path d={glyph} fill="var(--text-color)"/>
    </svg>
</NavButton>
