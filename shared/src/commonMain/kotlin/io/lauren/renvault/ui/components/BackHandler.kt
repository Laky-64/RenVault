package io.lauren.renvault.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

private const val PeekFraction = 0.3f

@Composable
expect fun RvBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun RvPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
)

@Composable
internal fun <S> rememberBackTransition(screen: S, previous: S?, label: String, onBack: () -> Unit): Transition<S> {
    val state = remember { SeekableTransitionState(screen) }
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(screen) {
        if (!dragging) state.animateTo(screen)
    }
    LaunchedEffect(dragging, previous) {
        val target = previous ?: return@LaunchedEffect
        if (dragging) snapshotFlow { progress }.collect { state.seekTo(it * PeekFraction, target) }
    }

    RvPredictiveBackHandler(
        enabled = previous != null,
        onProgress = {
            progress = it
            dragging = true
        },
        onCancel = {
            dragging = false
            scope.launch { state.animateTo(screen) }
        },
        onBack = {
            dragging = false
            onBack()
        },
    )
    return rememberTransition(state, label)
}

internal fun slideBetween(forward: Boolean, motion: MotionScheme, fraction: Int): ContentTransform {
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val enter = slideInHorizontally(spatial) { if (forward) it / fraction else -it / fraction } +
        fadeIn(motion.defaultEffectsSpec())
    val exit = slideOutHorizontally(spatial) { if (forward) -it / fraction else it / fraction } +
        fadeOut(motion.fastEffectsSpec())
    return if (forward) {
        enter togetherWith exit
    } else {
        enter togetherWith (exit + scaleOut(motion.defaultSpatialSpec(), targetScale = 0.92f))
    }
}
