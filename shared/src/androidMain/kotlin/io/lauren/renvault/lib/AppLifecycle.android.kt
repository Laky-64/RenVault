package io.lauren.renvault.lib

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle

actual val leaveEvent: Lifecycle.Event = Lifecycle.Event.ON_PAUSE

@Composable
actual fun RvHideFromRecents(hidden: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity, hidden) {
        activity.setRecentsScreenshotEnabled(!hidden)
        onDispose { activity.setRecentsScreenshotEnabled(true) }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
