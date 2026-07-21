<script lang="ts">
    import {type Icon, pathFor} from "./ZoneIcon";

    const {
        icon,
        accent,
        filled = false,
    } : {
        icon: Icon;
        accent: string;
        filled?: boolean;
    } = $props();
</script>

<div style="--accent: {accent}" class:filled>
    <svg xmlns="http://www.w3.org/2000/svg" height="20px" width="20px" viewBox="0 -960 960 960" fill="var(--text-color)" style="pointer-events: none">
        <path d="{pathFor(icon)}"/>
    </svg>
</div>

<style>
    div {
        display: flex;
        align-items: center;
        justify-content: center;
        transition: background 200ms ease;
        background: var(--accent);
        width: 28px;
        height: 28px;
        flex-shrink: 0;
        border-radius: 50%;
    }

    div.filled {
        background: var(--text-color);
    }

    div.filled > svg {
        transition: fill 200ms ease;
        fill: var(--text-color);
    }

    div.filled > svg {
        fill: var(--accent);
    }
</style>