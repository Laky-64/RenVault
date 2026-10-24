package io.lauren.renvault.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.lauren.renvault.bridge.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val os = System.getProperty("os.name").orEmpty().lowercase()

actual val screenshotBlockSupported: Boolean = os.startsWith("windows") || os.startsWith("mac")

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private var secureScreens = 0

private fun apply(on: Boolean) {
    scope.launch { runCatching { Vault.setSecure(on) } }
}

@Composable
actual fun RvSecureScreen() {
    if (!screenshotBlockSupported) return
    val blocked = RvScreenshots.blocked
    DisposableEffect(blocked) {
        if (!blocked) return@DisposableEffect onDispose {}
        if (secureScreens++ == 0) apply(true)
        onDispose {
            if (--secureScreens == 0) apply(false)
        }
    }
}
