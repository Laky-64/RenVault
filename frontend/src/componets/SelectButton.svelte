<script lang="ts">
    import {m} from "../paraglide/messages";
    import NavButton from "./NavButton.svelte";
    import {CHECK} from "../lib/icons";

    const {
        shown = true,
        label,
        active = false,
        disabled = false,
        onclick,
    } : {
        shown?: boolean;
        label: string;
        active?: boolean;
        disabled?: boolean;
        onclick: () => void;
    } = $props();

</script>

<NavButton
    {shown}
    align="end"
    label={active ? undefined : label}
    accent={active ? 'var(--button-color)' : ''}
    morph={`${label}:${active}`}
    flash={active}
    {disabled}
    {onclick}
>
    <svg viewBox="0 -960 960 960" width="20" height="20" role="img" aria-label={m.list_selectDone()}>
        <path d={CHECK} fill="var(--button-text-color)"/>
    </svg>
</NavButton>
