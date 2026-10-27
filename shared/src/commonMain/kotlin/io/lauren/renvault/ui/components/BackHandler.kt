package io.lauren.renvault.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private const val PeekFraction = 0.15f

private const val PeekMs = 500

private const val SettleMs = 250

private const val CancelMs = 150

internal class BackTransition<S>(val transition: Transition<S>, peeking: State<Boolean>) {
    val peeking by peeking
}

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
internal fun <S> rememberBackTransition(screen: S, previous: S?, label: String, onBack: () -> Unit): BackTransition<S> {
    val state = remember { SeekableTransitionState(screen) }
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val draggingState = remember { mutableStateOf(false) }
    var dragging by draggingState
    var settling by remember { mutableStateOf<Job?>(null) }
    var released by remember { mutableStateOf(false) }
    var gesture by remember { mutableIntStateOf(0) }
    val commands = remember { Channel<suspend () -> Unit>(Channel.UNLIMITED) }
    val current by rememberUpdatedState(screen)
    val target by rememberUpdatedState(previous)

    LaunchedEffect(commands) {
        for (command in commands) command()
    }
    LaunchedEffect(screen, dragging) {
        if (dragging) return@LaunchedEffect
        state.animateTo(screen, if (released) tween(SettleMs, easing = FastOutSlowInEasing) else null)
        released = false
    }

    fun seek(value: Float) {
        progress = value
        val id = gesture
        val destination = target ?: return
        commands.trySend { if (gesture == id && dragging) state.seekTo(value * PeekFraction, destination) }
    }

    RvPredictiveBackHandler(
        enabled = previous != null,
        onProgress = {
            settling?.cancel()
            dragging = true
            seek(it)
        },
        onCancel = {
            val id = gesture
            settling = scope.launch {
                animate(progress, 0f, animationSpec = tween(CancelMs, easing = FastOutSlowInEasing)) { value, _ -> seek(value) }
                commands.send {
                    if (gesture == id) {
                        gesture++
                        state.snapTo(current)
                        dragging = false
                    }
                }
            }
        },
        onBack = {
            settling?.cancel()
            gesture++
            released = dragging
            dragging = false
            onBack()
        },
    )
    val transition = rememberTransition(state, label)
    return remember(transition) { BackTransition(transition, draggingState) }
}

internal fun slideBetween(forward: Boolean, motion: MotionScheme, fraction: Int, peek: Boolean = false): ContentTransform {
    val spatial: FiniteAnimationSpec<IntOffset> = if (peek) tween(PeekMs, easing = LinearEasing) else motion.defaultSpatialSpec()
    val scale: FiniteAnimationSpec<Float> = if (peek) tween(PeekMs, easing = LinearEasing) else motion.defaultSpatialSpec()
    val fadeInSpec: FiniteAnimationSpec<Float> = if (peek) tween(PeekMs, easing = LinearEasing) else motion.defaultEffectsSpec()
    val fadeOutSpec: FiniteAnimationSpec<Float> = if (peek) tween(PeekMs, easing = LinearEasing) else motion.fastEffectsSpec()
    val enter = slideInHorizontally(spatial) { if (forward) it / fraction else -it / fraction } +
        fadeIn(fadeInSpec)
    val exit = slideOutHorizontally(spatial) { if (forward) -it / fraction else it / fraction } +
        fadeOut(fadeOutSpec)
    return if (forward) {
        enter togetherWith exit
    } else {
        enter togetherWith (exit + scaleOut(scale, targetScale = 0.92f))
    }
}
