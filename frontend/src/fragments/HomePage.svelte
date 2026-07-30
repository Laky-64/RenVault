<script lang="ts">
    import type {Zone} from "../componets/ZoneContainer";
    import ZoneContainer from "../componets/ZoneContainer.svelte";
    import PasswordList from "../componets/PasswordList.svelte";
    import PasswordInfo from "../componets/PasswordInfo.svelte";
    import {currentItem, currentZone, nav, openItem, openZone} from "../navigation.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {Service} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";
    import {
        type PasskeyItem,
        type SecretSource,
        type WebItem,
        type WiFiItem,
        linkItems,
        passkeyItem,
        webItem,
        wifiItem,
    } from "../lib/items";
    import {describeFailure} from "../lib/failure";
    import {onMount} from "svelte";

    const secrets: SecretSource = {
        password: (id) => Service.GetPassword(id),
        totp: (id) => Service.GetTOTP(id),
    };

    let web: WebItem[] = $state([]);
    let wifi: WiFiItem[] = $state([]);
    let passkey: PasskeyItem[] = $state([]);

    const zones: Zone[] = $derived([
        {kind: 'all', icon: 'all', color: 'blue', items: web.filter(i => i.kind == 'web' && !i.isDeleted && !i.shared)},
        {kind: 'passkeys', icon: 'passkey', color: 'green', items: passkey.filter(i => i.kind == 'passkey' && !i.isDeleted)},
        {kind: 'codes', icon: 'codes', color: 'yellow', items: web.filter(i => i.kind === 'web' && i.hasTotp && !i.isDeleted && !i.shared)},
        {kind: 'networks', icon: 'wifi', color: 'teal', items: wifi},
        {kind: 'security', icon: 'security', color: 'red', items: []},
        {kind: 'deleted', icon: 'deleted', color: 'orange', items: web.filter(i => i.kind == 'web' && i.isDeleted && !i.shared)},
    ]);

    onMount(() => {
        void load();
    });

    async function load() {
        try {
            const [webMetas, wifiMetas, passkeyMetas] = await Promise.all([Service.ListWeb(), Service.ListWiFi(), Service.ListPasskey()]);
            const linked = linkItems((webMetas ?? []).map(webItem), (passkeyMetas ?? []).map(passkeyItem));
            web = linked.web;
            wifi = (wifiMetas ?? []).map(wifiItem);
            passkey = linked.passkeys;
        } catch (e) {
            console.error(describeFailure(e).raw);
        }
    }

    const stack = $derived(isCompact());
    const shownZone = $derived(currentZone() ?? zones[0]);
</script>

<div class="container" class:stack>
    <div class="pane" style="transform: translateX({nav.offsetOf(0)}%)" inert={!nav.isActive(0)}>
        <ZoneContainer zones={zones} on_selected={openZone}/>
    </div>
    <div class="pane" style="transform: translateX({nav.offsetOf(1)}%)" inert={!nav.isActive(1)}>
        <PasswordList zone={shownZone} {secrets} selected={currentItem() ?? null} on_selected={openItem}/>
    </div>
    <div class="pane" style="transform: translateX({nav.offsetOf(2)}%)" inert={!nav.isActive(2)}>
        <PasswordInfo zone={shownZone} item={currentItem() ?? null} {secrets}/>
    </div>
</div>

<style>
    .container {
        display: flex;
        width: 100%;
        flex: 1;
        min-height: 0;
    }

    .stack {
        position: relative;
        padding-inline: 0;
    }

    .pane {
        display: contents;
    }

    .stack > .pane {
        display: block;
        position: absolute;
        inset: 0;
        transition: transform 350ms cubic-bezier(0.32, 0.72, 0, 1);
        will-change: transform;
        background: var(--secondary-bg-color);
    }

    @media (prefers-reduced-motion: reduce) {
        .stack > .pane {
            transition: none;
        }
    }
</style>