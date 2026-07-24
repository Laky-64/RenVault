<script lang="ts">
    import {pathFor} from "./ZoneIcon";
    import {type Password, type Zone, zoneText} from "./ZoneContainer";
    import PasswordIcon from "./PasswordIcon.svelte";
    import PasswordInfoFields from "./PasswordInfoFields.svelte";
    import type {Field} from "./PasswordInfoFields";
    import ElasticScroll from "./ElasticScroll.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {formatDate} from "../lib/datetime";
    import {m} from "../paraglide/messages";

    const {
        zone,
        password,
    } : {
        zone: Zone,
        password?: Password | null,
    } = $props();

    const text = $derived(zoneText(zone));
    let fields: Field[] = $derived(password ? [
        {
            name: m.field_username(),
            value: password.email,
        },
        {
            name: m.field_password(),
            value: password.password,
            sensitive: true,
        },
        {
            name: m.field_website(),
            value: password.domains[0],
        },
        {
            name: m.field_modified(),
            value: formatDate(password.modified),
        }
    ] : []);

    let scroller: ElasticScroll | undefined = $state();
    const stack = $derived(isCompact());
    let contentHeight = $state(0);

    $effect(() => {
        password;
        scroller?.reset();
    });
</script>

<div class="container" class:stack>
    {#if password}
        <div class="scroller">
            <ElasticScroll bind:this={scroller} contentHeight={contentHeight}>
                <div class="stack" bind:clientHeight={contentHeight}>
                    <div class="card">
                        <PasswordIcon password={password} width="50px"/>
                        <p class="name">{password.name}</p>
                        <PasswordInfoFields fields={fields} />
                    </div>
                </div>
            </ElasticScroll>
        </div>
    {:else}
        <div class="no-selection">
            <svg xmlns="http://www.w3.org/2000/svg" height="50px" width="50px" viewBox="0 -960 960 960" fill="var(--hint-pass-selection)" style="pointer-events: none" aria-hidden="true">
                <path d="{pathFor(zone.icon, true)}"/>
            </svg>
            <p class="title">{text.emptyTitle}</p>
            <p class="desc">{text.emptyDescription}</p>
        </div>
    {/if}
</div>

<style>
    .container {
        display: flex;
        flex: 1.25;
        min-width: 0;
        height: 100%;
        align-items: start;
        justify-content: center;
        margin-inline: 8px;
    }

    .scroller {
        width: 100%;
        height: 100%;
    }

    .stack {
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    .card {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 100%;
        max-width: 600px;
        margin-inline: auto;
        border-radius: var(--zone-border-radius);
        background: var(--section-bg-color);
        padding-block: 10px;
        padding-inline: 15px;
    }

    .name {
        font-size: 22px;
        margin: 0 0 15px;
        font-weight: bold;
        color: var(--text-color);
    }

    .no-selection {
        display: flex;
        flex-direction: column;
        align-items: center;
        align-self: center;
        padding-inline: 50px;
    }

    .title {
        font-size: 20px;
        font-weight: bold;
        color: var(--hint-pass-selection);
        margin: 25px 0 0;
        text-align: center;
    }

    .desc {
        font-size: 14px;
        color: var(--hint-pass-selection);
        margin: 8px 0 0;
        text-align: center;
    }
</style>