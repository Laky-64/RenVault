<script lang="ts">
    import type {Snippet} from "svelte";
    import MorphSurface from "./MorphSurface.svelte";
    import {centred, sheet} from "../lib/morph";
    import {BAR_HEIGHT} from "../lib/layout";
    import {isCompact} from "../lib/navigation.svelte";

    let {
        open = $bindable(false),
        active = $bindable(false),
        surface = 'glass-surface',
        compactSheet = true,
        children,
    } : {
        open?: boolean;
        active?: boolean;
        surface?: string;
        compactSheet?: boolean;
        children: Snippet;
    } = $props();

    const asSheet = $derived(compactSheet && isCompact());
    const place = $derived(asSheet ? sheet(BAR_HEIGHT + 20) : centred());
</script>

<MorphSurface
    bind:open
    bind:active
    {place}
    {surface}
    mode="pop"
    openMs={260}
    closeMs={200}
    radius={asSheet ? '22px 22px 0 0' : 22}
    contentAnchor={asSheet ? 'top-start' : 'center'}
    fill={asSheet}
>
    {@render children()}
</MorphSurface>
