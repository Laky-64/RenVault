<script lang="ts">
import ZoneButton from "./ZoneButton.svelte";
import ProfileAvatar from "./ProfileAvatar.svelte";
import SettingsSurface from "./SettingsSurface.svelte";
import Button from "./Button.svelte";
import {Service} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";
import {m} from "../paraglide/messages";
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
let settingsOpen = $state(false);
let settingsAnchor: HTMLElement | undefined = $state();
let hasPhoto = $state(false);

$effect(() => {
    Service.ProfileInfo()
        .then(info => (hasPhoto = info.hasPhoto))
        .catch(() => {});
});
</script>

<div class="frame" class:stack>
    <div class="scroll">
    <ElasticScroll {contentHeight}>
        <div class="content" bind:clientHeight={contentHeight}>
            {#if stack}
                <div class="large-title">
                    <h1>{appName()}</h1>
                    {@render profile()}
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

    {#if !stack}
        <div class="account">
            {@render profile()}
        </div>
    {/if}
</div>

<SettingsSurface bind:open={settingsOpen} anchor={settingsAnchor} {hasPhoto}/>

{#snippet profile()}
    <div class="avatar" bind:this={settingsAnchor}>
        <Button variant="plain" padding="4px" radius="999px"
                onclick={() => (settingsOpen = true)}>
            <ProfileAvatar size={stack ? 32 : 40} {hasPhoto} label={m.settings_openLabel()}/>
        </Button>
    </div>
{/snippet}
<style>
    .frame {
        display: flex;
        flex-direction: column;
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
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 15px 13px 0;
    }

    .large-title > h1 {
        flex: 1;
    }

    .scroll {
        flex: 1;
        min-height: 0;
    }

    .account {
        flex-shrink: 0;
        display: flex;
        align-items: center;
        padding: 8px 9px;
    }

    .avatar {
        display: flex;
        flex-shrink: 0;
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
