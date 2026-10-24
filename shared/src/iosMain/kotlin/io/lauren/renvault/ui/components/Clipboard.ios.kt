package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberClipboardCopy(): (text: String, sensitive: Boolean) -> Unit = remember {
    { text, _ -> UIPasteboard.generalPasteboard.string = text }
}
