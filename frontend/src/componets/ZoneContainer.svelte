<script lang="ts">
import ZoneButton from "./ZoneButton.svelte";
import {type Zone, zoneText} from "./ZoneContainer";
import {isCompact} from "../lib/navigation.svelte";

const {
    zones,
    selected,
    on_selected
} : {
    zones: Zone[];
    selected?: Zone | null;
    on_selected?: (zone: Zone) => void;
} = $props();
const stack = $derived(isCompact());
</script>

<div class="container" class:stack>
    {#each zones as zone}
        <ZoneButton icon={zone.icon} text={zoneText(zone).name} count={zone.items.length} accent="var(--tile-{zone.color}-color)" selected={zone.kind === selected?.kind && !stack} onclick={() => on_selected?.(zone)}/>
    {/each}
</div>
<style>
    .container {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        grid-auto-rows: max-content;
        align-items: start;
        padding: 13px;
        gap: 8px;
        height: calc(100% - 16px);
        flex: 1.0;
        overflow: hidden;
        transform: scale(1);
        transition: transform 250ms ease;
        margin-block: 8px;
    }

    /*noinspection CssNonIntegerLengthInPixels*/
    .container:not(.stack) {
        max-width: var(--zones-max, 250px);
        margin-left: 8px;
        border-radius: var(--section-border-radius);
        border: 1.5px oklch(from var(--subtitle-text-color) l c h / 40%) solid;
        background: var(--section-bg-color);
    }

    .container:not(.stack):has(:global(*:active)) {
        transform: scale(1.02);
    }
</style>