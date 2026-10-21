package io.lauren.renvault.lib

import io.lauren.renvault.bridge.PlatformStorage
import java.io.File

private fun preferenceFile(key: String): File = File(PlatformStorage.base(), "pref-$key")

actual fun readPreference(key: String): String? =
    runCatching { preferenceFile(key).readText() }.getOrNull()

actual fun writePreference(key: String, value: String) {
    runCatching { preferenceFile(key).writeText(value) }
}
