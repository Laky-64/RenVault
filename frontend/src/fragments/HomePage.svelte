<script lang="ts">
    import type {Password, Zone} from "../componets/ZoneContainer";
    import ZoneContainer from "../componets/ZoneContainer.svelte";
    import PasswordList from "../componets/PasswordList.svelte";
    import PasswordInfo from "../componets/PasswordInfo.svelte";

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

    let selectedZone: Zone = $state(zones[0]);
    let selectedPassword: Password | null = $state(null);
</script>

<div class="container">
    <ZoneContainer zones={zones} on_selected={(zone) => {
        selectedZone = zone;
        selectedPassword = null;
    }} />
    <PasswordList zone={selectedZone} on_selected={(password) => selectedPassword = password} />
    <PasswordInfo icon={selectedZone.icon} password={selectedPassword} />
</div>

<style>
    .container {
        display: flex;
        width: 100%;
        flex: 1;
        min-height: 0;
        padding-inline: 8px;

    }
</style>