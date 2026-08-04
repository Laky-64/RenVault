<script lang="ts">
    import {Clipboard} from "@wailsio/runtime";
    import Card from "./Card.svelte";
    import CardAction from "./CardAction.svelte";
    import Fold from "./Fold.svelte";
    import PasswordIcon from "./PasswordIcon.svelte";
    import PasswordInfoFields from "./PasswordInfoFields.svelte";
    import RevealToggle from "./RevealToggle.svelte";
    import TotpField from "./TotpField.svelte";
    import WebsitesDialog from "./WebsitesDialog.svelte";
    import TotpDialog from "./TotpDialog.svelte";
    import ConfirmDialog from "./ConfirmDialog.svelte";
    import {type Draft, titleOf} from "../lib/detail";
    import {codeOf, domainOf, type ItemDetail, type ItemIcon, type ItemView, type WebItem} from "../lib/items";
    import {openChangePassword} from "../lib/changepw";
    import {m} from "../paraglide/messages";

    let {
        item,
        detail,
        view,
        editing = false,
        draft = $bindable(),
        on_delete,
    } : {
        item: WebItem;
        detail: ItemDetail;
        view: ItemView;
        editing?: boolean;
        draft: Draft;
        on_delete?: () => void;
    } = $props();

    const COPIED_FOR = 1200;

    let secretShown = $state(false);
    let websitesOpen = $state(false);
    let websitesAnchor: HTMLElement | undefined = $state();
    let totpOpen = $state(false);
    let dropOpen = $state(false);
    let codeCopied = $state(false);
    let copyTimer: ReturnType<typeof setTimeout>;

    $effect(() => {
        editing;
        secretShown = false;
    });

    const masked = $derived(!secretShown && draft.password.length > 0);
    const savedCode = $derived(item.hasTotp && !draft.totpRemoved);
    const hasCode = $derived(savedCode || draft.totpKey !== null);
    const codeLoad = $derived.by(() => {
        if (!savedCode || draft.totpKey !== null) return null;
        for (const field of detail.fields) {
            const load = codeOf(field);
            if (load) return load;
        }
        return null;
    });

    async function copyCode() {
        if (!codeLoad) return;
        const code = await codeLoad();
        if (!code) return;

        await Clipboard.SetText(code);
        clearTimeout(copyTimer);
        codeCopied = true;
        copyTimer = setTimeout(() => (codeCopied = false), COPIED_FOR);
    }

    function dropCode() {
        draft.totpKey = null;
        draft.totpRemoved = item.hasTotp;
    }
    const websiteLabel = $derived.by(() => {
        if (draft.websites.length === 0) return m.edit_noWebsite();
        if (draft.websites.length === 1) return draft.websites[0];
        return m.field_websiteMore({domain: draft.websites[0], count: draft.websites.length - 1});
    });
    const icon = $derived<ItemIcon>(editing
        ? {source: 'favicon', domain: domainOf(draft.websites[0] ?? ''), fallback: titleOf(draft) || '?'}
        : view.icon);
</script>

<div class="body">
    <Card padding="10px 15px">
        <PasswordIcon {icon} width="50px"/>
        {#if editing}
            <input class="title" bind:value={draft.title} placeholder={draft.titleHint}
                   autocomplete="off" spellcheck="false"/>
        {:else}
            <p class="name">{detail.title}</p>
        {/if}

        <Fold open={!editing} bleed>
            <PasswordInfoFields fields={detail.fields}/>
        </Fold>

        <Fold open={editing}>
            <div class="rows">
                <label class="field">
                    <span class="label">{m.field_username()}</span>
                    <input bind:value={draft.username} placeholder={m.editor_usernameHint()}
                           autocomplete="off" spellcheck="false"/>
                </label>
            </div>
        </Fold>
    </Card>

    <Fold open={editing}>
        <div class="spaced">
            <Card>
                <div class="rows inset">
                    <label class="field">
                        <span class="label">{m.field_password()}</span>
                        <input class:masked-text={masked} type={secretShown ? 'text' : 'password'}
                               bind:value={draft.password} autocomplete="new-password"/>
                        <RevealToggle bind:shown={secretShown} padding="0 0 0 10px"/>
                    </label>
                </div>
                {#if draft.websites.length > 0}
                    <CardAction text={m.security_changeAction()} onclick={() => openChangePassword(draft.websites[0])}/>
                {/if}
            </Card>
        </div>
    </Fold>

    <Fold open={editing}>
        <div class="spaced">
            <Card>
                <div class="rows inset">
                    <div class="field">
                        <span class="label">{m.field_verificationCode()}</span>
                        {#if codeLoad}
                            <TotpField
                                label={m.field_verificationCode()}
                                load={codeLoad}
                                copied={codeCopied}
                                onCopy={copyCode}/>
                        {:else}
                            <span class="value">{hasCode ? m.edit_saveForCode() : m.edit_noCode()}</span>
                        {/if}
                    </div>
                </div>
                {#if hasCode}
                    <CardAction text={m.edit_deleteCode()} onclick={dropCode} danger/>
                {:else}
                    <CardAction text={m.edit_setUpCode()} onclick={() => (totpOpen = true)}/>
                {/if}
            </Card>
        </div>
    </Fold>

    <Fold open={editing}>
        <div class="spaced">
            <Card>
                <div class="head" bind:this={websitesAnchor}>
                    <CardAction edge="bottom" onclick={() => (websitesOpen = true)}>
                        <span class="label">{m.field_website()}</span>
                        <span class="value">{websiteLabel}</span>
                    </CardAction>
                </div>
                <div class="rows inset">
                    <label class="field notes">
                        <textarea bind:value={draft.note} rows="3" placeholder={m.editor_notes()}></textarea>
                    </label>
                </div>
            </Card>
        </div>
    </Fold>

    <Fold open={editing}>
        <div class="spaced">
            <Card radius="100px">
                <CardAction text={m.edit_delete()} onclick={() => (dropOpen = true)}
                            danger centred edge="none" radius="var(--zone-border-radius)"/>
            </Card>
        </div>
    </Fold>
</div>

<WebsitesDialog
    bind:open={websitesOpen}
    anchor={websitesAnchor}
    title={titleOf(draft)}
    bind:websites={draft.websites}>
    {#snippet seed()}
        <div class="seed-row">
            <span class="label">{m.field_website()}</span>
            <span class="value">{websiteLabel}</span>
        </div>
    {/snippet}
</WebsitesDialog>

<TotpDialog
    bind:open={totpOpen}
    domain={draft.websites[0] ?? titleOf(draft)}
    on_key={key => {
        draft.totpKey = key;
        draft.totpRemoved = false;
    }}/>

<ConfirmDialog
    bind:open={dropOpen}
    title={m.delete_title()}
    desc={m.delete_softDesc()}
    confirm={m.delete_confirm()}
    danger
    on_confirm={() => on_delete?.()}/>

<style>
    .body {
        display: flex;
        flex-direction: column;
        width: 100%;
    }

    .spaced {
        padding-top: 10px;
    }

    .name {
        font-size: 22px;
        margin: 0 0 15px;
        font-weight: bold;
        text-align: center;
        letter-spacing: normal;
        color: var(--text-color);
    }

    .title {
        width: 100%;
        margin: 0 0 15px;
        padding-inline: 4px;
        border: none;
        background: transparent;
        text-align: center;
        font-family: inherit;
        font-size: 22px;
        font-weight: bold;
        color: var(--text-color);
    }

    .head {
        display: flex;
        width: 100%;
    }

    .rows {
        display: flex;
        flex-direction: column;
        width: 100%;
    }

    .rows.inset {
        padding-inline: 15px;
    }

    .seed-row {
        display: flex;
        align-items: center;
        width: 100%;
        height: 100%;
        padding-inline: 15px;
    }

    .field {
        position: relative;
        display: flex;
        align-items: center;
        width: 100%;
        padding: 15px 0;
        border: none;
        background: none;
        font-family: inherit;
        text-align: left;
    }

    .rows > :global(*:not(:last-child))::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 1px;
        background: var(--hairline-color);
    }

    .label {
        flex-shrink: 0;
        margin-right: 15px;
        font-size: 15px;
        color: var(--text-color);
    }

    .value {
        margin: 0 -2px 0 auto;
        padding-right: 2px;
        overflow: hidden;
        font-size: 15px;
        color: var(--hint-color);
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    input,
    textarea {
        min-width: 0;
        border: none;
        background: transparent;
        font-family: inherit;
        font-size: 15px;
        font-weight: 500;
        color: var(--text-color);
        caret-color: var(--button-color);
        --wails-draggable: no-drag;
    }

    input:not(.masked-text),
    textarea {
        letter-spacing: normal;
    }

    .field > input {
        flex: 1;
        text-align: right;
        padding-right: 4px;
    }

    .notes {
        padding-block: 12px;
    }

    .notes > textarea {
        width: 100%;
        padding-right: 4px;
        resize: none;
        line-height: 1.35;
    }

    input:focus,
    textarea:focus {
        outline: none;
    }

    input::placeholder,
    textarea::placeholder {
        color: var(--hint-color);
    }

</style>
