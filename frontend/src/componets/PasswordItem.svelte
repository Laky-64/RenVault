<script lang="ts">
    import {onMount} from "svelte";
    import {fly} from "svelte/transition";
    import type {Password} from "./ZoneContainer";
    import PasswordIcon from "./PasswordIcon.svelte";

    const {
        password,
        totpDigits = 6,
        last = false,
        selected = false,
        onclick,
    } : {
        password: Password;
        totpDigits?: number;
        last?: boolean;
        selected?: boolean;
        onclick?: () => void;
    } = $props();

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

    const groups = $derived(
        password.totp === undefined
            ? []
            : String(password.totp).padStart(totpDigits, '0').match(/.{1,3}/g) ?? [],
    );

    const FLY_DURATION = 300;
    let mounted = $state(false);
    onMount(() => {
        mounted = true;
    });
</script>
<div class="row" role="button" tabindex="0" onclick={handleClick} onkeydown={onKey} class:selected>
    <PasswordIcon password={password} />
    <div class="info-container" class:last>
        <div class="desc-container">
            <p class="name">{password.name}</p>
            <p class="desc" class:selected>{password.email}</p>
        </div>
        {#if password.totp !== undefined}
            <div class="totp-slot">
                {#key password.totp}
                    <p class="totp" in:fly={{ y: -8, duration: mounted ? FLY_DURATION : 0 }} out:fly={{ y: 8, duration: FLY_DURATION }}>
                        {#each groups as group}<span>{group}</span>{/each}
                    </p>
                {/key}
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

    .totp-slot {
        display: grid;
        flex-shrink: 0;
        overflow: hidden;
    }

    .totp-slot > .totp {
        grid-area: 1 / 1;
    }

    .totp {
        display: flex;
        gap: 4px;
        margin: 0 0 0 8px;
        font-size: 15px;
        font-weight: 600;
        color: var(--subtitle-text-color);
        flex-shrink: 0;
        font-variant-numeric: tabular-nums;
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