package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable

internal const val ClipboardClearMs = 45_000L

@Composable
expect fun rememberClipboardCopy(sensitive: Boolean = true): (String) -> Unit
