package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Timer
import kotlin.concurrent.schedule

private val clearer = Timer("renvault-clipboard", true)

@Composable
actual fun rememberClipboardCopy(sensitive: Boolean): (String) -> Unit = remember(sensitive) {
    { text ->
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
        if (sensitive) {
            clearer.schedule(ClipboardClearMs) {
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
