package io.lauren.renvault.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun RvBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun RvPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float, Boolean, Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val progress by rememberUpdatedState(onProgress)
    val cancel by rememberUpdatedState(onCancel)
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { progress(it.progress, it.swipeEdge == BackEventCompat.EDGE_RIGHT, it.touchY) }
            back()
        } catch (failure: CancellationException) {
            cancel()
            throw failure
        }
    }
}

@Composable
internal actual fun screenCorner(): Dp {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0.dp
    val radius = LocalView.current.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: return 0.dp
    return with(LocalDensity.current) { radius.toDp() }
}
