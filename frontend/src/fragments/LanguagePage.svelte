<script lang="ts">
    import Card from "../componets/Card.svelte";
    import SettingsRow from "../componets/SettingsRow.svelte";
    import {chooseLocale, locale, localeChoices, localeName} from "../lib/locale.svelte";
    import type {Locale} from "../paraglide/runtime";
    import {m} from "../paraglide/messages";

    const {query = ''}: {query?: string} = $props();

    const sorted = $derived([...localeChoices()]
        .sort((a, b) => localeName(a).localeCompare(localeName(b), a)));

    const needle = $derived(query.trim().toLowerCase());
    const choices = $derived(needle === ''
        ? sorted
        : sorted.filter(value => localeName(value).toLowerCase().includes(needle)
            || value.toLowerCase().includes(needle)));

    function edgeOf(index: number, total: number) {
        if (total === 1) return 'only' as const;
        if (index === 0) return 'top' as const;
        if (index === total - 1) return 'bottom' as const;
        return 'middle' as const;
    }

    function pick(value: Locale) {
        chooseLocale(value);
    }
</script>

<div class="page">
    {#if choices.length === 0}
        <p class="empty">{m.search_empty({query: query.trim()})}</p>
    {:else}
        <Card>
            {#each choices as value, index (value)}
                <SettingsRow
                    edge={edgeOf(index, choices.length)}
                    label={localeName(value)}
                    check={value === locale()}
                    onclick={() => pick(value)}/>
            {/each}
        </Card>
    {/if}
</div>

<style>
    .page {
        display: flex;
        flex-direction: column;
        width: 100%;
    }

    .empty {
        margin: 26px 0 0;
        font-size: 14px;
        line-height: 1.4;
        text-align: center;
        color: var(--hint-color);
    }
</style>
