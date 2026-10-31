package io.lauren.renvault.lib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object RvFavicons {
    private const val Key = "site_icons"

    private var loaded = false
    private var state by mutableStateOf(true)

    val enabled: Boolean
        get() {
            if (!loaded) {
                loaded = true
                state = readPreference(Key) != "off"
            }
            return state
        }

    fun setEnabled(on: Boolean) {
        loaded = true
        state = on
        writePreference(Key, if (on) "on" else "off")
    }
}
