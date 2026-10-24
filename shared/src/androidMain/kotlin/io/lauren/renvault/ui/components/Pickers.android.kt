package io.lauren.renvault.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import io.lauren.renvault.bridge.FileFilter
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { squarePhoto(context, uri) }.getOrNull() }
            bytes?.let(latest)
        }
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

@Composable
internal actual fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onScanned)
    return remember(context) {
        {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(context, options)
                .startScan()
                .addOnSuccessListener { code -> code.rawValue?.let { latest(it) } }
        }
    }
}

@Composable
internal actual fun rememberFileOpener(
    title: String,
    filter: FileFilter,
    onOpened: (ByteArray) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onOpened)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            bytes?.takeIf { it.isNotEmpty() }?.let(latest)
        }
    }
    return remember(launcher, filter) {
        { launcher.launch(filter.types.ifEmpty { listOf("*/*") }.plus("application/octet-stream").distinct().toTypedArray()) }
    }
}

@Composable
internal actual fun rememberFileSaver(
    title: String,
    filter: FileFilter,
    onSaved: (Boolean) -> Unit,
): ((name: String, data: ByteArray) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onSaved)
    val pending = remember { mutableStateOf<ByteArray?>(null) }
    val mime = filter.types.firstOrNull() ?: "application/octet-stream"
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val data = pending.value
        pending.value = null
        if (uri == null || data == null) {
            latest(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) } != null
                }.getOrDefault(false)
            }
            latest(saved)
        }
    }
    return remember(launcher) {
        { name, data ->
            pending.value = data
            launcher.launch(name)
        }
    }
}

private fun squarePhoto(context: Context, uri: Uri): ByteArray {
    val source = decode(context, uri)
    val side = minOf(source.width, source.height)
    val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(square, PhotoSize, PhotoSize, true)
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, PhotoQuality, out)
        out.toByteArray()
    }
}

private fun decode(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val shortest = minOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize((shortest / (PhotoSize * 2)).coerceAtLeast(1))
        }
    }
    return context.contentResolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream) ?: error("unreadable image")
    }
}

private const val PhotoQuality = 88
