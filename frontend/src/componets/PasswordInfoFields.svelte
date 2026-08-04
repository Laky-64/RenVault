<script lang="ts">
    import {Clipboard} from "@wailsio/runtime";
    import CopyValue from "./CopyValue.svelte";
    import TotpField from "./TotpField.svelte";
    import {codeOf, type DetailField, plainOf, secretOf} from "../lib/items";

    const {
        fields,
    } : {
        fields: DetailField[];
    } = $props();

    const MASK = '••••••••••••';
    const COPIED_FOR = 1200;

    let revealed: Record<string, string> = $state({});
    let loading: string | null = $state(null);
    let hovered: string | null = $state(null);
    let copied: string | null = $state(null);
    let copiedTimer: ReturnType<typeof setTimeout> | undefined;

    $effect(() => {
        fields;
        revealed = {};
        copied = null;
        hovered = null;
        clearTimeout(copiedTimer);
    });

    async function copy(field: DetailField) {
        if (!field.copyable || loading === field.label) return;
        const load = secretOf(field) ?? codeOf(field);
        let text = plainOf(field);
        if (load) {
            loading = field.label;
            try {
                text = revealed[field.label] ?? await load();
            } finally {
                loading = null;
            }
        }
        if (!text) return;

        await Clipboard.SetText(text);
        clearTimeout(copiedTimer);
        copied = field.label;
        copiedTimer = setTimeout(() => (copied = null), COPIED_FOR);
    }

    async function hover(field: DetailField, entering: boolean) {
        const load = secretOf(field);
        if (!load) return;

        if (!entering) {
            hovered = hovered === field.label ? null : hovered;
            const {[field.label]: _dropped, ...rest} = revealed;
            revealed = rest;
            return;
        }

        hovered = field.label;
        if (revealed[field.label] !== undefined) return;

        loading = field.label;
        try {
            const value = await load();
            if (hovered === field.label) revealed = {...revealed, [field.label]: value};
        } finally {
            loading = null;
        }
    }
</script>

<div class="field-list">
    {#each fields as field (field.label)}
        {@const secret = secretOf(field)}
        {@const totp = codeOf(field)}
        {@const shown = secret ? revealed[field.label] : plainOf(field)}
        <div class="field" class:wrap={field.wrap}>
            <p class="name">{field.label}</p>

            {#if totp}
                <TotpField
                    label={field.label}
                    load={totp}
                    copied={copied === field.label}
                    onCopy={() => copy(field)}
                />
            {:else if field.copyable}
                <CopyValue
                    label={field.label}
                    text={secret !== null && shown === undefined ? MASK : (shown ?? '')}
                    masked={secret !== null && shown === undefined}
                    copied={copied === field.label}
                    onCopy={() => copy(field)}
                    onHover={(entering) => hover(field, entering)}
                />
            {:else if secret && shown === undefined}
                <p class="value masked" aria-hidden="true">{MASK}</p>
            {:else}
                <p class="value" class:wrapped={field.wrap}>{shown}</p>
            {/if}
        </div>
    {/each}
</div>

<style>
    .field-list {
        display: flex;
        flex-direction: column;
        width: 100%;
    }

    .field {
        position: relative;
        display: flex;
        align-items: center;
        width: 100%;
        padding-block: 15px;
    }

    .field:not(:last-child)::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 1px;
        background: var(--hairline-color);
    }

    .name {
        color: var(--text-color);
        margin: 0 15px 0 0;
        font-size: 15px;
        flex-shrink: 0;
    }

    .value {
        color: var(--hint-color);
        margin: 0 -2px 0 auto;
        padding-right: 2px;
        font-size: 15px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    /*noinspection CssUnusedSymbol*/
    .field.wrap {
        align-items: flex-start;
    }

    /*noinspection CssUnusedSymbol*/
    .value.wrapped {
        min-width: 0;
        text-align: right;
        line-height: 1.35;
        overflow-wrap: anywhere;
        white-space: pre-wrap;
    }

    .masked {
        display: inline-block;
        font-weight: bold;
        font-size: 25px;
        line-height: 15px;
        letter-spacing: -0.05em;
    }
</style>