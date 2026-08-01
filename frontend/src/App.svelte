<script lang="ts">
import TitleBar from "./componets/TitleBar.svelte";
import {onMount} from "svelte";
import {Events, WML} from "@wailsio/runtime";
import HomePage from "./fragments/HomePage.svelte";
import type {TransitionConfig} from "svelte/transition";
import AuthFlow from "./fragments/AuthFlow.svelte";
import {nav} from "./navigation.svelte";
import {LOCKED_EVENT} from "./lib/lock";

onMount(() => {
    WML.Reload();
    return Events.On(LOCKED_EVENT, () => {
        authenticated = false;
        nav.reset();
    });
});

const DURATION = typeof location !== 'undefined' && location.search.includes('slow')
    ? 2000
    : 320;

function swap(_node: Element, {scale}: {scale: number}): TransitionConfig {
    return {
        duration: DURATION,
        css: (t, u) => `opacity: ${t}; transform: scale(${1 + (scale - 1) * u})`,
    };
}

let authenticated = $state(false);

</script>
<main>
    <TitleBar/>
    <div class="page">
        {#if authenticated}
            <div class="screen" in:swap={{scale: 1.02}}>
                <HomePage/>
            </div>
        {:else}
            <div class="screen" out:swap={{scale: 0.98}}>
                <AuthFlow onDone={() => authenticated = true}/>
            </div>
        {/if}
    </div>
</main>

<style>
    main {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 100%;
        height: 100vh;
        overflow: hidden;
        background: var(--secondary-bg-color);
    }

    .page {
        display: grid;
        min-height: 0;
        width: 100%;
        height: 100%;
    }

    .screen {
        grid-area: 1 / 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
    }
</style>