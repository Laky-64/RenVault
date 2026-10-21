package io.lauren.renvault.lib

import java.util.Locale

actual fun currentLocaleTag(): String = Locale.getDefault().language

actual fun applyLocaleTag(tag: String) {
    Locale.setDefault(Locale.forLanguageTag(tag))
}

actual fun localeDisplayName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayLanguage(locale).ifEmpty { tag }
    return name.replaceFirstChar { it.titlecase(locale) }
}
