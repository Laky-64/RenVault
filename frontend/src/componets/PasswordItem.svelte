<script lang="ts">
    import {type Item, viewOf} from "../lib/items";
    import PasswordIcon from "./PasswordIcon.svelte";
    import TotpDigits from "./TotpDigits.svelte";
    import {totpSlot} from "../lib/totp.svelte";

    const {
        item,
        loadCode,
        last = false,
        selected = false,
        onclick,
    } : {
        item: Item;
        loadCode?: (item: Item) => Promise<string>;
        last?: boolean;
        selected?: boolean;
        onclick?: () => void;
    } = $props();

    const view = $derived(viewOf(item));

    function onKey(e: KeyboardEvent) {
        if ((e.key === "Enter" || e.key === " ") && onclick) {
            e.preventDefault();
            onclick();
        }
    }

    function handleClick(e: MouseEvent) {
        if (onclick) {
            e.preventDefault();
            onclick();
        }
    }

    let code = $state('');
    let source: Item | null = null;
    const slot = $derived(totpSlot());

    $effect(() => {
        if (!loadCode) return;
        slot;
        if (item !== source) {
            source = item;
            code = '';
        }
        let alive = true;
        loadCode(item)
            .then(value => {
                if (alive) code = value;
            })
            .catch(() => {
                if (alive) code = '';
            });
        return () => (alive = false);
    });
</script>
<div class="row" role="button" tabindex="0" onclick={handleClick} onkeydown={onKey} class:selected>
    <PasswordIcon icon={view.icon}/>
    <div class="info-container" class:last>
        <div class="desc-container">
            <p class="name">{view.title}</p>
            <p class="desc" class:selected>{view.subtitle}</p>
        </div>
        {#if code}
            <div class="totp">
                <TotpDigits {code} source={item}/>
            </div>
        {/if}
    </div>
</div>
<style>
    .row {
        display: flex;
        padding-left: 8px;
        box-sizing: border-box;
        border-radius: 10px;
        overflow: hidden;
        transition: transform 150ms ease, background 150ms ease;
        margin-inline: 15px;
    }

    .row:focus {
        outline: none;
        box-shadow: none;
    }

    .row:active {
        transform: scale(1.02);
        background: color-mix(in srgb, var(--text-color) 10%, transparent);
    }

    .row.selected {
        background: color-mix(in srgb, var(--text-color) 8%, transparent);
    }

    .info-container {
        display: flex;
        position: relative;
        flex: 1;
        min-width: 0;
        align-items: center;
        margin-left: 12px;
        margin-right: 12px;
    }

    .desc-container {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: flex-start;
        position: relative;
        flex: 1;
        min-width: 0;
        gap: 2px;
    }

    .desc-container > p {
        margin: 0;
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .name {
        font-size: 15px;
        font-weight: 500;
        color: var(--text-color);
    }

    .desc {
        font-size: 12px;
        color: var(--subtitle-text-color);
    }

    .totp {
        flex-shrink: 0;
        margin-left: 8px;
        font-size: 15px;
        color: var(--subtitle-text-color);
    }

    .row:not(:active):not(.selected) > .info-container:not(.last)::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 1px;
        background: color-mix(in srgb, var(--text-color) 12%, transparent);
    }
</style>