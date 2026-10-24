package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import io.lauren.renvault.bridge.FileFilter

@Composable
internal actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun rememberFileOpener(
    title: String,
    filter: FileFilter,
    onOpened: (ByteArray) -> Unit,
): (() -> Unit)? = null

@Composable
internal actual fun rememberFileSaver(
    title: String,
    filter: FileFilter,
    onSaved: (Boolean) -> Unit,
): ((name: String, data: ByteArray) -> Unit)? = null
