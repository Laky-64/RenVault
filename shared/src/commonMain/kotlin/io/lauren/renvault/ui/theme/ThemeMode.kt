package io.lauren.renvault.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import io.lauren.renvault.lib.readPreference
import io.lauren.renvault.lib.writePreference

enum class RvThemeMode { System, Light, Dark }

@Stable
class RvThemeController internal constructor(initial: RvThemeMode) {
    var mode by mutableStateOf(initial)
        private set

    internal var reveal by mutableStateOf<RvThemeReveal?>(null)
        private set

    fun choose(next: RvThemeMode, origin: Offset? = null) {
        if (next == mode) return
        if (origin == null) apply(next) else reveal = RvThemeReveal(next, origin)
    }

    internal fun apply(next: RvThemeMode) {
        reveal = null
        if (next == mode) return
        mode = next
        writePreference(ThemeKey, next.name)
    }
}

internal data class RvThemeReveal(val mode: RvThemeMode, val origin: Offset)

val LocalThemeController = staticCompositionLocalOf<RvThemeController> {
    error("RvThemeController not provided")
}

@Composable
fun rememberThemeController(): RvThemeController = remember {
    val stored = readPreference(ThemeKey)
    RvThemeController(RvThemeMode.entries.firstOrNull { it.name == stored } ?: RvThemeMode.System)
}

private const val ThemeKey = "theme"
