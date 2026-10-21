package io.lauren.renvault.lib

val RvLocaleTags: List<String> = listOf("en", "it")

expect fun currentLocaleTag(): String

expect fun applyLocaleTag(tag: String)

expect fun localeDisplayName(tag: String): String

fun sortedLocaleTags(): List<String> =
    RvLocaleTags.sortedBy { localeDisplayName(it).lowercase() }

fun matchesLocale(tag: String, query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return localeDisplayName(tag).lowercase().contains(needle) || tag.lowercase().contains(needle)
}
