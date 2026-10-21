package io.lauren.renvault.lib

data class RvNameSwap(val from: String, val to: String)

class RvNameMask(swaps: List<RvNameSwap>) {
    private val rules: Map<String, String> = swaps
        .map { RvNameSwap(it.from.trim(), it.to.trim()) }
        .filter { it.from.isNotEmpty() }
        .associate { it.from.lowercase() to it.to }

    private val pattern: Regex? = rules.keys
        .sortedByDescending { it.length }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("|") { Regex.escape(it) }
        ?.let { Regex(it, RegexOption.IGNORE_CASE) }

    val active: Boolean get() = pattern != null

    fun hides(text: String): Boolean = pattern?.containsMatchIn(text) == true

    fun mask(text: String): String {
        val regex = pattern ?: return text
        return regex.replace(text) { match ->
            val found = match.value
            casedLike(found, rules[found.lowercase()].orEmpty())
        }
    }

    companion object {
        val None = RvNameMask(emptyList())
    }
}

private fun casedLike(found: String, replacement: String): String = when {
    replacement.isEmpty() -> replacement
    found == found.lowercase() -> replacement.lowercase()
    found.length > 1 && found == found.uppercase() -> replacement.uppercase()
    found.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercaseChar() }
    else -> replacement
}
