package io.lauren.renvault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.lauren.renvault.lib.applyLocaleTag
import io.lauren.renvault.lib.currentLocaleTag
import io.lauren.renvault.lib.localeDisplayName
import io.lauren.renvault.lib.matchesLocale
import io.lauren.renvault.lib.sortedLocaleTags

@Stable
class RvLocaleController internal constructor(initial: String) {
    var current by mutableStateOf(initial)
        private set

    val choices: List<String> get() = sortedLocaleTags()

    fun nameOf(tag: String): String = localeDisplayName(tag)

    fun search(query: String): List<String> = choices.filter { matchesLocale(it, query) }

    fun choose(tag: String) {
        if (tag == current) return
        applyLocaleTag(tag)
        current = tag
    }
}

val LocalLocale = staticCompositionLocalOf<RvLocaleController> {
    error("RvLocaleController not provided")
}

@Composable
fun rememberLocaleController(): RvLocaleController = remember { RvLocaleController(currentLocaleTag()) }
