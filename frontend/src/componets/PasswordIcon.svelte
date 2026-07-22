<script lang="ts">
    import type {Password} from "./ZoneContainer";

    const {
        password,
        width = '38px',
    } : {
        password: Password;
        width?: string;
    } = $props();

    const src = $derived(password.icon ? `/icons?src=${encodeURIComponent(password.icon)}` : undefined);
    let failed = $state(false);
    $effect(() => {
        src;
        failed = false;
    });
</script>

{#if src && !failed}
    <!--suppress HtmlDeprecatedAttribute -->
    <img style="width: {width};" class="icon" src="{src}" alt="" onerror={() => failed = true}/>
{:else}
    <div style="width: {width};font-size: calc(calc({width} * 52) / 100);" class="icon empty">{password.name[0].toUpperCase()}</div>
{/if}

<style>
    .icon {
        display: flex;
        align-items: center;
        justify-content: center;
        aspect-ratio: 1/1;
        flex-shrink: 0;
        border-radius: 8px;
        align-self: center;
        margin-block: 8px;
        pointer-events: none;
        background: color-mix(in srgb, var(--text-color) 7%, transparent);
        overflow: hidden;
    }

    .icon.empty {
        background: color-mix(in srgb, var(--text-color) 13%, transparent);
        color: color-mix(in srgb, var(--text-color) 45%, transparent);
        font-weight: 500;
    }
</style>