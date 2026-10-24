package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import io.lauren.renvault.bridge.FileFilter

@Composable
internal expect fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (() -> Unit)?

@Composable
internal expect fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)?

internal const val PhotoSize = 512

@Composable
internal expect fun rememberFileOpener(
    title: String,
    filter: FileFilter,
    onOpened: (ByteArray) -> Unit,
): (() -> Unit)?

@Composable
internal expect fun rememberFileSaver(
    title: String,
    filter: FileFilter,
    onSaved: (Boolean) -> Unit,
): ((name: String, data: ByteArray) -> Unit)?
