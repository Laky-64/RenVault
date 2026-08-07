<script lang="ts">
    import CopyValue from "./CopyValue.svelte";
    import TotpDigits from "./TotpDigits.svelte";
    import TotpRing from "./TotpRing.svelte";
    import {totpSlot} from "../lib/totp.svelte";

    const {
        label,
        load,
        period = 30,
        copied = false,
        onCopy,
    } : {
        label: string;
        load: () => Promise<string>;
        period?: number;
        copied?: boolean;
        onCopy?: () => void;
    } = $props();

    let code = $state('');

    let source: (() => Promise<string>) | null = null;
    const slot = $derived(totpSlot(period));

    $effect(() => {
        slot;
        if (load !== source) {
            source = load;
            code = '';
        }
        let alive = true;
        load()
            .then(value => {
                if (alive) code = value;
            })
            .catch(() => {
                if (alive) code = '';
            });
        return () => (alive = false);
    });
</script>

<CopyValue {label} text={code} {copied} {onCopy}>
    {#snippet face()}
        <span class="totp">
            <TotpRing {period}/>
            <TotpDigits {code} source={load}/>
        </span>
    {/snippet}
</CopyValue>

<style>
    .totp {
        display: flex;
        align-items: center;
        gap: 5px;
    }
</style>
