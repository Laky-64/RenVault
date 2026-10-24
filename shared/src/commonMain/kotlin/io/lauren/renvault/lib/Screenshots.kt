package io.lauren.renvault.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object RvScreenshots {
    private const val Key = "block_screenshots"

    private var loaded = false
    private var state by mutableStateOf(true)

    val blocked: Boolean
        get() {
            if (!loaded) {
                loaded = true
                state = readPreference(Key) != "off"
            }
            return state
        }

    fun setBlocked(on: Boolean) {
        loaded = true
        state = on
        writePreference(Key, if (on) "on" else "off")
    }
}

expect val screenshotBlockSupported: Boolean

@Composable
expect fun RvSecureScreen()
