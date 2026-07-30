<script lang="ts">
    import {m} from "../paraglide/messages";
    import Button from "./Button.svelte";
    import PasswordIcon from "./PasswordIcon.svelte";
    import type {ItemIcon} from "../lib/items";
    import {openChangePassword} from "../lib/changepw";

    const {
        icon,
        domain,
    } : {
        icon: ItemIcon;
        domain: string;
    } = $props();

    function change() {
        void openChangePassword(domain);
    }
</script>

<div class="card">
    <PasswordIcon {icon} width="50px"/>
    <p class="title">{m.security_changeTitle()}</p>
    <p class="desc">{m.security_changeDesc({domain})}</p>
    <Button padding="10px 15px" variant="primary" onclick={change}>{m.security_changeAction()}</Button>
</div>

<style>
    .card {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 100%;
        max-width: 600px;
        margin-inline: auto;
        border-radius: var(--zone-border-radius);
        background: var(--section-bg-color);
        padding-block: 10px 15px;
        padding-inline: 15px;
    }

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
