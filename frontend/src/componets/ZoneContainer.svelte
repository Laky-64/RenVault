<script lang="ts">
import ZoneButton from "./ZoneButton.svelte";
import type {Zone} from "./ZoneContainer";

const {
    zones,
    on_selected
} : {
    zones: Zone[];
    on_selected?: (zone: Zone) => void;
} = $props();

let selected = $state(0);
</script>

<div class="container">
    {#each zones as zone, i}
        <ZoneButton icon={zone.icon} text={zone.text} count={zone.passwords.length} accent="var(--tile-{zone.color}-color)" selected={selected === i} onclick={() => {
            selected = i;
            if (on_selected) on_selected(zone);
        }}/>
    {/each}
</div>
<style>
    /*noinspection CssNonIntegerLengthInPixels*/
    .container {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        grid-auto-rows: max-content;
        align-items: start;
        padding: 13px;
        gap: 8px;
        background: var(--section-bg-color);
        height: calc(100% - 8px);
        flex: 1.0;
        max-width: 250px;
        border-radius: var(--section-border-radius);
        border: 1.5px oklch(from var(--subtitle-text-color) l c h / 40%) solid;
        overflow: hidden;
        transform: scale(1);
        transition: transform 250ms ease;
        margin-bottom: 8px;
    }

    .container:has(:global(*:active)) {
        transform: scale(1.02);
    }
</style>