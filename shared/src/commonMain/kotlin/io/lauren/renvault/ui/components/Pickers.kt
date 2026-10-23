package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (() -> Unit)?

@Composable
internal expect fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)?

internal const val PhotoSize = 512
