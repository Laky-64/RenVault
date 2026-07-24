<script lang="ts">
    import {untrack} from "svelte";
    import {m} from "../paraglide/messages";

    let {
        length = 6,
        value = $bindable(''),
        disabled = false,
        error = 0,
        autofocus = true,
        onComplete,
    }: {
        length?: number;
        value?: string;
        disabled?: boolean;
        error?: number;
        autofocus?: boolean;
        onComplete?: (code: string) => void;
    } = $props();

    let input: HTMLInputElement | undefined = $state();
    let focused = $state(false);
    let shaking = $state(false);

    const chars = $derived(Array.from({length}, (_, i) => value[i] ?? ''));
    const active = $derived(Math.min(value.length, length - 1));
    const full = $derived(value.length === length);

    function clean(raw: string): string {
        return raw.replace(/\D/g, '').slice(0, length);
    }

    function onInput() {
        if (!input) return;
        value = clean(input.value);
        input.value = value;
        caretToEnd();
    }

    function caretToEnd() {
        input?.setSelectionRange(value.length, value.length);
    }

    $effect(() => {
        const v = value;
        if (input && input.value !== v) {
            input.value = v;
            input.setSelectionRange(v.length, v.length);
        }
    });

    let delivered = $state('');
    $effect(() => {
        if (!full || disabled) {
            if (!full) untrack(() => (delivered = ''));
            return;
        }
        const code = value;
        untrack(() => {
            if (delivered === code) return;
            delivered = code;
            onComplete?.(code);
        });
    });

    $effect(() => {
        if (error <= 0) return;
        error;
        untrack(() => {
            shaking = false;
            requestAnimationFrame(() => {
                shaking = true;
                setTimeout(() => (shaking = false), 420);
            });
        });
    });

    $effect(() => {
        if (autofocus && input && !disabled) input.focus({preventScroll: true});
    });
</script>

<div class="wrap" class:shaking class:disabled>
    {#each chars as char, i (i)}
        <div
            class="box"
            class:filled={char !== ''}
            class:active={focused && !disabled && i === active && !full}
            class:complete={full}
        >
            {#if char}
                <span class="digit">{char}</span>
            {/if}
        </div>
    {/each}

    <input
        bind:this={input}
        class="capture"
        type="text"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength={length}
        disabled={disabled}
        aria-label={m.code_inputLabel()}
        oninput={onInput}
        onfocus={() => (focused = true)}
        onblur={() => (focused = false)}
        onclick={caretToEnd}
        onkeyup={caretToEnd}
        onselect={caretToEnd}
    />
</div>

<style>
    .wrap {
        position: relative;
        display: flex;
        gap: 10px;
    }

    .wrap.disabled {
        opacity: 0.5;
    }

    .capture {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        opacity: 0;
        border: 0;
        padding: 0;
        background: none;
        color: transparent;
        caret-color: transparent;
        font-size: 16px;
        cursor: text;
    }

    .capture:focus {
        outline: none;
    }

    .box {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 46px;
        height: 46px;
        border-radius: 12px;
        background: color-mix(in srgb, var(--text-color) 7%, transparent);
        border: 2px solid color-mix(in srgb, var(--text-color) 7%, transparent);
        transition:
            box-shadow 180ms ease,
            background 180ms ease,
            transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
    }

    .box.filled {
        background: color-mix(in srgb, var(--text-color) 10%, transparent);
    }

    .box.active {
        border: 2px solid var(--button-color);
        transform: translateY(-2px);
    }

    .box.complete {
        border: 2px solid var(--button-color);
    }

    .digit {
        font-size: 24px;
        font-weight: 600;
        color: var(--text-color);
        font-variant-numeric: tabular-nums;
        animation: pop 260ms cubic-bezier(0.22, 1, 0.36, 1);
    }

    .wrap.shaking {
        animation: shake 400ms ease;
    }

    .wrap.shaking .box {
        border: 2px solid var(--destructive-text-color);
    }

    @keyframes pop {
        from {
            opacity: 0;
            scale: 0.4;
        }
        to {
            opacity: 1;
            scale: 1;
        }
    }

    @keyframes shake {
        0%, 100% { translate: 0; }
        15% { translate: -7px; }
        30% { translate: 6px; }
        45% { translate: -4px; }
        60% { translate: 3px; }
        80% { translate: -1px; }
    }

    @media (prefers-reduced-motion: reduce) {
        .box,
        .digit,
        .wrap.shaking {
            animation: none;
            transition: none;
        }

        .box.active {
            transform: none;
        }
    }
</style>
