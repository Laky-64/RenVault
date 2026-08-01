<script lang="ts">
    import RevealToggle from "./RevealToggle.svelte";

    let {
        value = $bindable(''),
        placeholder,
        type = 'text',
        autocomplete,
        disabled = false,
        reveal = false,
        shown = $bindable(false),
        onEnter,
    }: {
        value?: string;
        placeholder: string;
        type?: 'text' | 'email' | 'password';
        autocomplete?: HTMLInputElement['autocomplete'];
        disabled?: boolean;
        reveal?: boolean;
        shown?: boolean;
        onEnter?: () => void;
    } = $props();
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
        class:masked-text={masked}
        {placeholder}
        {disabled}
        {autocomplete}
        type={effective}
        bind:value
        onkeydown={onKey}
    />

    {#if reveal && type === 'password'}
        <RevealToggle bind:shown/>
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

    input:focus {
        outline: none;
    }

    input::placeholder {
        font-weight: normal;
        color: var(--subtitle-text-color);
    }

</style>
