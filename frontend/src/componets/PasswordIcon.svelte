<script lang="ts">
    import type {ItemIcon} from "../lib/items";

    const {
        icon,
        width = '38px',
    } : {
        icon: ItemIcon;
        width?: string;
    } = $props();

    const src = $derived(
        icon.source === 'favicon' && icon.domain
            ? `/icons?src=${encodeURIComponent(icon.domain)}`
            : undefined,
    );
    const letter = $derived(
        icon.source === 'favicon' ? (icon.fallback.trim()[0] ?? '?').toUpperCase() : '',
    );

    let failedSrc: string | undefined = $state();
    const failed = $derived(src !== undefined && failedSrc === src);
    let loadedSrc: string | undefined = $state();
    const loaded = $derived(src !== undefined && loadedSrc === src);

    const reduced = typeof window !== 'undefined'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function reveal(forSrc: string | undefined) {
        if (forSrc === undefined) return;
        if (reduced) {
            loadedSrc = forSrc;
            return;
        }
        requestAnimationFrame(() => requestAnimationFrame(() => {
            if (src === forSrc) loadedSrc = forSrc;
        }));
    }
</script>

{#if icon.source === 'glyph'}
    <div style="width: {width};" class="icon glyph">
        <svg viewBox="0 -960 960 960" width="60%" height="60%" fill="currentColor" aria-hidden="true">
            {#if icon.name === 'wifi'}
                <path d="M409-149q-29-29-29-71t29-71q29-29 71-29t71 29q29 29 29 71t-29 71q-29 29-71 29t-71-29Zm213.5-387Q690-512 745-470q20 15 20.5 39.5T748-388q-17 17-42 17.5T661-384q-38-26-84-41t-97-15q-51 0-97 15t-84 41q-20 14-45 13t-42-18q-17-18-17-42.5t20-39.5q55-42 122.5-65.5T480-560q75 0 142.5 24Zm93-223Q826-718 914-643q20 17 21 42t-17 43q-17 17-42 17.5T831-556q-72-59-161.5-91.5T480-680q-100 0-189.5 32.5T129-556q-20 16-45 15.5T42-558q-18-18-17-43t21-42q88-75 198.5-116T480-800q125 0 235.5 41Z"/>
            {/if}
        </svg>
    </div>
{:else}
    <div style="width: {width};font-size: calc(calc({width} * 52) / 100);" class="icon empty">
        {letter}
        {#if src && !failed}
            {#key src}
                <!--suppress HtmlDeprecatedAttribute -->
                <img
                    class="favicon"
                    class:loaded
                    src="{src}"
                    alt=""
                    onload={() => reveal(src)}
                    onerror={() => (failedSrc = src)}
                />
            {/key}
        {/if}
    </div>
{/if}

<style>
    .icon {
        display: flex;
        align-items: center;
        justify-content: center;
        aspect-ratio: 1/1;
        flex-shrink: 0;
        border-radius: 8px;
        align-self: center;
        margin-block: 8px;
        pointer-events: none;
        background: color-mix(in srgb, var(--text-color) 7%, transparent);
        overflow: hidden;
    }

    .icon.empty {
        position: relative;
        background: color-mix(in srgb, var(--text-color) 13%, transparent);
        color: color-mix(in srgb, var(--text-color) 45%, transparent);
        font-weight: 500;
    }

    .favicon {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        border-radius: inherit;
        background: var(--section-bg-color, var(--secondary-bg-color));
        opacity: 0;
        transition: opacity 220ms ease;
    }

    .favicon.loaded {
        opacity: 1;
    }

    @media (prefers-reduced-motion: reduce) {
        .favicon {
            transition: none;
        }
    }

    .icon.glyph {
        background: color-mix(in srgb, var(--text-color) 13%, transparent);
        color: color-mix(in srgb, var(--text-color) 45%, transparent);
    }
</style>