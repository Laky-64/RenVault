<script lang="ts">
    import type {Password, Zone} from "../componets/ZoneContainer";
    import ZoneContainer from "../componets/ZoneContainer.svelte";
    import PasswordList from "../componets/PasswordList.svelte";
    import PasswordInfo from "../componets/PasswordInfo.svelte";
    import {currentPassword, currentZone, nav, openPassword, openZone} from "../navigation.svelte";

    const icon = (host: string) => `https://icons.duckduckgo.com/ip3/${host}.ico`;
    const DEMO = [
        {name: 'Amazon', icon: icon('amazon.it')},
        {name: 'Google', icon: icon('google.com')},
        {name: 'TIM', icon: icon('tim.it')},
        {name: 'GitHub', icon: icon('github.com'), totp: 123456},
        {name: 'Netflix', icon: icon('netflix.com')},
        {name: 'Poste Italiane', icon: icon('poste.it')},
        {name: 'Spotify', icon: icon('spotify.com'), totp: 567890},
        {name: 'Reddit', icon: icon('reddit.com')},
        {name: 'Telegram', icon: icon('telegram.org')},
        {name: 'PayPal', icon: icon('paypal.com')},
        {name: 'Intranet aziendale', icon: undefined},
    ];

    const genPasswords = (count: number) => Array.from({ length: count }, (_, i) => ({
        name: DEMO[i % DEMO.length].name,
        icon: DEMO[i % DEMO.length].icon!,
        totp: DEMO[i % DEMO.length].totp,
        email: "stoats.foxes+" + i + "@example.com",
        password: "MEOW123!.",
        domains: ["meow.com", "stoat.com"]
    }));

    const zones: Zone[] = [
        {
            text: 'Tutto',
            icon: 'all',
            color: 'blue',
            passwords: genPasswords(150),
        },
        {
            text: 'Passkey',
            icon: 'passkey',
            color: 'green',
            passwords: genPasswords(10),
        },
        {
            text: 'Codici',
            icon: 'codes',
            color: 'yellow',
            passwords: genPasswords(2),
        },
        {
            text: 'Wi-Fi',
            icon: 'wifi',
            color: 'teal',
            passwords: genPasswords(3),
        },
        {
            text: 'Sicurezza',
            icon: 'security',
            color: 'red',
            passwords: genPasswords(27),
        },
        {
            text: 'Eliminate',
            icon: 'deleted',
            color: 'orange',
            passwords: genPasswords(0),
        }
    ];

    const shownZone = $derived(currentZone() ?? zones[0]);
</script>

<div class="container" class:stack={nav.narrow}>
    <div class="pane" style="transform: translateX({nav.offsetOf(0)}%)" inert={!nav.isActive(0)}>
        <ZoneContainer zones={zones} on_selected={openZone}/>
    </div>
    <div class="pane" style="transform: translateX({nav.offsetOf(1)}%)" inert={!nav.isActive(1)}>
        <PasswordList zone={shownZone} on_selected={openPassword}/>
    </div>
    <div class="pane" style="transform: translateX({nav.offsetOf(2)}%)" inert={!nav.isActive(2)}>
        <PasswordInfo icon={shownZone.icon} password={currentPassword() ?? null}/>
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