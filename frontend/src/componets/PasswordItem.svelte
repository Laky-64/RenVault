<script lang="ts">
    import {type Item, viewOf} from "../lib/items";
    import PasswordIcon from "./PasswordIcon.svelte";
    import TotpDigits from "./TotpDigits.svelte";
    import {totpSlot} from "../lib/totp.svelte";
    import {CHECK_MARK} from "../lib/icons";

    const {
        item,
        loadCode,
        notice,
        last = false,
        selected = false,
        selectable = false,
        checked = false,
        onclick,
    } : {
        item: Item;
        loadCode?: (item: Item) => Promise<string>;
        notice?: string;
        last?: boolean;
        selected?: boolean;
        selectable?: boolean;
        checked?: boolean;
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
<div class="row" role="button" aria-pressed={selectable ? checked : undefined}
     tabindex="0" onclick={handleClick} onkeydown={onKey} class:selected>
    <div class="check" class:open={selectable}>
        <span class="mark" class:checked>
            <svg viewBox="0 -960 960 960" width="12" height="12" aria-hidden="true">
                <path d={CHECK_MARK} fill="var(--button-text-color)"/>
            </svg>
        </span>
    </div>
    <PasswordIcon icon={view.icon}/>
    <div class="info-container" class:last>
        <div class="desc-container">
            <p class="name">{view.title}</p>
            <p class="desc" class:selected class:notice={notice !== undefined}>{notice ?? view.subtitle}</p>
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
        cursor: pointer;
    }

    .check {
        display: flex;
        flex-shrink: 0;
        align-items: center;
        justify-content: flex-start;
        width: 0;
        transition: width 260ms cubic-bezier(0.4, 0.05, 0.25, 1);
    }

    .check.open {
        width: 28px;
        transition: width 340ms cubic-bezier(0.32, 1.06, 0.5, 1);
    }

    .mark {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 18px;
        height: 18px;
        flex-shrink: 0;
        border-radius: 50%;
        box-sizing: border-box;
        border: 1.5px solid color-mix(in srgb, var(--text-color) 30%, transparent);
        background: transparent;
        opacity: 0;
        transform: translateX(-18px);
        transition:
            background 160ms ease,
            border-color 160ms ease,
            opacity 130ms ease,
            transform 240ms cubic-bezier(0.4, 0.05, 0.25, 1);
    }

    .check.open .mark {
        opacity: 1;
        transform: none;
        transition:
            background 160ms ease,
            border-color 160ms ease,
            opacity 220ms ease 80ms,
            transform 320ms cubic-bezier(0.32, 1.06, 0.5, 1) 80ms;
    }

    .mark > svg {
        opacity: 0;
        transform: scale(0.5);
        transition: opacity 140ms ease, transform 200ms cubic-bezier(0.34, 1.6, 0.5, 1);
    }

    .mark.checked {
        background: var(--button-color);
        border-color: var(--button-color);
        animation: pop 240ms cubic-bezier(0.34, 1.4, 0.5, 1);
    }

    @keyframes pop {
        0% {
            transform: scale(0.82);
        }
        60% {
            transform: scale(1.12);
        }
        100% {
            transform: scale(1);
        }
    }

    .mark.checked > svg {
        opacity: 1;
        transform: none;
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

    .desc.notice {
        color: var(--destructive-text-color);
    }

    .totp {
        flex-shrink: 0;
        margin-left: 8px;
        font-size: 15px;
        color: var(--subtitle-text-color);
    }

    @media (prefers-reduced-motion: reduce) {
        .check,
        .mark,
        .mark > svg {
            transition: none;
        }

        .mark.checked {
            animation: none;
        }
    }

    .row:not(:active):not(.selected) > .info-container:not(.last)::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 1px;
        background: var(--hairline-color);
    }
</style>