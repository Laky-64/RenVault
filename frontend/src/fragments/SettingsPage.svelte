<script lang="ts">
    import Card from "../componets/Card.svelte";
    import SettingsRow from "../componets/SettingsRow.svelte";
    import ProfileAvatar from "../componets/ProfileAvatar.svelte";
    import ConfirmDialog from "../componets/ConfirmDialog.svelte";
    import Menu from "../componets/Menu.svelte";
    import {Service} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";
    import {Service as AppService} from "../../bindings/github.com/Laky-64/RenVault/internal/appinfo";
    import type {ProfileMeta} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";
    import type {MenuSection} from "../lib/menu";
    import {describeFailure} from "../lib/failure";
    import {formatRelative} from "../lib/datetime";
    import {appName} from "../lib/app.svelte";
    import {locale, localeName} from "../lib/locale.svelte";
    import {m} from "../paraglide/messages";

    let {
        onClose,
        onLanguage,
        busy = $bindable(false),
    }: {
        onClose?: () => void;
        onLanguage?: () => void;
        busy?: boolean;
    } = $props();

    const BUSY_AFTER = 350;
    const CLOCK_TICK = 30_000;

    let profile: ProfileMeta | null = $state(null);
    let minutes = $state(5);
    let choices: number[] = $state([]);
    let version = $state('');
    let pending = $state(0);
    let syncedAt = $state('');
    let lockOpen = $state(false);
    let lockAnchor: HTMLElement | undefined = $state();
    let signOutOpen = $state(false);
    let checking = $state(false);

    let now = $state(Date.now());

    $effect(() => {
        void refresh();
        const clock = setInterval(() => (now = Date.now()), CLOCK_TICK);
        return () => clearInterval(clock);
    });

    async function load<T>(call: () => Promise<T>, apply: (value: T) => void) {
        try {
            apply(await call());
        } catch (e) {
            console.error(describeFailure(e).raw);
        }
    }

    async function refresh() {
        await Promise.all([
            load(() => Service.ProfileInfo(), value => (profile = value)),
            load(() => Service.AutoLockMinutes(), value => (minutes = value)),
            load(() => Service.AutoLockChoices(), value => (choices = value ?? [])),
            load(() => AppService.AppVersion(), value => (version = value)),
            load(() => Service.SyncState(), value => {
                pending = value.pending;
                syncedAt = value.syncedAt;
            }),
        ]);
    }

    function lockLabel(value: number): string {
        if (value <= 0) return m.settings_autoLockNever();
        if (value === 1) return m.settings_autoLockMinute();
        if (value === 60) return m.settings_autoLockHour();
        return m.settings_autoLockMinutes({count: value});
    }

    const lockSections: MenuSection[] = $derived([
        choices.map(value => ({id: String(value), label: lockLabel(value), checked: value === minutes})),
    ]);

    async function pickLock(id: string) {
        const value = Number(id);
        if (!Number.isFinite(value) || value === minutes) return;
        try {
            await Service.SetAutoLockMinutes(value);
            minutes = value;
        } catch (e) {
            console.error(describeFailure(e).raw);
        }
    }

    async function run(action: () => Promise<unknown>) {
        try {
            await action();
        } catch (e) {
            console.error(describeFailure(e).raw);
        }
    }

    async function lockNow() {
        onClose?.();
        await run(() => Service.Lock());
    }

    async function checkPwned() {
        if (checking) return;
        checking = true;
        const slow = setTimeout(() => (busy = true), BUSY_AFTER);
        await run(() => Service.CheckPwned());
        await refresh();
        clearTimeout(slow);
        busy = false;
        checking = false;
    }

    async function signOut() {
        onClose?.();
        await run(() => Service.SignOut());
    }

    const syncedLabel = $derived(formatRelative(syncedAt, now) || m.settings_syncedNever());
</script>

<div class="page">
    <div class="identity">
        <div class="grow">
            <ProfileAvatar size={76} hasPhoto={profile?.hasPhoto ?? false}/>
        </div>
        <p class="name">{profile?.name || appName()}</p>
        {#if profile?.appleId}
            <p class="mail">{profile.appleId}</p>
        {/if}
    </div>

    <p class="section">{m.settings_generalTitle()}</p>
    <Card>
        <SettingsRow
            edge="only"
            label={m.settings_language()}
            value={localeName(locale())}
            chevron
            onclick={() => onLanguage?.()}/>
    </Card>

    <p class="section">{m.settings_securityTitle()}</p>
    <Card>
        <div class="anchor" bind:this={lockAnchor}>
            <SettingsRow
                edge="top"
                label={m.settings_autoLock()}
                value={lockLabel(minutes)}
                chevron
                onclick={() => (lockOpen = true)}/>
        </div>
        <SettingsRow edge="bottom" label={m.settings_lockNow()} onclick={lockNow}/>
    </Card>
    <p class="note">{m.settings_autoLockNote()}</p>

    <div class="detached">
        <Card>
            <SettingsRow
                edge="only"
                tone="accent"
                label={m.settings_checkPwned()}
                onclick={checkPwned}/>
        </Card>
    </div>

    <p class="section">{m.settings_aboutTitle()}</p>
    <Card>
        <SettingsRow edge="top" label={m.settings_lastSync()} value={syncedLabel}/>
        <SettingsRow edge="bottom" label={appName()} value={`v${version}`}/>
    </Card>
    {#if pending > 0}
        <p class="note">{m.settings_pending({count: pending})}</p>
    {/if}

    <div class="tail">
        <Card>
            <SettingsRow
                edge="only"
                tone="danger"
                label={m.settings_signOut()}
                onclick={() => (signOutOpen = true)}/>
        </Card>
        <p class="note">{m.settings_signOutNote()}</p>
    </div>
</div>

<Menu bind:open={lockOpen} anchor={lockAnchor} sections={lockSections} on_select={pickLock}/>

<ConfirmDialog
    bind:open={signOutOpen}
    title={m.settings_signOutTitle()}
    desc={m.settings_signOutDesc()}
    confirm={m.settings_signOut()}
    danger
    on_confirm={signOut}/>

<style>
    .page {
        display: flex;
        flex-direction: column;
        width: 100%;
        gap: 8px;
    }

    .identity {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
        padding: 4px 0 20px;
    }

    .name {
        margin: 8px 0 0;
        font-size: 22px;
        font-weight: bold;
        color: var(--text-color);
        text-align: center;
    }

    .mail {
        margin: 0;
        font-size: 13px;
        color: var(--subtitle-text-color);
        text-align: center;
    }

    .section {
        margin: 14px 0 2px;
        padding-inline: 4px;
        font-size: 17px;
        font-weight: 600;
        color: var(--text-color);
    }

    .note {
        margin: 8px 0 0;
        padding-inline: 4px;
        font-size: 12px;
        line-height: 1.4;
        color: var(--hint-color);
    }

    .anchor {
        width: 100%;
    }

    .detached {
        margin-top: 14px;
    }

    .tail {
        margin-top: 22px;
    }

    .grow {
        display: flex;
        animation: grow 420ms cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    @keyframes grow {
        from { scale: 0.42; }
        to { scale: 1; }
    }

    @media (prefers-reduced-motion: reduce) {
        .grow {
            animation: none;
        }
    }
</style>
