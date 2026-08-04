<script lang="ts">
    import type {Snippet} from "svelte";
    import MorphSurface from "./MorphSurface.svelte";
    import {centred, sheet} from "../lib/morph";
    import {BAR_HEIGHT} from "../lib/layout";
    import {isCompact} from "../lib/navigation.svelte";

    let {
        open = $bindable(false),
        active = $bindable(false),
        anchor,
        seed,
        surface = 'panel-surface',
        fluid = false,
        children,
    } : {
        open?: boolean;
        active?: boolean;
        anchor?: HTMLElement;
        seed?: Snippet;
        surface?: string;
        fluid?: boolean;
        children: Snippet<[boolean]>;
    } = $props();

    const stack = $derived(isCompact());
    const place = $derived(stack ? sheet(BAR_HEIGHT + 20) : centred());
</script>

<MorphSurface
    bind:open
    bind:active
    {anchor}
    {place}
    {seed}
    {surface}
    dim
    fluid={fluid && !stack}
    mode={stack ? 'zoom' : 'box'}
    openMs={stack ? undefined : 400}
    closeMs={stack ? undefined : 350}
    radius={stack ? '22px 22px 0 0' : 22}
    contentAnchor={stack ? 'top-start' : 'center'}
>
    {@render children(stack)}
</MorphSurface>
