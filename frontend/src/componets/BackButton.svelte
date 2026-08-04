<script lang="ts">
    import {m} from "../paraglide/messages";
    import NavButton from "./NavButton.svelte";
    import {CHEVRON, CLOSE} from "../lib/icons";

    const {
        shown = false,
        mode = 'back',
        onclick,
    } : {
        shown?: boolean;
        mode?: 'back' | 'select-all' | 'deselect-all' | 'cancel';
        onclick: () => void;
    } = $props();

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
