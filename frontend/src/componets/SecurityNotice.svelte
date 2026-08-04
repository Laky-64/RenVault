<script lang="ts">
    import {m} from "../paraglide/messages";
    import Button from "./Button.svelte";
    import PasswordIcon from "./PasswordIcon.svelte";
    import Card from "./Card.svelte";
    import type {ItemIcon} from "../lib/items";
    import {openChangePassword} from "../lib/changepw";

    const {
        icon,
        label,
        domain = '',
    } : {
        icon: ItemIcon;
        label: string;
        domain?: string;
    } = $props();

    function change() {
        void openChangePassword(domain);
    }
</script>

<Card padding="10px 15px 15px">
    <PasswordIcon {icon} width="50px"/>
    <p class="title">{m.security_changeTitle()}</p>
    <p class="desc">{m.security_changeDesc({domain: label})}</p>
    {#if domain}
        <Button padding="10px 15px" variant="primary" onclick={change}>{m.security_changeAction()}</Button>
    {/if}
</Card>

<style>

    .title {
        font-size: 22px;
        margin: 0 0 8px;
        font-weight: bold;
        text-align: center;
        color: var(--text-color);
    }

    .desc {
        margin: 0 0 15px;
        max-width: 420px;
        font-size: 14px;
        line-height: 1.35;
        text-align: center;
        color: var(--subtitle-text-color);
    }
</style>
