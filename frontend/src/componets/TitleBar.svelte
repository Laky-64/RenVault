<script>
import Button from "./Button.svelte";
import { Window } from '@wailsio/runtime';
import {onMount, onDestroy} from "svelte";

let isMaximised = $state(true);
/**
 * @type {number | undefined}
 */
let pollId;

onMount(async () => {
    isMaximised = await Window.IsMaximised();

    pollId = setInterval(async () => {
        isMaximised = await Window.IsMaximised();
    }, 150);
});

onDestroy(() => {
    clearInterval(pollId);
});
</script>

<header>
    <div class="title-bar" ondblclick={() => Window.ToggleMaximise()} role="button" tabindex="0">
        <h1>{document.title}</h1>
    </div>
    <div class="right-items">
        <Button onclick={() => Window.Minimise()}>
            <svg xmlns="http://www.w3.org/2000/svg" height="14px" width="14px" viewBox="0 -960 960 960" fill="var(--text-color)">
                <path d="M280-120q-17 0-28.5-11.5T240-160q0-17 11.5-28.5T280-200h400q17 0 28.5 11.5T720-160q0 17-11.5 28.5T680-120H280Z"/>
            </svg>
        </Button>
        <Button onclick={() => Window.ToggleMaximise()}>
            {#if isMaximised}
                <svg xmlns="http://www.w3.org/2000/svg" height="14px" width="14px" viewBox="0 -960 960 960" fill="var(--text-color)">
                    <path d="M320-320h480v-400H320v400Zm0 80q-33 0-56.5-23.5T240-320v-480q0-33 23.5-56.5T320-880h480q33 0 56.5 23.5T880-800v480q0 33-23.5 56.5T800-240H320ZM160-80q-33 0-56.5-23.5T80-160v-520q0-17 11.5-28.5T120-720q17 0 28.5 11.5T160-680v520h520q17 0 28.5 11.5T720-120q0 17-11.5 28.5T680-80H160Zm160-720v480-480Z"/>
                </svg>
            {:else}
                <svg xmlns="http://www.w3.org/2000/svg" height="14px" width="14px" viewBox="0 -960 960 960" fill="var(--text-color)">
                    <path d="M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm0-80h560v-440H200v440Z"/>
                </svg>
            {/if}
        </Button>
        <Button onclick={() => Window.Close()} accent="var(--destructive-text-color)">
            <svg xmlns="http://www.w3.org/2000/svg" height="14px" width="14px" viewBox="0 -960 960 960" fill="var(--text-color)">
                <path d="M480-424 284-228q-11 11-28 11t-28-11q-11-11-11-28t11-28l196-196-196-196q-11-11-11-28t11-28q11-11 28-11t28 11l196 196 196-196q11-11 28-11t28 11q11 11 11 28t-11 28L536-480l196 196q11 11 11 28t-11 28q-11 11-28 11t-28-11L480-424Z"/>
            </svg>
        </Button>
    </div>
</header>

<style>
    header{
        display: flex;
        justify-content: space-between;
        align-items: center;
        width: 100%;
        flex-shrink: 0;
        --wails-draggable: drag;
    }

    h1 {
        color: var(--text-color);
        font-size: 16px;
        font-weight: 600;
        margin: 0;
        padding: 0;
    }

    .title-bar{
        flex: 1;
        align-self: stretch;
        display: flex;
        padding: 15px;
        align-items: center;
    }

    .title-bar:focus {
        outline: none;
        box-shadow: none;
    }

    .right-items {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 15px;
    }
</style>