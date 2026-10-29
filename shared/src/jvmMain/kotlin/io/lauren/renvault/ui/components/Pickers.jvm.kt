package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.lauren.renvault.bridge.FailureKind
import io.lauren.renvault.bridge.FileFilter
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.RvLockGuard
import io.lauren.renvault.bridge.VaultException
import io.lauren.renvault.ui.LocalStrings
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val latest by rememberUpdatedState(onPicked)
    return remember(scope, strings) {
        {
            scope.launch {
                RvLockGuard.expectLeave()
                val raw = try {
                    Vault.pickImage(strings.profilePhotoPick, strings.photoFilter)
                } catch (failure: VaultException) {
                    if (failure.failure.kind != FailureKind.BAD_OP) return@launch
                    withContext(Dispatchers.IO) { fromDialog() }
                }
                if (raw == null || raw.isEmpty()) return@launch
                val bytes = withContext(Dispatchers.IO) { runCatching { squarePhoto(raw) }.getOrNull() }
                bytes?.let(latest)
            }
        }
    }
}

@Composable
internal actual fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun rememberHybridLauncher(): (() -> Unit)? = null

@Composable
internal actual fun rememberFileOpener(
    title: String,
    filter: FileFilter,
    onOpened: (ByteArray) -> Unit,
): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onOpened)
    return remember(scope, title, filter) {
        {
            scope.launch {
                RvLockGuard.expectLeave()
                val raw = try {
                    Vault.pickFile(title, filter)
                } catch (failure: VaultException) {
                    if (failure.failure.kind != FailureKind.BAD_OP) return@launch
                    withContext(Dispatchers.IO) { openDialog(title, filter.extensions) }
                }
                if (raw != null && raw.isNotEmpty()) latest(raw)
            }
        }
    }
}

@Composable
internal actual fun rememberFileSaver(
    title: String,
    filter: FileFilter,
    onSaved: (Boolean) -> Unit,
): ((name: String, data: ByteArray) -> Unit)? {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onSaved)
    return remember(scope, title, filter) {
        { name, data ->
            scope.launch {
                RvLockGuard.expectLeave()
                val saved = try {
                    Vault.saveFile(title, name, filter, data)
                } catch (failure: VaultException) {
                    if (failure.failure.kind != FailureKind.BAD_OP) throw failure
                    withContext(Dispatchers.IO) { saveDialog(title, name, data) }
                }
                latest(saved)
            }
        }
    }
}

private fun openDialog(title: String, extensions: List<String>): ByteArray? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        if (extensions.isNotEmpty()) {
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in extensions }
        }
        isVisible = true
    }
    val name = dialog.file ?: return null
    return runCatching { File(dialog.directory, name).readBytes() }.getOrNull()
}

private fun saveDialog(title: String, suggested: String, data: ByteArray): Boolean {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
        file = suggested
        isVisible = true
    }
    val name = dialog.file ?: return false
    File(dialog.directory, name).writeBytes(data)
    return true
}

private fun fromDialog(): ByteArray? {
    val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in PhotoTypes }
        isVisible = true
    }
    val name = dialog.file ?: return null
    return runCatching { File(dialog.directory, name).readBytes() }.getOrNull()
}

private fun squarePhoto(raw: ByteArray): ByteArray {
    val source = ImageIO.read(raw.inputStream()) ?: error("unreadable image")
    val side = minOf(source.width, source.height)
    val square = source.getSubimage((source.width - side) / 2, (source.height - side) / 2, side, side)
    val scaled = BufferedImage(PhotoSize, PhotoSize, BufferedImage.TYPE_INT_RGB)
    scaled.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        drawImage(square, 0, 0, PhotoSize, PhotoSize, null)
        dispose()
    }
    return ByteArrayOutputStream().use { out ->
        ImageIO.write(scaled, "jpg", out)
        out.toByteArray()
    }
}

private val PhotoTypes = setOf("jpg", "jpeg", "png")
