<script lang="ts" module>
    let seen = false;
</script>

<script lang="ts">
    import {prefersReducedMotion} from "../lib/dom";

    const {
        size = 58,
        hasPhoto = false,
        label,
    }: {
        size?: number;
        hasPhoto?: boolean;
        label?: string;
    } = $props();

    const reduced = prefersReducedMotion();
    const instant = reduced || seen;
    let ready = $state(false);

    function reveal() {
        seen = true;
        if (instant) {
            ready = true;
            return;
        }
        requestAnimationFrame(() => requestAnimationFrame(() => (ready = true)));
    }
</script>

<div class="face" style="--size: {size}px">
    <svg viewBox="0 -960 960 960" width={size} height={size}
         role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}>
        <circle cx="480" cy="-480" r="480" fill="color-mix(in srgb, var(--text-color) 14%, transparent)"/>
        <path
            transform="translate(480 -480) scale(0.86) translate(-480 480)"
            fill="color-mix(in srgb, var(--text-color) 45%, transparent)"
            d="M367-527q-47-47-47-113t47-113q47-47 113-47t113 47q47 47 47 113t-47 113q-47 47-113 47t-113-47ZM160-240v-32q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v32q0 33-23.5 56.5T720-160H240q-33 0-56.5-23.5T160-240Z"
        />
    </svg>

    {#if hasPhoto}
        <img class="profile" class:shown={ready} class:still={instant} src="/profile" alt="" onload={reveal}/>
    {/if}
</div>

<style>
    .face {
        position: relative;
        width: var(--size);
        height: var(--size);
        flex-shrink: 0;
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

    /*noinspection CssUnusedSymbol*/
    .profile.still {
        transition: none;
    }

    @media (prefers-reduced-motion: reduce) {
        .profile {
            transition: none;
        }
    }
</style>
