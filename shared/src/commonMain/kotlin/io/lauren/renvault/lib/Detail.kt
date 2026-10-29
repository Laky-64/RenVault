package io.lauren.renvault.lib

data class RvDraft(
    val title: String = "",
    val titleHint: String = "",
    val username: String = "",
    val password: String = "",
    val websites: List<String> = emptyList(),
    val note: String = "",
    val totpKey: String? = null,
    val totpRemoved: Boolean = false,
)

fun draftOf(item: RvWebItem): RvDraft {
    val hosts = if (item.entry.website) {
        (listOf(item.entry.domain) + item.entry.domains).filter { it.isNotEmpty() }
    } else {
        emptyList()
    }

    return RvDraft(
        title = "",
        titleHint = labelOf(item),
        username = item.entry.username,
        password = "",
        websites = hosts.distinct(),
        note = item.entry.note,
        totpKey = null,
        totpRemoved = false,
    )
}

private fun filled(websites: List<String>): List<String> = websites.filter { it.isNotBlank() }

fun websitesOk(websites: List<String>): Boolean = filled(websites).all { isDomain(domainOf(it)) }

fun tidyWebsites(websites: List<String>): List<String> =
    filled(websites).map(::domainOf).filter { it.isNotEmpty() }.distinct()

fun titleOf(draft: RvDraft): String = draft.title.trim().ifEmpty { draft.titleHint }

fun draftOf(item: RvPasskeyItem): RvDraft = RvDraft(
    titleHint = item.entry.title,
    username = item.entry.username,
    note = item.entry.note,
    websites = (listOf(item.entry.relyingParty) + item.entry.sites.orEmpty()).distinct(),
)

fun canSave(draft: RvDraft): Boolean {
    if (!websitesOk(draft.websites)) return false
    if (tidyWebsites(draft.websites).isEmpty() && titleOf(draft).isEmpty()) return false
    return draft.password.isNotEmpty() || draft.username.trim().isNotEmpty()
}
