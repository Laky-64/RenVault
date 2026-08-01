<script lang="ts">
    import {isCompact, Navigation} from "../lib/navigation.svelte";
    import { Browser } from '@wailsio/runtime';
    import DotRing from "../componets/DotRing.svelte";
    import Button from "../componets/Button.svelte";
    import FieldCard from "../componets/FieldCard.svelte";
    import FieldInput from "../componets/FieldInput.svelte";
    import {type ProfileMeta, Service} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";
    import {describeFailure} from "../lib/failure";
    import {fade} from "svelte/transition";
    import {onMount, tick} from "svelte";
    import {m} from "../paraglide/messages";
    import CodeInput from "../componets/CodeInput.svelte";
    import type {BottleInfo} from "../../bindings/github.com/Laky-64/RenVault/internal/apple";
    import DeviceRow from "../componets/DeviceRow.svelte";
    import StrengthMeter from "../componets/StrengthMeter.svelte";
    import Fold from "../componets/Fold.svelte";
    import {MIN_PASSWORD} from "../lib/strength";
    import {prefersReducedMotion} from "../lib/dom";
    import BackButton from "../componets/BackButton.svelte";

    const {onDone}: {onDone: () => void} = $props();

    type Step = {kind: 'two-factor'} | {kind: 'trusted-devices'} | {kind: 'device-passcode'} | {kind: 'master-setup'};
    const auth = new Navigation<Step>({stacked: true});

    const reduced = prefersReducedMotion();
    const VEIL = reduced ? 0 : 220;
    const SHAKE = 420;

    type Entry = 'deciding' | 'credentials' | 'master';
    let entry: Entry = $state('deciding');
    let email = $state('');
    let password = $state('');
    let code = $state('');
    let chosenDevice = $state(0);
    let busy = $state(false);
    let busyTitle = $state('');
    let busyLabel = $state('');
    let codeError = $state(0);
    let passcode = $state('');
    let passcodeError = $state('');
    let passcodeShaking = $state(false);
    let master = $state('');
    let masterRepeat = $state('');
    let masterShown = $state(false);
    let masterError = $state('');
    let masterShaking = $state(false);
    let unlock = $state('');
    let unlockError = $state('');
    let unlockShaking = $state(false);
    let error = $state('');
    let shaking = $state(false);
    let profileInfo: ProfileMeta | null = $state(null);
    let photoReady = $state(false);
    let trustedDevices: BottleInfo[] | null = $state(null);
    let passwordField: ReturnType<typeof FieldInput> | undefined = $state();
    let passcodeField: ReturnType<typeof FieldInput> | undefined = $state();
    let masterField: ReturnType<typeof FieldInput> | undefined = $state();
    let unlockField: ReturnType<typeof FieldInput> | undefined = $state();

    const stack = $derived(isCompact());

    function slotOf(kind: Step['kind']): number {
        const at = auth.levels.findIndex(l => l.kind === kind);
        return at >= 0 ? at + 1 : auth.depth + 1;
    }

    async function submitCredentials() {
        if (!canContinue || busy) return;
        error = '';
        busyTitle = m.busy_signingInTitle();
        busyLabel = m.busy_checkingCredentials();
        busy = true;
        try {
            let needsCode = await Service.StartLogin(email, password);
            if (needsCode) {
                await Service.RequestCode();
                auth.push({kind: 'two-factor'});
            } else {
                await openTrustedDevices();
            }
        } catch (e) {
            error = describeFailure(e).message;
            password = '';
            setTimeout(reportError, VEIL);
        }
        busy = false;
    }

    async function requestCode() {
        busyTitle = m.auth_2faTitle();
        busyLabel = m.busy_sendingCode();
        busy = true;
        code = '';
        try {
            await Service.RequestCode();
        } catch (e) {
            auth.back();
            error = describeFailure(e).message;
            password = '';
            setTimeout(reportError, VEIL);
        }
        busy = false;
    }

    async function submitCode(entered: string) {
        busyTitle = m.auth_2faTitle();
        busyLabel = m.busy_verifyingCode();
        busy = true;
        try {
            await Service.SubmitCode(entered);
            await openTrustedDevices();
        } catch (e) {
            codeError++;
            code = '';
        }
        busy = false;
    }

    async function openTrustedDevices() {
        profileInfo = await Service.ProfileInfo()
        trustedDevices = await Service.Bottles()
        if (trustedDevices && trustedDevices.length == 1) {
            await selectDevice(0);
        } else {
            auth.push({kind: 'trusted-devices'});
        }
    }

    const chosen = $derived.by(() => trustedDevices?.[chosenDevice]);

    const canGoBack = $derived(!busy
        && auth.top?.kind === 'device-passcode'
        && auth.levels.some(l => l.kind === 'trusted-devices'));

    function goBack() {
        passcode = '';
        passcodeError = '';
        auth.back();
    }

    async function selectDevice(index: number) {
        chosenDevice = index;
        passcode = '';
        passcodeError = '';
        auth.push({kind: 'device-passcode'});
        await tick();
        passcodeField?.focus();
    }

    async function submitPasscode() {
        if (passcode.length === 0 || busy) return;
        passcodeError = '';
        busyTitle = m.auth_devicePasscodeTitle();
        busyLabel = m.busy_verifyingPasscode();
        busy = true;
        const entered = passcode;
        passcode = '';
        try {
            await Service.RecoverBottle(chosenDevice, entered);
            master = '';
            masterRepeat = '';
            masterError = '';
            auth.push({kind: 'master-setup'});
            await tick();
            masterField?.focus();
        } catch (e) {
            passcodeError = describeFailure(e).message;
            setTimeout(reportPasscodeError, VEIL);
        }
        busy = false;
    }

    const masterTooShort = $derived(master.length > 0 && master.length < MIN_PASSWORD);
    const masterMismatch = $derived(masterRepeat.length > 0 && master !== masterRepeat);
    const masterHint = $derived(
        masterError !== '' ? masterError
            : masterTooShort ? m.auth_masterTooShort({count: MIN_PASSWORD})
                : masterMismatch ? m.auth_masterMismatch()
                    : '',
    );
    const masterReady = $derived(master.length >= MIN_PASSWORD && master === masterRepeat);

    async function submitMaster() {
        if (!masterReady || busy) return;
        masterError = '';
        busyTitle = m.busy_creatingVaultTitle();
        busyLabel = m.busy_creatingVault();
        busy = true;
        const entered = master;
        master = '';
        masterRepeat = '';
        try {
            await Service.FinishSetup(entered);
            onDone();
            return;
        } catch (e) {
            masterError = describeFailure(e).message;
            setTimeout(reportMasterError, VEIL);
        }
        busy = false;
    }

    async function submitUnlock() {
        if (unlock.length === 0 || busy) return;
        unlockError = '';
        busyTitle = m.busy_openingVaultTitle();
        busyLabel = m.busy_openingVault();
        busy = true;
        const entered = unlock;
        unlock = '';
        try {
            await Service.UnlockWithPassword(entered);
            onDone(); // keep the veil up: App.svelte swaps the screen underneath
            return;
        } catch (e) {
            unlockError = describeFailure(e).message;
            setTimeout(reportUnlockError, VEIL);
        }
        busy = false;
    }

    function revealPhoto() {
        if (reduced) {
            photoReady = true;
            return;
        }
        requestAnimationFrame(() => requestAnimationFrame(() => (photoReady = true)));
    }

    function shake(set: (on: boolean) => void) {
        if (reduced) return;
        set(false);
        requestAnimationFrame(() => {
            set(true);
            setTimeout(() => set(false), SHAKE);
        });
    }

    function reportError() {
        passwordField?.focus();
        shake(on => (shaking = on));
    }

    function reportPasscodeError() {
        passcodeField?.focus();
        shake(on => (passcodeShaking = on));
    }

    function reportMasterError() {
        masterField?.focus();
        shake(on => (masterShaking = on));
    }

    function reportUnlockError() {
        unlockField?.focus();
        shake(on => (unlockShaking = on));
    }

    const canContinue = $derived(email.trim().length > 0 && password.length > 0);

    let alive = true;
    onMount(() => {
        void decide();
        return () => (alive = false);
    });

    function raiseVeil() {
        busyTitle = m.busy_openingVaultTitle();
        busyLabel = m.busy_openingVault();
        busy = true;
    }

    async function decide() {
        const slow = setTimeout(raiseVeil, 200);
        try {
            if (await Service.Unlocked()) {
                if (!alive) return;
                raiseVeil();
                onDone();
                return;
            }
            if (!alive) return;
            entry = await Service.Configured() ? 'master' : 'credentials';
            if (!alive) return;
            if (entry === 'master') {
                await tick();
                unlockField?.focus();
            }
        } catch (e) {
            console.error(describeFailure(e).raw);
            entry = 'credentials';
        } finally {
            clearTimeout(slow);
        }
        busy = false;
    }
</script>

{#snippet appleMark()}
    <svg viewBox="0 0 24 24" width="38" height="38" fill="var(--text-color)" aria-hidden="true">
        <path d="M16.36 12.72c.02-2.3 1.88-3.4 1.96-3.46-1.07-1.56-2.73-1.78-3.32-1.8-1.41-.14-2.76.83-3.48.83-.72 0-1.83-.81-3-.79-1.55.02-2.98.9-3.77 2.29-1.61 2.79-.41 6.92 1.15 9.19.77 1.11 1.68 2.36 2.87 2.31 1.15-.05 1.59-.74 2.98-.74s1.78.74 3 .72c1.24-.02 2.02-1.13 2.78-2.25.88-1.29 1.24-2.54 1.26-2.6-.03-.01-2.41-.93-2.43-3.7zM14.1 5.6c.63-.77 1.06-1.83.94-2.9-.91.04-2.02.61-2.67 1.37-.58.68-1.09 1.77-.95 2.81 1.02.08 2.05-.52 2.68-1.28z"/>
    </svg>
{/snippet}

{#snippet lockMark()}
    <svg xmlns="http://www.w3.org/2000/svg" height="44px" width="44px" viewBox="0 -960 960 960" fill="var(--text-color)" aria-hidden="true">
        <path d="M240-80q-33 0-56.5-23.5T160-160v-400q0-33 23.5-56.5T240-640h40v-80q0-83 58.5-141.5T480-920q83 0 141.5 58.5T680-720v80h40q33 0 56.5 23.5T800-560v400q0 33-23.5 56.5T720-80H240Zm296.5-223.5Q560-327 560-360t-23.5-56.5Q513-440 480-440t-56.5 23.5Q400-393 400-360t23.5 56.5Q447-280 480-280t56.5-23.5ZM360-640h240v-80q0-50-35-85t-85-35q-50 0-85 35t-35 85v80Z"/>
    </svg>
{/snippet}

{#snippet termsAndConditions()}
    <svg width="28" height="22" viewBox="0 0 150 120" xmlns="http://www.w3.org/2000/svg">
        <g fill="var(--button-color)" fill-rule="evenodd">
            <path d="M12.013 21c0-11.61 8.52-21 21-21 11.01 0 18.933 7.32 20.64 17.01.24 1.29.36 2.61.36 3.99 0 11.61-8.52 21-21 21-.84 0-1.65-.03-2.433-.15C19.423 40.74 12.013 31.83 12.013 21"/><path d="M138.013 21c0-11.61-8.52-21-21-21-11.01 0-18.93 7.32-20.64 17.01-.24 1.29-.36 2.61-.36 3.99 0 11.61 8.52 21 21 21 .84 0 1.65-.03 2.43-.15 11.16-1.11 18.57-10.02 18.57-20.85m11.982 96.032c0 1.65-1.347 2.967-2.997 2.967H104.79a2.557 2.557 0 01-2.562-2.73c.195-2.96.327-6.972.327-9.558 0-2.547-.12-5.994-.507-8a46.062 46.062 0 01-6.21 3.566c-.69.36-1.407.657-2.13.957-.687.3-1.38.57-2.097.84-.75.3-1.53.54-2.307.78-.663.21-1.323.39-2.01.567-.453.12-.87.24-1.323.333-.597.15-1.167.27-1.767.39-1.17.207-2.37.417-3.6.537-.36.06-.75.09-1.14.12-.22-3.585-.114-6.864-.033-8.715a1.484 1.484 0 00-1.5-1.563h-4.416A1.519 1.519 0 0172 96.035a59.796 59.796 0 01.24-6.126c.9.09 1.857.12 2.787.12.96 0 1.89-.03 2.82-.12.087 0 .18-.03.237-.03 3.78-.39 7.347-1.47 10.59-3.147.237-.12.447-.21.657-.33.42-.21.84-.45 1.227-.72a33.016 33.016 0 003.363-2.337c.267-.24.537-.48.807-.72 6.537-6.144 7.917-14.745 10.287-21.996 2.337-7.254 5.697-13.158 16.07-14.058 28.999-2.457 28.909 58.653 28.909 70.461" fill-opacity=".5"/><path d="M79.495 107.8c-.39.06-.783.09-1.2.09-.303.03-.63.06-.963.06h-.06c-.72.03-1.443.06-2.193.06h-.027c-.75 0-1.473-.03-2.193-.06h-.06c-.51 0-.993-.06-1.473-.09-.15 0-.27-.03-.42-.03-.09 0-.18-.03-.27-.03-.39-.03-.783-.06-1.143-.12-1.23-.12-2.433-.33-3.603-.54-.6-.12-1.17-.24-1.77-.39-.648-.15-1.245-.302-1.917-.453-1.39-.314-2.736-.8-4.056-1.328-.612-.246-1.224-.46-1.806-.735-.66-.27-1.32-.567-1.983-.897-.6-.273-1.23-.57-1.83-.93-.543-.27-1.053-.54-1.533-.84-.51-.3-1.023-.6-1.503-.897-.48-.303-.99-.63-1.47-.96-.39 2.007-.51 5.454-.51 8 0 2.56.129 6.52.32 9.469a2.642 2.642 0 01-2.642 2.82H3.004c-1.653 0-3.003-1.32-3.003-2.968 0-11.808-.09-72.888 28.92-70.46h.03c10.332.867 13.695 6.714 16.038 13.875 2.58 7.794 3.936 17.145 11.742 23.379.51.39 1.02.78 1.563 1.106.36.27.72.48 1.11.72.51.33 1.05.63 1.623.9a29.485 29.485 0 0011.232 3.352 59.914 59.914 0 00-.24 6.131 1.514 1.514 0 001.515 1.485h4.428a1.49 1.49 0 011.5 1.566c-.084 1.852-.19 5.128.033 8.716"/>
        </g>
    </svg>
    <p class="policy-info">
        {m.auth_privacyNotice()}
        <button onclick={() => Browser.OpenURL("https://www.apple.com/it/legal/privacy/data/it/apple-id/")}>{m.auth_privacyLink()}</button>
    </p>
{/snippet}

{#snippet avatar()}
    <div class="face">
        <svg viewBox="0 -960 960 960" width="58" height="58" aria-hidden="true">
            <circle cx="480" cy="-480" r="480" fill="color-mix(in srgb, var(--text-color) 14%, transparent)"/>
            <path
                transform="translate(480 -480) scale(0.86) translate(-480 480)"
                fill="color-mix(in srgb, var(--text-color) 45%, transparent)"
                d="M367-527q-47-47-47-113t47-113q47-47 113-47t113 47q47 47 47 113t-47 113q-47 47-113 47t-113-47ZM160-240v-32q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v32q0 33-23.5 56.5T720-160H240q-33 0-56.5-23.5T160-240Z"
            />
        </svg>
        {#if profileInfo?.hasPhoto}
            <img
                class="profile"
                class:shown={photoReady}
                src="/profile"
                alt=""
                onload={revealPhoto}
            />
        {/if}
    </div>
{/snippet}

<div class="flow" class:stack>
    {#if entry !== 'deciding'}
        <div class="step" style="transform: translateX({auth.offsetOf(0)}%)" inert={!auth.isActive(0)}>
            <div class="body">
                {#if entry === 'master'}
                    <DotRing size={130}>{@render lockMark()}</DotRing>
                    <p class="title">{m.auth_unlockTitle()}</p>
                    <div class="fields" class:shaking={unlockShaking}>
                        <FieldCard invalid={unlockError !== ''}>
                            <FieldInput
                                bind:this={unlockField}
                                bind:value={unlock}
                                placeholder={m.auth_masterPlaceholder()}
                                type="password"
                                autocomplete="current-password"
                                reveal
                                disabled={busy}
                                onEnter={submitUnlock}
                            />
                        </FieldCard>
                        <Fold open={unlockError !== ''}>
                            <p class="error" role="alert" aria-live="polite">{unlockError}</p>
                        </Fold>
                    </div>
                    <p class="bottom-desc">{m.auth_unlockHint()}</p>
                    <div class="actions">
                        <Button variant="primary" block disabled={unlock.length === 0} onclick={submitUnlock}>
                            {m.auth_unlockAction()}
                        </Button>
                    </div>
                {:else}
                    <DotRing size={130}>{@render appleMark()}</DotRing>
                    <p class="title">{m.auth_signInTitle()}</p>
                    <p class="desc">{m.auth_signInHint()}</p>
                    <div class="fields" class:shaking>
                        <FieldCard invalid={error !== ''}>
                            <FieldInput
                                bind:value={email}
                                placeholder={m.auth_emailPlaceholder()}
                                type="email"
                                autocomplete="username"
                                disabled={busy}
                            />
                            <FieldInput
                                bind:this={passwordField}
                                bind:value={password}
                                placeholder={m.auth_passwordPlaceholder()}
                                type="password"
                                autocomplete="current-password"
                                reveal
                                disabled={busy}
                                onEnter={submitCredentials}
                            />
                        </FieldCard>
                        <div class="error-slot" role="alert" aria-live="polite">
                            {#if error}
                                <p class="error" transition:fade={{duration: reduced ? 0 : 160}}>{error}</p>
                            {/if}
                        </div>
                    </div>
                    <div class="actions">
                        {@render termsAndConditions()}
                        <Button variant="primary" block disabled={!canContinue} onclick={submitCredentials}>
                            {m.auth_signInAction()}
                        </Button>
                    </div>
                {/if}
            </div>
        </div>
    {/if}

    <div class="step" style="transform: translateX({auth.offsetOf(slotOf('two-factor'))}%)" inert={!auth.isActive(slotOf('two-factor'))}>
        <div class="body">
            <DotRing size={130} spinDuration={26000}>{@render appleMark()}</DotRing>
            <p class="title">{m.auth_2faTitle()}</p>
            <div class="code">
                <CodeInput
                    bind:value={code}
                    error={codeError}
                    disabled={busy}
                    autofocus={auth.top?.kind === 'two-factor'}
                    onComplete={submitCode}
                />
            </div>
            <p class="bottom-desc">{m.auth_2faHint()}</p>
            <div class="links">
                <button class="link" onclick={requestCode}>{m.auth_2faResendAction()}</button>
            </div>
        </div>
    </div>

    <div class="step" style="transform: translateX({auth.offsetOf(slotOf('trusted-devices'))}%)" inert={!auth.isActive(slotOf('trusted-devices'))}>
        <div class="body">
            <DotRing size={130}>{@render avatar()}</DotRing>
            <p class="title">{m.auth_deviceSelectionTitle()}</p>
            <div class="fields">
                <FieldCard>
                    {#each trustedDevices ?? [] as device, i (device.name)}
                        <DeviceRow
                            image={device.imageURL}
                            name={device.name}
                            detail={device.shortModel}
                            onclick={() => selectDevice(i)}
                        />
                    {/each}
                </FieldCard>
            </div>
            <p class="bottom-desc">{m.auth_deviceSelectionHint()}</p>
        </div>
    </div>

    <div class="step" style="transform: translateX({auth.offsetOf(slotOf('device-passcode'))}%)" inert={!auth.isActive(slotOf('device-passcode'))}>
        <div class="body">
            <DotRing size={130}>{@render avatar()}</DotRing>
            <p class="title">{m.auth_devicePasscodeTitle()}</p>
            <div class="fields" class:shaking={passcodeShaking}>
                <FieldCard invalid={passcodeError !== ''}>
                    <FieldInput
                        bind:this={passcodeField}
                        bind:value={passcode}
                        placeholder={m.auth_devicePasscodePlaceholder()}
                        type="password"
                        autocomplete="off"
                        reveal
                        disabled={busy}
                        onEnter={submitPasscode}
                    />
                </FieldCard>
                <div class="error-slot" role="alert" aria-live="polite">
                    {#if passcodeError}
                        <p class="error" transition:fade={{duration: reduced ? 0 : 160}}>{passcodeError}</p>
                    {/if}
                </div>
            </div>
            <p class="bottom-desc">{m.auth_devicePasscodeHint({device: chosen?.shortModel ?? ''})}</p>
            <div class="actions">
                <Button variant="primary" block disabled={passcode.length === 0} onclick={submitPasscode}>
                    {m.auth_continueAction()}
                </Button>
            </div>
        </div>
    </div>

    <div class="step" style="transform: translateX({auth.offsetOf(slotOf('master-setup'))}%)" inert={!auth.isActive(slotOf('master-setup'))}>
        <div class="body">
            <DotRing size={130}>{@render lockMark()}</DotRing>
            <p class="title">{m.auth_masterSetupTitle()}</p>
            <div class="fields" class:shaking={masterShaking}>
                <FieldCard invalid={masterError !== '' || masterMismatch}>
                    <FieldInput
                        bind:this={masterField}
                        bind:value={master}
                        placeholder={m.auth_masterPlaceholder()}
                        type="password"
                        autocomplete="new-password"
                        reveal
                        bind:shown={masterShown}
                        disabled={busy}
                    />
                    <FieldInput
                        bind:value={masterRepeat}
                        placeholder={m.auth_masterRepeatPlaceholder()}
                        type="password"
                        autocomplete="new-password"
                        bind:shown={masterShown}
                        disabled={busy}
                        onEnter={submitMaster}
                    />
                </FieldCard>
                <StrengthMeter value={master}/>
                <Fold open={masterHint !== ''}>
                    <p class="error" role="alert" aria-live="polite">{masterHint}</p>
                </Fold>
            </div>
            <p class="bottom-desc">{m.auth_masterSetupHint()}</p>
            <div class="actions">
                <Button variant="primary" block disabled={!masterReady} onclick={submitMaster}>
                    {m.auth_continueAction()}
                </Button>
            </div>
        </div>
    </div>

    <BackButton shown={canGoBack} onclick={goBack}/>

    {#if busy}
        <div class="busy" role="status" aria-live="polite" transition:fade={{duration: VEIL}}>
            <DotRing size={150} spinDuration={7000}>{@render avatar()}</DotRing>
            <p class="title">{busyTitle}</p>
            <p class="busy-label">{busyLabel}</p>
        </div>
    {/if}
</div>

<style>
    .flow {
        position: relative;
        flex: 1;
        min-height: 0;
        overflow: hidden;
    }

    .step {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        position: absolute;
        inset: 0;
        background: var(--secondary-bg-color);
        transition: transform 350ms cubic-bezier(0.32, 0.72, 0, 1);
        will-change: transform;
    }

    .body {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        flex: 1;
        min-height: 0;
        width: 100%;
        max-width: 400px;
        padding: 25px;
    }

    @media (prefers-reduced-motion: reduce) {
        .step {
            transition: none;
        }
    }

    .title {
        margin: 18px 0 0;
        font-size: 24px;
        font-weight: bold;
        color: var(--text-color);
        text-align: center;
    }

    .desc {
        margin: 6px 0 0;
        font-size: 14px;
        color: var(--hint-color);
        text-align: center;
    }

    .bottom-desc {
        margin: 12px 0 0;
        font-size: 12px;
        color: var(--hint-color);
        text-align: center;
    }

    .fields {
        width: 100%;
        margin-top: 22px;
    }

    .fields.shaking {
        animation: shake 400ms ease;
    }

    .error-slot {
        display: flex;
        justify-content: center;
        align-items: center;
    }

    .error {
        margin: 0;
        padding: 6px 4px;
        font-size: 11px;
        color: var(--destructive-text-color);
        text-align: center;
    }

    @keyframes shake {
        0%, 100% { translate: 0; }
        15% { translate: -7px; }
        30% { translate: 6px; }
        45% { translate: -4px; }
        60% { translate: 3px; }
        80% { translate: -1px; }
    }

    .stack > .step > .body > .actions {
        margin-top: auto;
    }

    .actions {
        display: flex;
        flex-direction: column;
        margin-top: 30px;
        gap: 5px;
        width: 100%;
    }

    .code {
        width: 100%;
        margin-top: 26px;
    }

    .links {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;
        margin-top: 22px;
    }

    .link {
        padding: 2px 4px;
        border: 0;
        background: none;
        font: inherit;
        font-size: 13px;
        color: var(--accent-text-color);
        cursor: pointer;
    }

    .policy-info {
        font-size: 11px;
        color: var(--hint-color);
    }

    .policy-info > button {
        border: 0;
        font: inherit;
        padding: 0;
        background: none;
        color: var(--accent-text-color);
        text-decoration: none;
        cursor: pointer;
    }

    .busy {
        position: absolute;
        inset: 0;
        z-index: 2;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 12px;
        background: var(--secondary-bg-color);
    }

    .face {
        position: relative;
        width: 58px;
        height: 58px;
    }

    .profile {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        border-radius: 50%;
        object-fit: cover;
        pointer-events: none;
        opacity: 0;
        scale: 1.12;
        transition:
            opacity 340ms ease,
            scale 460ms cubic-bezier(0.22, 1, 0.36, 1);
    }

    .profile.shown {
        opacity: 1;
        scale: 1;
    }

    .busy-label {
        margin: 0;
        font-size: 14px;
        color: var(--hint-color);
    }

    @media (prefers-reduced-motion: reduce) {
        .step,
        .busy,
        .profile,
        .fields.shaking {
            transition: none;
            animation: none;
        }
    }
</style>