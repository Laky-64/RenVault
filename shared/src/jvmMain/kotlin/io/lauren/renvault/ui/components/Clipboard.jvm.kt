package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.clipboardClearSeconds
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Timer
import kotlin.concurrent.schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val clearer = Timer("renvault-clipboard", true)
private val copier = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
actual fun rememberClipboardCopy(): (text: String, sensitive: Boolean) -> Unit = remember {
    { text, sensitive ->
        val seconds = if (sensitive) clipboardClearSeconds() else 0
        copier.launch {
            val native = runCatching { Vault.setClipboard(text, sensitive, seconds) }.isSuccess
            if (!native) awtCopy(text, seconds)
        }
    }
}

private fun awtCopy(text: String, seconds: Int) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
    if (seconds <= 0) return
    clearer.schedule(seconds * 1000L) {
        runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.getData(DataFlavor.stringFlavor) == text) {
                clipboard.setContents(StringSelection(""), null)
            }
        }
    }
}
