package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private const val MaxScale = 0.9f

private val GestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private val EmphasizedEasing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private val EdgeMargin = 8.dp

private val EnterOffset = 96.dp

private const val ScrimDark = 0.8f

private const val ScrimLight = 0.2f

private const val CommitMs = 450

private const val FlingScale = 100f

private const val MaxFling = 1000f

private const val MinFling = 120f

private const val CancelMs = 150

private const val SettleFrames = 30

internal class BackTransition<S>(
    val transition: Transition<S>,
    predictive: State<Boolean>,
    released: State<Boolean>,
    progress: State<Float>,
    held: State<Float>,
    settle: State<Float>,
    fromRight: State<Boolean>,
    drift: State<Float>,
    bounce: State<Float>,
    internal val alive: MutableIntState,
    depth: State<Int>,
) {
    val depth by depth
    val predictive by predictive
    val released by released
    val progress by progress
    val held by held
    val settle by settle
    val fromRight by fromRight
    val drift by drift
    val bounce by bounce
}

@Composable
expect fun RvBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
internal expect fun screenCorner(): Dp

@Composable
expect fun RvPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float, Boolean, Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
)

@Composable
internal fun <S> rememberBackTransition(screen: S, previous: S?, label: String, onBack: () -> Unit): BackTransition<S> {
    val state = remember { SeekableTransitionState(screen) }
    val scope = rememberCoroutineScope()
    val progressState = remember { mutableFloatStateOf(0f) }
    var progress by progressState
    val fromRightState = remember { mutableStateOf(false) }
    var fromRight by fromRightState
    val driftState = remember { mutableFloatStateOf(0f) }
    var drift by driftState
    var touchStart by remember { mutableFloatStateOf(0f) }
    val bounceState = remember { mutableFloatStateOf(1f) }
    var bounce by bounceState
    var velocity by remember { mutableFloatStateOf(0f) }
    var stamp by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    val alive = remember { mutableIntStateOf(0) }
    val depthState = remember { mutableIntStateOf(0) }
    var depth by depthState
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val releasedState = remember { mutableStateOf(false) }
    var released by releasedState
    val heldState = remember { mutableFloatStateOf(0f) }
    var held by heldState
    val settleState = remember { mutableFloatStateOf(0f) }
    var settle by settleState
    var landing by remember { mutableStateOf<S?>(null) }
    val predictive = remember { derivedStateOf { dragging || (released && state.targetState == landing) } }
    var gesture by remember { mutableIntStateOf(0) }
    val commands = remember { Channel<suspend () -> Unit>(Channel.UNLIMITED) }
    val current by rememberUpdatedState(screen)
    val target by rememberUpdatedState(previous)

    LaunchedEffect(commands) {
        for (command in commands) {
            try {
                command()
            } catch (interrupted: CancellationException) {
                ensureActive()
            }
        }
    }
    LaunchedEffect(screen, dragging) {
        if (dragging) return@LaunchedEffect
        if (released && screen != landing) {
            settling?.cancel()
            released = false
        }
        try {
            state.animateTo(screen, if (released) tween(CommitMs / 5, easing = LinearEasing) else null)
        } catch (interrupted: CancellationException) {
            ensureActive()
        }
    }

    fun seek(value: Float) {
        progress = value
        val id = gesture
        val destination = target ?: return
        commands.trySend {
            if (gesture != id || !dragging) return@trySend
            if (state.targetState != destination) {
                if (state.currentState != state.targetState) state.snapTo(state.targetState)
                var frames = 0
                while (alive.intValue > 1 && frames++ < SettleFrames) withFrameNanos { }
            }
            state.seekTo(0f, destination)
        }
    }

    RvPredictiveBackHandler(
        enabled = previous != null,
        onProgress = { value, right, touchY ->
            settling?.cancel()
            if (!dragging) {
                touchStart = touchY
                settle = 0f
                bounce = 1f
                velocity = 0f
                stamp = TimeSource.Monotonic.markNow()
                depth++
            } else {
                val elapsed = stamp.elapsedNow().inWholeMicroseconds / 1_000_000f
                if (elapsed > 0f) velocity = (GestureEasing.transform(value) - GestureEasing.transform(progress)) / elapsed
                stamp = TimeSource.Monotonic.markNow()
            }
            drift = touchY - touchStart
            fromRight = right
            dragging = true
            seek(value)
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
            if (released) {
                landing = target
                held = progress
                settle = 0f
                val eased = GestureEasing.transform(progress)
                var fling = velocity * FlingScale * (1f - MaxScale) * 2f
                if (eased < 0.1f) fling = fling.coerceAtLeast(MinFling)
                settling = scope.launch {
                    val spring = launch {
                        animate(
                            initialValue = 1f,
                            targetValue = 1f,
                            initialVelocity = -fling.coerceIn(0f, MaxFling) / FlingScale,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                        ) { value, _ -> bounce = value }
                    }
                    try {
                        animate(0f, 1f, animationSpec = tween(CommitMs, easing = LinearEasing)) { value, _ -> settle = value }
                    } finally {
                        spring.cancel()
                        released = false
                    }
                }
            }
            onBack()
        },
    )
    val transition = rememberTransition(state, label)
    return remember(transition) { BackTransition(transition, predictive, releasedState, progressState, heldState, settleState, fromRightState, driftState, bounceState, alive, depthState) }
}

@Composable
internal fun <S> BackTransition<S>.Content(
    modifier: Modifier,
    fraction: Int,
    forward: AnimatedContentTransitionScope<S>.() -> Boolean,
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val navigation = transition
    val corner = screenCorner()
    val surface = MaterialTheme.colorScheme.surface
    val scrim = if (surface.luminance() < 0.5f) ScrimDark else ScrimLight
    navigation.AnimatedContent(
        modifier = modifier.drawBehind {
            if (!predictive) return@drawBehind
            drawRect(surface)
            drawRect(Color.Black, alpha = scrim(scrim))
        },
        transitionSpec = { if (predictive) slideAway(depth) else slideBetween(forward(), motion, fraction) },
    ) { shown ->
        DisposableEffect(Unit) {
            alive.intValue++
            onDispose { alive.intValue-- }
        }
        val leaving = predictive && shown == navigation.currentState && shown != navigation.targetState
        val behind = predictive && shown == navigation.targetState && (shown != navigation.currentState || released)
        when {
            leaving -> Box(modifier = Modifier.fillMaxSize().closing(this@Content, corner)) { content(shown) }
            behind -> Box(modifier = Modifier.fillMaxSize().entering(this@Content, corner, scrim)) { content(shown) }
            else -> Box(modifier = Modifier.fillMaxSize()) { content(shown) }
        }
    }
}

private fun Rect.scaleCentered(scale: Float): Rect {
    val halfWidth = width * scale / 2f
    val halfHeight = height * scale / 2f
    return Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
}

private class BackFrame(val closing: Rect, val entering: Rect)

private fun GraphicsLayerScope.frameOf(back: BackTransition<*>, progress: Float): BackFrame {
    val full = Rect(0f, 0f, size.width, size.height)
    val margin = EdgeMargin.toPx()
    var closingTarget = full.scaleCentered(MaxScale)
    if (!back.fromRight) closingTarget = closingTarget.translate(full.right - closingTarget.right - margin, 0f)
    val closing = lerp(full, closingTarget, progress)
    val half = size.height / 2f
    val ratio = minOf(half, abs(back.drift)) / half
    val eased = 1f - (1f - ratio) * (1f - ratio)
    val lift = maxOf(0f, (size.height - closing.height) / 2f - margin) * eased * sign(back.drift)
    val enteringStart = full.translate(-EnterOffset.toPx(), 0f)
    val entering = lerp(enteringStart, enteringStart.scaleCentered(MaxScale), progress)
    return BackFrame(closing.translate(0f, lift), entering.translate(0f, lift))
}

private fun GraphicsLayerScope.frameNow(back: BackTransition<*>): BackFrame {
    if (!back.released) return frameOf(back, GestureEasing.transform(back.progress))
    val start = frameOf(back, GestureEasing.transform(back.held))
    val full = Rect(0f, 0f, size.width, size.height)
    val progress = EmphasizedEasing.transform(back.settle)
    val scale = minOf(back.bounce, 1f)
    return BackFrame(
        lerp(start.closing, full.translate(start.closing.left + EnterOffset.toPx(), 0f), progress).scaleCentered(scale),
        lerp(start.entering, full, progress).scaleCentered(scale),
    )
}

private fun GraphicsLayerScope.place(rect: Rect, corner: Dp) {
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = rect.width / size.width
    scaleY = rect.height / size.height
    translationX = rect.left
    translationY = rect.top
    shape = RoundedCornerShape(corner)
    clip = true
}

private fun BackTransition<*>.scrim(max: Float): Float = if (released) max * (1f - settle) else max

private fun Modifier.closing(back: BackTransition<*>, corner: Dp): Modifier = graphicsLayer {
    place(frameNow(back).closing, corner)
    alpha = if (back.released) maxOf(1f - back.settle * 5f, 0f) else 1f
}

private fun Modifier.entering(back: BackTransition<*>, corner: Dp, scrim: Float): Modifier = graphicsLayer {
    place(frameNow(back).entering, corner)
}.drawWithContent {
    drawContent()
    drawRect(Color.Black, alpha = back.scrim(scrim))
}

private val Reveal = fadeIn(tween(CommitMs), initialAlpha = 1f) +
    slideInHorizontally(tween(CommitMs)) { 0 } +
    scaleIn(tween(CommitMs), initialScale = 1f)

private fun slideAway(depth: Int): ContentTransform = ContentTransform(
    targetContentEnter = Reveal,
    initialContentExit = fadeOut(tween(CommitMs / 5), targetAlpha = 1f),
    targetContentZIndex = -depth.toFloat(),
)

internal fun slideBetween(forward: Boolean, motion: MotionScheme, fraction: Int): ContentTransform {
    val enter = slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / fraction else -it / fraction } +
        fadeIn(motion.defaultEffectsSpec())
    val exit = slideOutHorizontally(motion.defaultSpatialSpec()) { if (forward) -it / fraction else it / fraction } +
        fadeOut(motion.fastEffectsSpec())
    return if (forward) {
        enter togetherWith exit
    } else {
        enter togetherWith (exit + scaleOut(motion.defaultSpatialSpec(), targetScale = 0.92f))
    }
}
