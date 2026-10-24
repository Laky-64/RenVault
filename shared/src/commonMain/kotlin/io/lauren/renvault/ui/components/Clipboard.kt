package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun rememberClipboardCopy(): (text: String, sensitive: Boolean) -> Unit
