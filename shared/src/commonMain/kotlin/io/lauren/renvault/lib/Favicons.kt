package io.lauren.renvault.lib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class RvIconProvider(val key: String, val label: String) {
    Site("site", "RenVault"),
    DuckDuckGo("duckduckgo", "DuckDuckGo"),
    Google("google", "Google"),
    Bitwarden("bitwarden", "Bitwarden"),
}

object RvFavicons {
    private const val ProviderKey = "icon_provider"
    private const val EnabledKey = "site_icons"
    private const val OffKey = "off"

    private var loaded = false
    private var chosen by mutableStateOf(RvIconProvider.Site)
    private var on by mutableStateOf(true)

    private fun load() {
        if (loaded) return
        loaded = true
        val saved = readPreference(ProviderKey)
        chosen = RvIconProvider.entries.firstOrNull { it.key == saved } ?: RvIconProvider.Site
        on = readPreference(EnabledKey) != OffKey && saved != OffKey
    }

    val provider: RvIconProvider
        get() {
            load()
            return chosen
        }

    val enabled: Boolean
        get() {
            load()
            return on
        }

    val key: String get() = if (enabled) provider.key else OffKey

    fun choose(next: RvIconProvider) {
        load()
        chosen = next
        writePreference(ProviderKey, next.key)
    }

    fun setEnabled(value: Boolean) {
        load()
        on = value
        writePreference(EnabledKey, if (value) "on" else OffKey)
    }
}
