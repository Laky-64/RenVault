package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberClipboardCopy(sensitive: Boolean): (String) -> Unit = remember {
    { text -> UIPasteboard.generalPasteboard.string = text }
}
