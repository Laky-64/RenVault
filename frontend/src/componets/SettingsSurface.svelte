<script lang="ts">
    import MorphDialog from "./MorphDialog.svelte";
    import ElasticScroll from "./ElasticScroll.svelte";
    import Button from "./Button.svelte";
    import ProfileAvatar from "./ProfileAvatar.svelte";
    import SettingsPage from "../fragments/SettingsPage.svelte";
    import LoadingDialog from "./LoadingDialog.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {prefersReducedMotion} from "../lib/dom";
    import {m} from "../paraglide/messages";
    import {CLOSE} from "../lib/icons";

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
    let sheetHeight = $state(0);
    let dialogHeight = $state(0);
    let busy = $state(false);

    function close() {
        open = false;
    }

    function onKey(event: KeyboardEvent) {
        if (event.key === 'Escape') close();
    }
</script>

<svelte:window onkeydown={open && stack ? onKey : undefined}/>

{#snippet seed()}
    <ProfileAvatar size={40} {hasPhoto}/>
{/snippet}

{#snippet bar()}
    <div class="bar">
        <div class="close">
            <Button variant="glass" padding="7px" radius="999px" onclick={close}>
                <svg viewBox="0 -960 960 960" width="22" height="22" role="img"
                     aria-label={m.settings_close()}>
                    <path d={CLOSE} fill="var(--text-color)"/>
                </svg>
            </Button>
        </div>
        <p class="heading">{m.settings_title()}</p>
    </div>
{/snippet}

{#if stack}
    <div class="frame sheet" class:shown={open} class:still={reduced} inert={!open}>
        {@render bar()}
        <div class="scroll">
            <ElasticScroll contentHeight={sheetHeight}>
                <div class="body" bind:clientHeight={sheetHeight}>
                    <SettingsPage onClose={close} bind:busy/>
                </div>
            </ElasticScroll>
        </div>
        {#if busy}<LoadingDialog label={m.settings_checkPwned()}/>{/if}
    </div>
{:else}
    <MorphDialog bind:open bind:active {anchor} {seed} surface="page-surface" fluid>
        {#snippet children(_stack: boolean)}
            <div class="frame dialog" role="dialog" aria-modal="true" aria-label={m.settings_title()}>
                {@render bar()}
                <div class="scroll">
                    <ElasticScroll contentHeight={dialogHeight}>
                        <div class="body" bind:clientHeight={dialogHeight}>
                            <SettingsPage onClose={close} bind:busy/>
                        </div>
                    </ElasticScroll>
                </div>
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
        inset: 0 0 -22px;
        background: linear-gradient(
            to bottom,
            var(--secondary-bg-color) 0%,
            color-mix(in srgb, var(--secondary-bg-color) 88%, transparent) 52%,
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

    .scroll {
        flex: 1;
        min-height: 0;
    }

    .body {
        padding-top: var(--bar);
    }

    .dialog {
        --bar: 60px;
        width: 500px;
        max-width: 100vw;
        height: min(760px, 84vh);
        background: var(--secondary-bg-color);
    }

    .dialog > .scroll {
        padding-inline: 16px;
    }

    .dialog .body {
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

    .sheet > .scroll {
        padding-inline: 16px;
    }

    .sheet .body {
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
