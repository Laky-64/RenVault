<script lang="ts">
    import {pathFor} from "./ZoneIcon";
    import {type Zone, zoneText} from "./ZoneContainer";
    import PasswordIcon from "./PasswordIcon.svelte";
    import PasswordInfoFields from "./PasswordInfoFields.svelte";
    import {detailOf, type Item, type SecretSource, viewOf} from "../lib/items";
    import ElasticScroll from "./ElasticScroll.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import DetailNote from "./DetailNote.svelte";
    import SecurityNotice from "./SecurityNotice.svelte";
    import {observeSize} from "../lib/dom";

    const {
        zone,
        item,
        secrets,
    } : {
        zone: Zone,
        item?: Item | null,
        secrets: SecretSource,
    } = $props();

    const text = $derived(zoneText(zone));
    const detail = $derived.by(() => (item ? detailOf(item, secrets) : null));
    const view = $derived.by(() => (item ? viewOf(item) : null));
    const hasPasskey = $derived.by(() =>
        item?.kind === 'passkey' || (item?.kind === 'web' && item.passkey !== undefined));
    const compromised = $derived(
         item?.kind === 'web' && item.pwned ? item.domain : '');
    let scroller: ElasticScroll | undefined = $state();
    const stack = $derived(isCompact());
    let contentHeight = $state(0);

    $effect(() => {
        item;
        scroller?.reset();
    });
</script>

<div class="container" class:stack use:observeSize={node => on_bounds?.(node.offsetLeft, node.offsetWidth)}>
    {#if item && detail && view}
        <div class="scroller">
            <ElasticScroll bind:this={scroller} contentHeight={contentHeight}>
                <div class="stack" bind:clientHeight={contentHeight}>
                    {#if compromised && zone.kind === 'security'}
                        <SecurityNotice icon={view.icon} domain={compromised}/>
                        <div class="card">
                            <PasswordInfoFields fields={detail.fields}/>
                        </div>
                    {:else}
                        <div class="card">
                            <PasswordIcon icon={view.icon} width="50px"/>
                            <p class="name">{detail.title}</p>
                            <PasswordInfoFields fields={detail.fields}/>
                        </div>
                    {/if}
                    {#if hasPasskey}
                        <DetailNote variant="passkey"/>
                    {/if}
                    {#if compromised && zone.kind !== 'security'}
                        <DetailNote variant="compromised" domain={compromised}/>
                    {/if}
                </div>
            </ElasticScroll>
        </div>
    {:else}
        <div class="no-selection">
            <svg xmlns="http://www.w3.org/2000/svg" height="50px" width="50px" viewBox="0 -960 960 960" fill="var(--hint-pass-selection)" style="pointer-events: none" aria-hidden="true">
                <path d="{pathFor(zone.icon, true)}"/>
            </svg>
            <p class="title">{text.emptyTitle}</p>
            <p class="desc">{text.emptyDescription}</p>
        </div>
    {/if}
</div>

<style>
    .container {
        display: flex;
        flex: 1.25;
        min-width: 0;
        height: 100%;
        align-items: start;
        justify-content: center;
        margin-inline: 8px;
    }

    .scroller {
        width: 100%;
        height: 100%;
    }

    .stack {
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    .card {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 100%;
        max-width: 600px;
        margin-inline: auto;
        border-radius: var(--zone-border-radius);
        background: var(--section-bg-color);
        padding-block: 10px;
        padding-inline: 15px;
    }

    .name {
        font-size: 22px;
        margin: 0 0 15px;
        font-weight: bold;
        text-align: center;
        color: var(--text-color);
    }

    .no-selection {
        display: flex;
        flex-direction: column;
        align-items: center;
        align-self: center;
        padding-inline: 50px;
    }

    .title {
        font-size: 20px;
        font-weight: bold;
        color: var(--hint-pass-selection);
        margin: 25px 0 0;
        text-align: center;
    }

    .desc {
        font-size: 14px;
        color: var(--hint-pass-selection);
        margin: 8px 0 0;
        text-align: center;
    }
</style>