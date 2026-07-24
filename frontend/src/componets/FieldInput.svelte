<script lang="ts">
    let {
        value = $bindable(''),
        placeholder,
        type = 'text',
        autocomplete,
        disabled = false,
        reveal = false,
        onEnter,
    }: {
        value?: string;
        placeholder: string;
        type?: 'text' | 'email' | 'password';
        autocomplete?: HTMLInputElement['autocomplete'];
        disabled?: boolean;
        reveal?: boolean;
        onEnter?: () => void;
    } = $props();

    let shown = $state(false);
    let field: HTMLInputElement | undefined = $state();

    // noinspection JSUnusedGlobalSymbols
    export function focus() {
        field?.focus({preventScroll: true});
    }

    const effective = $derived(type === 'password' && shown ? 'text' : type);
    const masked = $derived(type === 'password' && !shown && value.length > 0);

    function onKey(e: KeyboardEvent) {
        if (e.key === 'Enter') onEnter?.();
    }
</script>

<div class="row" class:disabled>
    <input
        bind:this={field}
        class:masked
        {placeholder}
        {disabled}
        {autocomplete}
        type={effective}
        bind:value
        onkeydown={onKey}
    />

    {#if reveal && type === 'password'}
        <button
            class="reveal"
            type="button"
            tabindex="-1"
            aria-label={shown ? 'Hide Password' : 'Show Password'}
            onclick={() => (shown = !shown)}
        >
            <svg viewBox="0 -960 960 960" width="20" height="20" fill="currentColor" aria-hidden="true">
                {#if shown}
                    <path d="M607.5-372.5Q660-425 660-500t-52.5-127.5Q555-680 480-680t-127.5 52.5Q300-575 300-500t52.5 127.5Q405-320 480-320t127.5-52.5Zm-204-51Q372-455 372-500t31.5-76.5Q435-608 480-608t76.5 31.5Q588-545 588-500t-31.5 76.5Q525-392 480-392t-76.5-31.5ZM235.5-272Q125-344 61-462q-5-9-7.5-18.5T51-500q0-10 2.5-19.5T61-538q64-118 174.5-190T480-800q134 0 244.5 72T899-538q5 9 7.5 18.5T909-500q0 10-2.5 19.5T899-462q-64 118-174.5 190T480-200q-134 0-244.5-72Z"/>
                {:else}
                    <path d="M607-627q29 29 42.5 66t9.5 76q0 15-11 25.5T622-449q-15 0-25.5-10.5T586-485q5-26-3-50t-25-41q-17-17-41-26t-51-4q-15 0-25.5-11T430-643q0-15 10.5-25.5T466-679q38-4 75 9.5t66 42.5Zm-127-93q-19 0-37 1.5t-36 5.5q-17 3-30.5-5T358-742q-5-16 3.5-31t24.5-18q23-5 46.5-7t47.5-2q137 0 250.5 72T904-534q4 8 6 16.5t2 17.5q0 9-1.5 17.5T905-466q-18 40-44.5 75T802-327q-12 11-28 9t-26-16q-10-14-8.5-30.5T753-392q24-23 44-50t35-58q-50-101-144.5-160.5T480-720Zm0 520q-134 0-245-72.5T60-463q-5-8-7.5-17.5T50-500q0-10 2-19t7-18q20-40 46.5-76.5T166-680l-83-84q-11-12-10.5-28.5T84-820q11-11 28-11t28 11l680 680q11 11 11.5 27.5T820-84q-11 11-28 11t-28-11L624-222q-35 11-71 16.5t-73 5.5ZM222-624q-29 26-53 57t-41 67q50 101 144.5 160.5T480-280q20 0 39-2.5t39-5.5l-36-38q-11 3-21 4.5t-21 1.5q-75 0-127.5-52.5T300-500q0-11 1.5-21t4.5-21l-84-82Zm319 93Zm-151 75Z"/>
                {/if}
            </svg>
        </button>
    {/if}
</div>

<style>
    .row {
        display: flex;
        align-items: center;
        width: 100%;
    }

    .row.disabled {
        opacity: 0.5;
    }

    input {
        flex: 1;
        min-width: 0;
        height: 46px;
        padding: 0 15px;
        border: 0;
        background: none;
        font-size: 15px;
        font-weight: 500;
        color: var(--text-color);
        caret-color: var(--button-color);
    }

    /*noinspection CssNonIntegerLengthInPixels*/
    input.masked {
        -webkit-text-stroke: 3.2px currentColor;
        letter-spacing: 0.17em;
    }

    input:focus {
        outline: none;
    }

    input::placeholder {
        font-weight: normal;
        color: var(--subtitle-text-color);
    }

    .reveal {
        display: flex;
        align-items: center;
        padding: 0 14px 0 4px;
        border: 0;
        background: none;
        color: var(--subtitle-text-color);
        cursor: pointer;
        transition: color 150ms ease;
    }

    .reveal:hover {
        color: var(--text-color);
    }
</style>
