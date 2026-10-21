package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberClipboardCopy(): (String) -> Unit = remember {
    { text -> UIPasteboard.generalPasteboard.string = text }
}
