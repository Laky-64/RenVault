package io.lauren.renvault.lib

enum class RvSortField { Title, Website, Modified, Created }

private fun websiteOf(item: RvItem): String = when (item) {
    is RvWebItem -> item.entry.domain
    is RvPasskeyItem -> item.entry.relyingParty
    is RvWifiItem -> item.entry.ssid
}

private fun compare(a: RvItem, b: RvItem, field: RvSortField, strings: RvStrings): Int = when (field) {
    RvSortField.Title -> viewOf(a, strings).title.compareTo(viewOf(b, strings).title, ignoreCase = true)
    RvSortField.Website -> websiteOf(a).compareTo(websiteOf(b), ignoreCase = true)
    RvSortField.Modified -> a.modified.compareTo(b.modified)
    RvSortField.Created -> a.created.compareTo(b.created)
}

fun sortItems(
    items: List<RvItem>,
    field: RvSortField,
    ascending: Boolean,
    strings: RvStrings,
): List<RvItem> {
    val sign = if (ascending) 1 else -1
    return items.sortedWith { a, b -> sign * compare(a, b, field, strings) }
}
