<script lang="ts">
    const {size = 50}: {size?: number} = $props();
</script>

<div class="loader">
    <svg style="width: {size}px; height: {size}px" class="circular" viewBox="25 25 50 50" aria-hidden="true">
        <circle class="path" cx="50" cy="50" r="20" fill="none" stroke-width="4"/>
    </svg>
</div>

<style>
    .loader > .circular {
        animation: rotate 2s linear infinite;
    }

    .path {
        stroke-dasharray: 89, 200;
        stroke-dashoffset: 0;
        stroke: var(--hint-color);
        stroke-linecap: round;
        animation: dash 1.5s ease-in-out infinite;
    }

    @keyframes rotate {
        100% { transform: rotate(360deg); }
    }

    @keyframes dash {
        0% { stroke-dasharray: 1, 200; stroke-dashoffset: 0; }
        50% { stroke-dasharray: 89, 200; stroke-dashoffset: -35; }
        100% { stroke-dasharray: 89, 200; stroke-dashoffset: -124; }
    }
</style>
