<script lang="ts">
    import {m} from "../paraglide/messages";
    import NavButton from "./NavButton.svelte";

    const {
        shown = true,
        label,
        active = false,
        onclick,
    } : {
        shown?: boolean;
        label: string;
        active?: boolean;
        onclick: () => void;
    } = $props();

    const CHECK = 'm382-354 339-339q12-12 28-12t28 12q12 12 12 28.5T777-636L410-268q-12 12-28 12t-28-12L182-440q-12-12-11.5-28.5T183-497q12-12 28.5-12t28.5 12l142 143Z';
</script>

<NavButton
    {shown}
    align="end"
    label={active ? undefined : label}
    accent={active ? 'var(--button-color)' : ''}
    morph={`${label}:${active}`}
    flash={active}
    {onclick}
>
    <svg viewBox="0 -960 960 960" width="20" height="20" role="img" aria-label={m.list_selectDone()}>
        <path d={CHECK} fill="var(--button-text-color)"/>
    </svg>
</NavButton>
