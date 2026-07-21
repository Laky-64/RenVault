<script lang="ts">
    import ZoneIcon from "./ZoneIcon.svelte";
    import {type Icon} from "./ZoneIcon";

    const {
        text,
        count,
        icon,
        onclick,
        accent = 'var(--tile-blue-color)',
        selected = false,
    } : {
        text: string,
        count: number,
        icon: Icon,
        onclick?: () => void,
        accent?: string;
        selected?: boolean;
    } = $props();

    function onKey(e: KeyboardEvent) {
        if ((e.key === "Enter" || e.key === " ") && onclick) {
            e.preventDefault();
            onclick();
        }
    }

    function handleClick(e: MouseEvent) {
        if (onclick) {
            e.preventDefault();
            onclick();
        }
    }
</script>

<div style="--accent: {accent}" class="container" role="button" tabindex="0" onclick={handleClick} onkeydown={onKey} class:selected>
    <div class="top-elements">
        <ZoneIcon icon={icon} filled={selected} accent={accent} />
        <p class:selected>{count}</p>
    </div>
    <p>{text}</p>
</div>

<style>
    .container {
        display: flex;
        flex-direction: column;
        overflow: hidden;
        border-radius: var(--zone-border-radius);
        background: color-mix(in srgb, var(--text-color) 7%, transparent);
        transition: background 200ms ease, box-shadow 200ms ease;
        box-sizing: border-box;
        padding: 12px;
        box-shadow: 0 0 0 transparent;
    }

    .container:focus {
        outline: none;
        box-shadow: none;
    }

    .container:hover {
        background: color-mix(in srgb, var(--text-color) 13%, transparent);
    }

    .container:active {
        background: color-mix(in srgb, var(--text-color) 20%, transparent);
        box-shadow: 0 0 20px color-mix(in srgb, var(--text-color) 20%, transparent);
    }

    .container.selected {
        background: var(--accent);
    }

    .container.selected:hover {
        background: color-mix(in srgb, var(--accent) 90%, var(--text-color));
    }

    .container.selected:active {
        background: color-mix(in srgb, var(--accent) 80%, var(--text-color));
        box-shadow: 0 0 20px  color-mix(in srgb, var(--accent) 20%, color-mix(in srgb, var(--text-color) 20%, transparent));
    }

    .top-elements {
        display: flex;
        align-items: center;
        pointer-events: none;
    }

    .top-elements > p {
        margin: 0 0 0 auto;
        font-size: 15px;
        color: var(--subtitle-text-color);
        transition: color 200ms ease;
    }

    .top-elements > p.selected {
        color: var(--text-color);
    }

    .container > p {
        margin: 8px 0 0;
        padding: 0;
        font-size: 14px;
        color: var(--text-color);
        font-weight: 500;
    }
</style>