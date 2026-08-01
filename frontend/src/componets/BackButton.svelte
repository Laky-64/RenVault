<script lang="ts">
    import {m} from "../paraglide/messages";
    import NavButton from "./NavButton.svelte";

    const {
        shown = false,
        mode = 'back',
        onclick,
    } : {
        shown?: boolean;
        mode?: 'back' | 'select-all' | 'deselect-all';
        onclick: () => void;
    } = $props();

    const CHEVRON = 'm382-480 294 294q15 15 14.5 35T675-116q-15 15-35 15t-35-15L297-423q-12-12-18-27t-6-30q0-15 6-30t18-27l308-308q15-15 35.5-14.5T676-844q15 15 15 35t-15 35L382-480Z';

    const label = $derived.by(() => {
        if (mode === 'select-all') return m.list_selectAll();
        if (mode === 'deselect-all') return m.list_deselectAll();
        return undefined;
    });
</script>

<NavButton
    {shown}
    {label}
    morph={mode}
    flash={mode}
    {onclick}
>
    <svg viewBox="0 -960 960 960" width="20" height="20" role="img" aria-label={m.nav_back()}>
        <path d={CHEVRON} fill="var(--text-color)"/>
    </svg>
</NavButton>
