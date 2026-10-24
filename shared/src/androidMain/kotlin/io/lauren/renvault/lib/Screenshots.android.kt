package io.lauren.renvault.lib

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

actual val screenshotBlockSupported: Boolean = true

private var secureScreens = 0

@Composable
actual fun RvSecureScreen() {
    val activity = LocalContext.current.findActivity() ?: return
    val blocked = RvScreenshots.blocked
    DisposableEffect(activity, blocked) {
        if (!blocked) return@DisposableEffect onDispose {}
        secureScreens++
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            secureScreens--
            if (secureScreens == 0) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
