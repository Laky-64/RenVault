<script lang="ts">
import TitleBar from "./componets/TitleBar.svelte";
import {onMount} from "svelte";
import {WML} from "@wailsio/runtime";
import HomePage from "./fragments/HomePage.svelte";

onMount(() => {
    WML.Reload();
});

</script>
<main>
    <TitleBar/>
    <HomePage/>
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
</style>