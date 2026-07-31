<script lang="ts">
    import {Browser} from "@wailsio/runtime";
    import {m} from "../paraglide/messages";
    import Button from "./Button.svelte";
    import Card from "./Card.svelte";
    import {pathFor} from "./ZoneIcon";
    import {openChangePassword} from "../lib/changepw";

    const {
        variant,
        domain = '',
    } : {
        variant: 'passkey' | 'compromised';
        domain?: string;
    } = $props();

    const PASSKEY_SEAL = 'm438-452-58-57q-11-11-27.5-11T324-508q-11 11-11 28t11 28l86 86q12 12 28 12t28-12l170-170q12-12 11.5-28T636-592q-12-12-28.5-12.5T579-593L438-452ZM326-90l-58-98-110-24q-15-3-24-15.5t-7-27.5l11-113-75-86q-10-11-10-26t10-26l75-86-11-113q-2-15 7-27.5t24-15.5l110-24 58-98q8-13 22-17.5t28 1.5l104 44 104-44q14-6 28-1.5t22 17.5l58 98 110 24q15 3 24 15.5t7 27.5l-11 113 75 86q10 11 10 26t-10 26l-75 86 11 113q2 15-7 27.5T802-212l-110 24-58 98q-8 13-22 17.5T584-74l-104-44-104 44q-14 6-28 1.5T326-90Z';
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
            description: m.security_changeDesc({domain}),
            action: m.security_changeAction(),
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
    <div class="footer">
        <Button
            block
            variant="plain"
            padding="12px 19px"
            radius="0 0 var(--zone-border-radius) var(--zone-border-radius)"
            onclick={note.run}
        >
            <span class="action">{note.action}</span>
        </Button>
    </div>
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

    .footer {
        display: flex;
        position: relative;
        width: 100%;
        justify-content: flex-end;
    }

    .footer::before {
        content: " ";
        position: absolute;
        right: 20px;
        left: 20px;
        top: 0;
        height: 1px;
        background: color-mix(in srgb, var(--text-color) 10%, transparent);
    }

    .action {
        flex: 1;
        text-align: left;
        font-size: 15px;
        color: var(--accent-text-color);
        white-space: nowrap;
    }
</style>
