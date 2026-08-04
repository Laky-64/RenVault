<script lang="ts">
    import type {Snippet} from "svelte";
    import MorphDialog from "./MorphDialog.svelte";
    import Button from "./Button.svelte";
    import FieldCard from "./FieldCard.svelte";
    import {tidyWebsites, websitesOk} from "../lib/detail";
    import {foldRow} from "../lib/motion";
    import {m} from "../paraglide/messages";
    import {CHECK, MINUS} from "../lib/icons";

    interface Row {
        id: number;
        value: string;
    }

    let {
        open = $bindable(false),
        active = $bindable(false),
        anchor,
        seed,
        title,
        websites = $bindable([]),
    } : {
        open?: boolean;
        active?: boolean;
        anchor?: HTMLElement;
        seed?: Snippet;
        title: string;
        websites?: string[];
    } = $props();

    let rows: Row[] = $state([]);
    let nextId = 0;
    let focusId: number | null = $state(null);
    let settled = $state(false);

    $effect(() => {
        if (!open) {
            settled = false;
            return;
        }
        rows = websites.map(value => ({id: nextId++, value}));
        focusId = null;
        const done = setTimeout(() => (settled = true), 0);
        return () => clearTimeout(done);
    });

    const values = $derived(rows.map(row => row.value));
    const canApply = $derived(websitesOk(values));

    function add() {
        const row = {id: nextId++, value: ''};
        rows = [...rows, row];
        focusId = row.id;
    }

    function remove(id: number) {
        rows = rows.filter(row => row.id !== id);
    }

    function apply() {
        if (!canApply) return;
        websites = tidyWebsites(values);
        open = false;
    }

    function autofocus(node: HTMLInputElement, wanted: boolean) {
        if (wanted) node.focus({preventScroll: true});
        return {
            update(next: boolean) {
                if (next) node.focus({preventScroll: true});
            },
        };
    }
</script>

<MorphDialog bind:open bind:active {anchor} {seed} fluid>
    {#snippet children(stack: boolean)}
        <div class="dialog" class:stack role="dialog" aria-modal="true" aria-label={m.websites_title()}>
            <div class="bar">
                <p class="heading">{m.websites_title()}</p>
                <div class="confirm">
                    <Button variant="glass" accent="var(--button-color)" padding="8px" radius="999px"
                            disabled={!canApply} onclick={apply}>
                        <svg viewBox="0 -960 960 960" width="20" height="20" role="img" aria-label={m.editor_save()}>
                            <path d={CHECK} fill="var(--button-text-color)"/>
                        </svg>
                    </Button>
                </div>
            </div>

            <p class="desc">{m.websites_desc({title})}</p>

            <FieldCard left="56px">
                {#each rows as row, i (row.id)}
                    <div class="row" transition:foldRow={{skip: !settled}}>
                        <button class="drop" type="button" aria-label={m.websites_remove()}
                                onclick={() => remove(row.id)}>
                            <svg viewBox="0 -960 960 960" width="18" height="18" aria-hidden="true">
                                <path d={MINUS} fill="var(--button-text-color)"/>
                            </svg>
                        </button>
                        <input
                            bind:value={rows[i].value}
                            placeholder={m.editor_websiteHint()}
                            autocomplete="off"
                            spellcheck="false"
                            use:autofocus={row.id === focusId}
                        />
                    </div>
                {/each}
                <Button block variant="plain" padding="15px 17px 15px 17px" radius="0" onclick={add}>
                    <span class="add">{m.websites_add()}</span>
                </Button>
            </FieldCard>
        </div>
    {/snippet}
</MorphDialog>

<style>
    .dialog {
        display: flex;
        flex-direction: column;
        width: 420px;
        max-width: 100vw;
        padding: 12px;
        gap: 12px;
    }

    .dialog.stack {
        width: 100%;
        height: 100%;
    }

    .bar {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 36px;
        flex-shrink: 0;
    }

    .heading {
        margin: 0;
        padding-inline: 44px;
        text-align: center;
        font-size: 15px;
        font-weight: 600;
        color: var(--text-color);
    }

    .confirm {
        position: absolute;
        top: 50%;
        right: 0;
        transform: translateY(-50%);
    }

    .desc {
        margin: 0;
        padding-inline: 4px;
        font-size: 13px;
        line-height: 1.35;
        color: var(--hint-color);
    }

    .row {
        display: flex;
        align-items: center;
        box-sizing: border-box;
        padding: 4px 14px 4px 17px;
    }

    .drop {
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        width: 22px;
        height: 22px;
        margin-right: 17px;
        padding: 0;
        border: none;
        border-radius: 999px;
        background: var(--destructive-text-color);
        cursor: pointer;
        --wails-draggable: no-drag;
        transition: transform 125ms ease-in;
    }

    .drop:active {
        transform: scale(1.1);
    }

    input {
        flex: 1;
        min-width: 0;
        height: 38px;
        border: none;
        background: transparent;
        font-family: inherit;
        font-size: 15px;
        font-weight: 500;
        letter-spacing: normal;
        color: var(--text-color);
        caret-color: var(--button-color);
        --wails-draggable: no-drag;
    }

    input:focus {
        outline: none;
    }

    input::placeholder {
        font-weight: normal;
        color: var(--hint-color);
    }

    .add {
        flex: 1;
        font-size: 15px;
        text-align: left;
        color: var(--link-color);
    }

    @media (prefers-reduced-motion: reduce) {
        .drop:active {
            transform: none;
        }
    }
</style>
