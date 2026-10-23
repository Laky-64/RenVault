package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)? = null
