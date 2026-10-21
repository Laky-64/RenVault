package io.lauren.renvault.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService

@Composable
actual fun rememberClipboardCopy(): (String) -> Unit {
    val context = LocalContext.current

    return remember(context) {
        { text ->
            context.getSystemService<ClipboardManager>()
                ?.setPrimaryClip(ClipData.newPlainText(null, text))
        }
    }
}
