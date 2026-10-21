package io.lauren.renvault.lib

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localizedStringForLanguageCode

actual fun currentLocaleTag(): String = NSLocale.currentLocale.languageCode

actual fun applyLocaleTag(tag: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(tag), forKey = "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}

actual fun localeDisplayName(tag: String): String {
    val locale = NSLocale(localeIdentifier = tag)
    val name = locale.localizedStringForLanguageCode(tag) ?: tag
    return name.replaceFirstChar { it.uppercaseChar() }
}
