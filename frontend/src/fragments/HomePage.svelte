<script lang="ts">
    import type {Zone} from "../componets/ZoneContainer";
    import ZoneContainer from "../componets/ZoneContainer.svelte";
    import NavBar from "../componets/NavBar.svelte";
    import ActionBar from "../componets/ActionBar.svelte";
    import {SvelteSet} from "svelte/reactivity";
    import {type Bounds, LIST_MAX, ZONES_MAX} from "../lib/layout";
    import PasswordList from "../componets/PasswordList.svelte";
    import PasswordInfo from "../componets/PasswordInfo.svelte";
    import {currentItem, currentZone, nav, openItem, openZone} from "../navigation.svelte";
    import {isCompact} from "../lib/navigation.svelte";
    import {Service} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";
    import {
        type Item,
        type PasskeyItem,
        type SecretSource,
        type WebItem,
        type WiFiItem,
        linkItems,
        passkeyItem,
        webItem,
        wifiItem,
    } from "../lib/items";
    import {type SortField, sortItems} from "../lib/sort";
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
        {kind: 'security', icon: 'security', color: 'red', items: web.filter(i => i.kind == 'web' && !i.isDeleted && !i.shared && i.pwned)},
        {kind: 'deleted', icon: 'deleted', color: 'orange', items: web.filter(i => i.kind == 'web' && i.isDeleted && !i.shared)},
    ]);

    const PWNED_TTL = 24 * 60 * 60 * 1000;

    onMount(() => {
        void loadSettings();
        void load().then(() => refreshPwned());
    });

    async function loadSettings() {
        try {
            const settings = await Service.Settings();
            sortField = settings.sortField as SortField;
            sortAscending = settings.sortAscending;
        } catch (e) {
            console.error(describeFailure(e).raw);
        }
    }

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

    async function refreshPwned(force = false) {
        try {
            if (!force) {
                const info = await Service.PwnedInfo();
                const checkedAt = new Date(info.checkedAt).getTime();
                if (Number.isFinite(checkedAt) && Date.now() - checkedAt < PWNED_TTL) {
                    return;
                }
            }
            await Service.CheckPwned();
            await load();
        } catch (e) {
            console.error(describeFailure(e).raw);
        }
    }

    const stack = $derived(isCompact());
    let sortField: SortField = $state('title');
    let sortAscending = $state(true);
    const activeZone = $derived(zones.find(z => z.kind === currentZone()?.kind) ?? zones[0]);
    const shownZone = $derived({...activeZone, items: sortItems(activeZone.items, sortField, sortAscending)});

    let selecting = $state(false);
    const picked = new SvelteSet<string>();
    const allPicked = $derived(shownZone.items.length > 0 && picked.size === shownZone.items.length);

    $effect(() => {
        selecting;
        shownZone.kind;
        picked.clear();
    });

    function toggle(item: Item) {
        if (picked.has(item.id)) picked.delete(item.id);
        else picked.add(item.id);
    }

    function selectAll() {
        if (allPicked) {
            picked.clear();
            return;
        }
        for (const entry of shownZone.items) picked.add(entry.id);
    }

    function deleteSelected() {
    }

    function applySort(field: SortField, ascending: boolean) {
        sortField = field;
        sortAscending = ascending;
        Service.SetSortPreference(field, ascending)
            .catch(e => console.error(describeFailure(e).raw));
    }

    function addItem() {
    }

    let listStuck = $state(false);
    let listBounds: Bounds = $state({left: 0, width: 0});
    let detailBounds: Bounds = $state({left: 0, width: 0});
    let editing = $state(false);
</script>

<div class="container" class:stack
     style="--zones-max: {ZONES_MAX}px; --list-max: {LIST_MAX}px">
    <div class="pane" style="transform: translateX({nav.offsetOf(0)}%)" inert={!nav.isActive(0)}>
        <ZoneContainer zones={zones} selected={shownZone} on_selected={openZone}/>
    </div>
    <div class="pane" style="transform: translateX({nav.offsetOf(1)}%)" inert={!nav.isActive(1)}>
        <PasswordList
            zone={shownZone}
            {secrets}
            selected={currentItem() ?? null}
            on_selected={openItem}
            {selecting}
            checked={picked}
            on_toggle={toggle}
            bind:stuck={listStuck}
            on_bounds={(left, width) => { listBounds = {left, width}; }}/>
    </div>
    <div class="pane" style="transform: translateX({nav.offsetOf(2)}%)" inert={!nav.isActive(2)}>
        <PasswordInfo
            zone={shownZone}
            item={currentItem() ?? null}
            {secrets}
            on_bounds={(left, width) => { detailBounds = {left, width}; }}/>
    </div>
    <NavBar
        zone={shownZone}
        list={listBounds}
        detail={detailBounds}
        stuck={listStuck}
        allSelected={allPicked}
        selectedCount={picked.size}
        bind:selecting
        bind:editing
        on_selectAll={selectAll}/>
    <ActionBar
        list={listBounds}
        {selecting}
        canDelete={picked.size > 0}
        canAdd={shownZone.kind === 'all'}
        {sortField}
        {sortAscending}
        on_sort={applySort}
        on_delete={deleteSelected}
        on_add={addItem}/>
</div>

<style>
    .container {
        display: flex;
        position: relative;
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