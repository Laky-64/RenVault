<script lang="ts">
    import MorphDialog from "./MorphDialog.svelte";
    import ElasticScroll from "./ElasticScroll.svelte";
    import Button from "./Button.svelte";
    import ProfileAvatar from "./ProfileAvatar.svelte";
    import SettingsPage from "../fragments/SettingsPage.svelte";
    import LanguagePage from "../fragments/LanguagePage.svelte";
    import SearchField from "./SearchField.svelte";
    import LoadingDialog from "./LoadingDialog.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {prefersReducedMotion} from "../lib/dom";
    import {m} from "../paraglide/messages";
    import {CHEVRON, CLOSE} from "../lib/icons";
    import {SEARCH_HEIGHT} from "../lib/layout";

    let {
        open = $bindable(false),
        active = $bindable(false),
        anchor,
        hasPhoto = false,
    } : {
        open?: boolean;
        active?: boolean;
        anchor?: HTMLElement;
        hasPhoto?: boolean;
    } = $props();

    const stack = $derived(isCompact());
    const reduced = prefersReducedMotion();
    let rootHeight = $state(0);
    let langHeight = $state(0);
    let busy = $state(false);
    let deep = $state(false);
    let langOffset = $state(0);
    let query = $state('');

    $effect(() => {
        if (open) deep = false;
    });

    $effect(() => {
        if (!deep) query = '';
    });

    function close() {
        open = false;
    }

    function back() {
        deep = false;
    }

    function onKey(event: KeyboardEvent) {
        if (event.key !== 'Escape') return;
        if (deep) back();
        else close();
    }
</script>

<svelte:window onkeydown={open && stack ? onKey : undefined}/>

{#snippet seed()}
    <ProfileAvatar size={40} {hasPhoto}/>
{/snippet}

{#snippet bar()}
    <div class="bar">
        <div class="close">
            <Button variant="glass" padding="7px" radius="999px" morph={deep} flash={deep}
                    onclick={deep ? back : close}>
                <svg viewBox="0 -960 960 960" width="22" height="22" role="img"
                     aria-label={deep ? m.nav_back() : m.settings_close()}>
                    <path d={deep ? CHEVRON : CLOSE} fill="var(--text-color)"/>
                </svg>
            </Button>
        </div>
        <p class="heading">{deep ? m.settings_language() : m.settings_title()}</p>
    </div>
{/snippet}

{#snippet body()}
    <div class="panes" class:deep class:still={reduced}>
        <div class="pane" inert={deep}>
            <div class="scroll">
                <ElasticScroll contentHeight={rootHeight}>
                    <div class="content" bind:clientHeight={rootHeight}>
                        <SettingsPage onClose={close} onLanguage={() => (deep = true)} bind:busy/>
                    </div>
                </ElasticScroll>
            </div>
        </div>
        <div class="pane" inert={!deep}>
            <div class="scroll">
                <ElasticScroll contentHeight={langHeight} bind:shownOffset={langOffset}>
                    <div class="content lang" bind:clientHeight={langHeight}>
                        <LanguagePage {query}/>
                    </div>
                </ElasticScroll>
            </div>
        </div>
    </div>
    {#if deep}
        <div class="dock">
            <SearchField bind:value={query} placeholder={m.search_placeholder()} glass={langOffset > 2}/>
        </div>
    {/if}
{/snippet}

{#if stack}
    <div class="frame sheet" class:shown={open} class:still={reduced} inert={!open}
         style="--search: {deep ? SEARCH_HEIGHT : 0}px">
        {@render bar()}
        {@render body()}
        {#if busy}<LoadingDialog label={m.settings_checkPwned()}/>{/if}
    </div>
{:else}
    <MorphDialog bind:open bind:active {anchor} {seed} surface="page-surface" fluid>
        {#snippet children(_stack: boolean)}
            <div class="frame dialog" role="dialog" aria-modal="true" aria-label={m.settings_title()}
                 style="--search: {deep ? SEARCH_HEIGHT : 0}px">
                {@render bar()}
                {@render body()}
                {#if busy}<LoadingDialog label={m.settings_checkPwned()}/>{/if}
            </div>
        {/snippet}
    </MorphDialog>
{/if}

<style>
    .frame {
        position: relative;
        display: flex;
        flex-direction: column;
        overflow: hidden;
    }

    .bar {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        z-index: 2;
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: var(--bar);
        padding-inline: 14px;
        pointer-events: none;
    }

    .bar::before {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: calc(var(--bar) + var(--search, 0px));
        background: linear-gradient(
            to bottom,
            var(--secondary-bg-color) 0,
            color-mix(in srgb, var(--secondary-bg-color) 96%, transparent) 55%,
            color-mix(in srgb, var(--secondary-bg-color) 74%, transparent) 84%,
            transparent 100%
        );
        pointer-events: none;
    }

    .close,
    .heading {
        position: relative;
    }

    .close {
        position: absolute;
        left: 14px;
        z-index: 1;
        display: flex;
        pointer-events: auto;
    }

    .heading {
        margin: 0;
        font-size: 17px;
        font-weight: 600;
        color: var(--text-color);
    }

    .panes {
        position: relative;
        flex: 1;
        min-height: 0;
    }

    .pane {
        position: absolute;
        inset: 0;
        display: flex;
        background: var(--secondary-bg-color);
        transition: transform 350ms cubic-bezier(0.32, 0.72, 0, 1);
        will-change: transform;
    }

    .pane:last-child {
        transform: translateX(100%);
    }

    .panes.deep > .pane:first-child {
        transform: translateX(-28%);
    }

    .panes.deep > .pane:last-child {
        transform: translateX(0);
    }

    .panes.still > .pane {
        transition: none;
    }

    .scroll {
        flex: 1;
        min-height: 0;
    }

    .content {
        padding-top: var(--bar);
    }

    .content.lang {
        padding-top: calc(var(--bar) + var(--search, 0px));
    }

    .dock {
        position: absolute;
        top: var(--bar);
        left: 0;
        right: 0;
        z-index: 3;
        padding: 0 16px 12px;
    }

    .dialog {
        --bar: 60px;
        width: 500px;
        max-width: 100vw;
        height: min(760px, 84vh);
        background: var(--secondary-bg-color);
    }

    .dialog .scroll {
        padding-inline: 16px;
    }

    .dialog .content {
        padding-bottom: 18px;
    }

    .sheet {
        --bar: 64px;
        position: fixed;
        inset: 0;
        z-index: 40;
        background: var(--secondary-bg-color);
        transform: translateX(100%);
        transition: transform 350ms cubic-bezier(0.32, 0.72, 0, 1);
        will-change: transform;
    }

    .sheet .scroll {
        padding-inline: 16px;
    }

    .sheet .content {
        padding-bottom: 34px;
    }

    .sheet.shown {
        transform: translateX(0);
    }

    .sheet.still {
        transition: none;
    }

    @media (prefers-reduced-motion: reduce) {
        .sheet {
            transition: none;
        }
    }
</style>
