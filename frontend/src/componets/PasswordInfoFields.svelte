<script lang="ts">
    import type {Field} from "./PasswordInfoFields";

    const {
        fields,
    } : {
        fields: Field[]
    } = $props();
</script>

<div class="field-list">
    {#each fields as field}
        <div class="field">
            <p class="name">{field.name}</p>
            {#if field.sensitive}
                <p class="value masked" aria-hidden="true">{'•'.repeat(field.value.length)}</p>
            {:else}
                <p class="value">{field.value}</p>
            {/if}
        </div>
    {/each}
</div>

<style>
    .field-list {
        display: flex;
        flex-direction: column;
        width: 100%;
    }

    .field {
        position: relative;
        display: flex;
        width: 100%;
        padding-block: 15px;
    }

    .field:not(:last-child)::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 1px;
        background: color-mix(in srgb, var(--text-color) 12%, transparent);
    }

    .name {
        color: var(--text-color);
        margin: 0;
        font-size: 15px;
    }

    .value {
        color: var(--hint-color);
        margin: 0 0 0 auto;
        font-size: 15px;
    }

    .masked {
        font-weight: bold;
        font-size: 25px;
        line-height: 15px;
        letter-spacing: -0.05em;
    }
</style>