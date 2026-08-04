<script lang="ts">
    import {pathFor} from "./ZoneIcon";
    import {type Zone, zoneText} from "./ZoneContainer";
    import PasswordIcon from "./PasswordIcon.svelte";
    import PasswordInfoFields from "./PasswordInfoFields.svelte";
    import PasswordBody from "./PasswordBody.svelte";
    import type {Draft} from "../lib/detail";
    import {DELETED_DAYS, detailOf, editableOf, type Item, labelOf, type SecretSource, siteOf, viewOf} from "../lib/items";
    import {daysLeft} from "../lib/datetime";
    import {m} from "../paraglide/messages";
    import ElasticScroll from "./ElasticScroll.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import DetailNote from "./DetailNote.svelte";
    import SecurityNotice from "./SecurityNotice.svelte";
    import Card from "./Card.svelte";
    import CardAction from "./CardAction.svelte";
    import ConfirmDialog from "./ConfirmDialog.svelte";
    import {BAR_HEIGHT} from "../lib/layout";
    import {observeSize} from "../lib/dom";

    let {
        zone,
        item,
        secrets,
        editing = false,
        draft = $bindable(),
        on_bounds,
        on_delete,
        on_recover,
        on_purge,
    } : {
        zone: Zone,
        item?: Item | null,
        secrets: SecretSource,
        editing?: boolean,
        draft: Draft,
        on_bounds?: (left: number, width: number) => void,
        on_delete?: () => void,
        on_recover?: () => void,
        on_purge?: () => void,
    } = $props();

    const text = $derived(zoneText(zone));
    const detail = $derived.by(() => (item ? detailOf(item, secrets) : null));
    const view = $derived.by(() => (item ? viewOf(item) : null));
    const hasPasskey = $derived.by(() =>
        item?.kind === 'passkey' || (item?.kind === 'web' && item.passkey !== undefined));
    const pwned = $derived(item?.kind === 'web' && item.pwned);
    const pwnedLabel = $derived(item?.kind === 'web' && item.pwned ? labelOf(item) : '');
    const pwnedSite = $derived(item?.kind === 'web' && item.pwned ? siteOf(item) : '');
    const target = $derived(editableOf(item));
    const notice = $derived(pwned && zone.kind === 'security' && !editing);
    const deleted = $derived(item !== null && item !== undefined && item.kind !== 'wifi' && item.isDeleted);
    const left = $derived(deleted && item?.kind !== 'wifi' ? daysLeft(item?.deleted, DELETED_DAYS) : 0);
    let purgeOpen = $state(false);
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
                <div class="stack" style="--inset: {BAR_HEIGHT}px" bind:clientHeight={contentHeight}>
                    {#if notice}
                        <SecurityNotice icon={view.icon} label={pwnedLabel} domain={pwnedSite}/>
                        <Card padding="10px 15px">
                            <PasswordInfoFields fields={detail.fields}/>
                        </Card>
                    {:else if target}
                        <PasswordBody
                            item={target}
                            {detail}
                            {view}
                            {editing}
                            bind:draft
                            {on_delete}/>
                    {:else}
                        <Card padding="10px 15px">
                            <PasswordIcon icon={view.icon} width="50px"/>
                            <p class="name">{detail.title}</p>
                            <PasswordInfoFields fields={detail.fields}/>
                        </Card>
                    {/if}
                    {#if deleted}
                        <p class="countdown">{m.deleted_countdown({count: left})}</p>
                        <Card>
                            <CardAction text={m.deleted_recover()} edge="bottom"
                                        onclick={() => on_recover?.()}/>
                            <CardAction text={m.deleted_delete()} danger edge="none"
                                        radius="0 0 var(--zone-border-radius) var(--zone-border-radius)"
                                        onclick={() => (purgeOpen = true)}/>
                        </Card>
                    {/if}
                    {#if hasPasskey && !editing}
                        <DetailNote variant="passkey"/>
                    {/if}
                    {#if pwned && zone.kind !== 'security' && !editing}
                        <DetailNote variant="compromised" label={pwnedLabel} domain={pwnedSite}/>
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

<ConfirmDialog
    bind:open={purgeOpen}
    title={m.delete_title()}
    desc={m.delete_hardDesc()}
    confirm={m.delete_confirm()}
    danger
    on_confirm={() => on_purge?.()}/>

<style>
    .container {
        display: flex;
        flex: 1.25;
        min-width: 0;
        height: 100%;
        align-items: start;
        justify-content: center;
        margin-inline: 12px;
    }

    .scroller {
        width: 100%;
        height: 100%;
    }

    .stack {
        display: flex;
        flex-direction: column;
        gap: 10px;
        padding-block: var(--inset);
    }

    .countdown {
        margin: 0;
        padding-inline: 15px;
        font-size: 13px;
        line-height: 1.35;
        text-align: center;
        color: var(--hint-color);
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
        max-width: 500px;
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