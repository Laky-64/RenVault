<script lang="ts">
    const {
        name,
        image,
        detail,
        selected = false,
        onclick,
    }: {
        name: string;
        image: string;
        detail?: string;
        selected?: boolean;
        onclick?: () => void;
    } = $props();

    function onKey(e: KeyboardEvent) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onclick?.();
        }
    }
</script>

<div class="row" class:selected role="button" tabindex="0" {onclick} onkeydown={onKey}>
    <img class="art" aria-hidden="true" src="{image}" alt=""/>

    <div class="text">
        <p class="name">{name}</p>
        {#if detail}<p class="detail">{detail}</p>{/if}
    </div>

    <svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px" class="chevron" fill="currentColor" aria-hidden="true">
        <path d="M504-480 348-636q-11-11-11-28t11-28q11-11 28-11t28 11l184 184q6 6 8.5 13t2.5 15q0 8-2.5 15t-8.5 13L404-268q-11 11-28 11t-28-11q-11-11-11-28t11-28l156-156Z"/>
    </svg>
</div>

<style>
    .row {
        display: flex;
        align-items: center;
        gap: 10px;
        width: 100%;
        padding: 10px 12px;
        cursor: pointer;
        transition: background 150ms ease;
    }

    .row:hover {
        background: color-mix(in srgb, var(--text-color) 5%, transparent);
    }

    .row:active {
        background: color-mix(in srgb, var(--text-color) 10%, transparent);
    }

    .art {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        flex-shrink: 0;
        border-radius: 10px;
    }

    .text {
        flex: 1;
        min-width: 0;
    }

    .name {
        margin: 0;
        font-size: 15px;
        font-weight: 500;
        color: var(--text-color);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .detail {
        margin: 2px 0 0;
        font-size: 12px;
        color: var(--subtitle-text-color);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .chevron {
        flex-shrink: 0;
        color: var(--subtitle-text-color);
    }
</style>
