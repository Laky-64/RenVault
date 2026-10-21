package io.lauren.renvault.lib

import java.util.prefs.Preferences

private val node: Preferences by lazy { Preferences.userRoot().node("io/lauren/renvault") }

actual fun readPreference(key: String): String? = runCatching { node.get(key, null) }.getOrNull()

actual fun writePreference(key: String, value: String) {
    runCatching {
        node.put(key, value)
        node.flush()
    }
}
