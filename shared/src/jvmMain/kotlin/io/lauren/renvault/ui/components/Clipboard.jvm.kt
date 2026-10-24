package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lauren.renvault.lib.clipboardClearSeconds
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Timer
import kotlin.concurrent.schedule

private val clearer = Timer("renvault-clipboard", true)

@Composable
actual fun rememberClipboardCopy(): (text: String, sensitive: Boolean) -> Unit = remember {
    { text, sensitive ->
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
        val delay = clipboardClearSeconds() * 1000L
        if (sensitive && delay > 0) {
            clearer.schedule(delay) {
                runCatching {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    if (clipboard.getData(DataFlavor.stringFlavor) == text) {
                        clipboard.setContents(StringSelection(""), null)
                    }
                }
            }
        }
    }
}
