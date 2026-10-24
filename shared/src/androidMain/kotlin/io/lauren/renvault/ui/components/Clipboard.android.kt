package io.lauren.renvault.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import kotlin.random.Random

private val clearer = Handler(Looper.getMainLooper())

@Composable
actual fun rememberClipboardCopy(sensitive: Boolean): (String) -> Unit {
    val context = LocalContext.current

    return remember(context, sensitive) {
        { text ->
            val clipboard = context.getSystemService<ClipboardManager>()
            if (clipboard != null) {
                val label = "RenVault ${Random.nextLong()}"
                val clip = ClipData.newPlainText(label, text)
                if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
                clipboard.setPrimaryClip(clip)
                if (sensitive) {
                    clearer.postDelayed({ clearIfOurs(clipboard, label) }, ClipboardClearMs)
                }
            }
        }
    }
}

private fun clearIfOurs(clipboard: ClipboardManager, label: String) {
    val current = runCatching { clipboard.primaryClipDescription?.label?.toString() }.getOrNull()
    if (current != null && current != label) return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
