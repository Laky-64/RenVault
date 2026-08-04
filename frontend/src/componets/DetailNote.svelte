<script lang="ts">
    import {Browser} from "@wailsio/runtime";
    import {m} from "../paraglide/messages";
    import CardAction from "./CardAction.svelte";
    import Card from "./Card.svelte";
    import {pathFor} from "./ZoneIcon";
    import {openChangePassword} from "../lib/changepw";
    import {PASSKEY_SEAL} from "../lib/icons";

    const {
        variant,
        label = '',
        domain = '',
    } : {
        variant: 'passkey' | 'compromised';
        label?: string;
        domain?: string;
    } = $props();

    const LEARN_MORE = 'https://support.apple.com/en-us/102195';

    const note = $derived(variant === 'passkey'
        ? {
            path: PASSKEY_SEAL,
            color: 'var(--passkey-badge-color)',
            title: m.field_passkey(),
            description: m.passkey_noteDesc(),
            action: m.passkey_learnMore(),
            run: () => Browser.OpenURL(LEARN_MORE),
        }
        : {
            path: pathFor('security', true),
            color: 'var(--destructive-text-color)',
            title: m.item_compromised(),
            description: m.security_changeDesc({domain: label}),
            action: domain ? m.security_changeAction() : '',
            run: () => void openChangePassword(domain),
        });
</script>

<Card>
    <div class="body">
        <svg class="seal" viewBox="0 -960 960 960" width="26" height="26" aria-hidden="true">
            <path d={note.path} fill={note.color} fill-rule="evenodd"/>
        </svg>
        <div class="text">
            <p class="title">{note.title}</p>
            <p class="desc">{note.description}</p>
        </div>
    </div>
    {#if note.action}
        <CardAction text={note.action} onclick={note.run} padding="12px 19px" inset="20px"/>
    {/if}
</Card>

<style>

    .body {
        display: flex;
        align-items: flex-start;
        width: 100%;
        gap: 12px;
        padding: 15px;
    }

    .seal {
        flex-shrink: 0;
        margin-top: 1px;
    }

    .text {
        min-width: 0;
    }

    .title {
        margin: 0 0 4px;
        font-size: 15px;
        font-weight: 600;
        color: var(--text-color);
    }

    .desc {
        margin: 0;
        font-size: 13px;
        line-height: 1.35;
        color: var(--subtitle-text-color);
    }

</style>
