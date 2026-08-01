<script lang="ts">
import ZoneButton from "./ZoneButton.svelte";
import ElasticScroll from "./ElasticScroll.svelte";
import {type Zone, zoneText} from "./ZoneContainer";
import {isCompact} from "../lib/navigation.svelte";
import {appName} from "../lib/app.svelte";

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
let contentHeight = $state(0);
</script>

<div class="frame" class:stack>
    <ElasticScroll {contentHeight}>
        <div class="content" bind:clientHeight={contentHeight}>
            {#if stack}
                <div class="large-title">
                    <h1>{appName()}</h1>
                </div>
            {/if}
            <div class="grid">
            {#each zones as zone}
                <ZoneButton icon={zone.icon} text={zoneText(zone).name} count={zone.items.length} accent="var(--tile-{zone.color}-color)" selected={zone.kind === selected?.kind && !stack} onclick={() => on_selected?.(zone)}/>
            {/each}
            </div>
        </div>
    </ElasticScroll>
</div>
<style>
    .frame {
        flex: 1.0;
        height: calc(100% - 16px);
        margin-block: 8px;
        overflow: hidden;
        transform: scale(1);
        transition: transform 250ms ease;
    }

    /*noinspection CssNonIntegerLengthInPixels*/
    .frame:not(.stack) {
        position: relative;
        z-index: 6;
        max-width: var(--zones-max, 250px);
        margin-left: 8px;
        border-radius: var(--section-border-radius);
        border: 1.5px oklch(from var(--subtitle-text-color) l c h / 40%) solid;
        background: var(--section-bg-color);
    }

    .frame:not(.stack):has(:global(*:active)) {
        transform: scale(1.02);
    }

    .large-title {
        padding: 15px 13px 0;
    }

    .large-title > h1 {
        font-size: 25px;
        font-weight: bold;
        color: var(--text-color);
        margin: 0;
    }

    .grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        grid-auto-rows: max-content;
        align-items: start;
        padding: 13px;
        gap: 8px;
    }
</style>
