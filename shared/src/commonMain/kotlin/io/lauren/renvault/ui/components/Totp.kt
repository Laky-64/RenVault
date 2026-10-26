package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.lauren.renvault.ui.theme.RenVault
import kotlin.math.floor
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val FlyMs = 300
private const val FlyStagger = 45
private val Guard = 25.milliseconds
private const val RefillMs = 480L

@OptIn(ExperimentalTime::class)
@Composable
fun rememberTotpSlot(period: Int = 30): Long {
    var slot by remember(period) { mutableLongStateOf(slotAt(nowMillis(), period)) }

    LaunchedEffect(period) {
        while (true) {
            val stamp = nowMillis()
            delay((1000L - stamp % 1000L).milliseconds + Guard)
            slot = slotAt(nowMillis(), period)
        }
    }

    return slot
}

@Composable
fun RvTotpCode(
    load: suspend () -> String,
    modifier: Modifier = Modifier,
    period: Int = 30,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    var code by remember(load) { mutableStateOf("") }
    val slot = rememberTotpSlot(period)

    LaunchedEffect(load, slot) {
        code = runCatching { load() }.getOrDefault("")
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RvTotpRing(period = period)
        RvTotpDigits(code = code, source = load, style = style, color = color)
    }
}

@Composable
fun RvTotpDigits(
    code: String,
    modifier: Modifier = Modifier,
    group: Int = 3,
    gap: Dp = 6.dp,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    source: Any? = null,
) {
    var animate by remember { mutableStateOf(false) }

    LaunchedEffect(source) { animate = false }
    LaunchedEffect(code) { if (code.isNotEmpty()) animate = true }

    val fly = with(LocalDensity.current) { 10.dp.roundToPx() }
    val face = style.copy(fontFeatureSettings = "tnum")

    Row(modifier = modifier) {
        code.forEachIndexed { index, char ->
            val spaced = group > 0 && index % group == group - 1 && index < code.lastIndex
            val duration = if (animate) FlyMs else 0
            val delay = if (animate) index * FlyStagger else 0

            AnimatedContent(
                targetState = char to code,
                transitionSpec = {
                    val spec = tween<IntOffset>(duration, delayMillis = delay)
                    val fade = tween<Float>(duration, delayMillis = delay)
                    (slideInVertically(spec) { -fly } + fadeIn(fade)) togetherWith
                        (slideOutVertically(spec) { fly } + fadeOut(fade))
                },
                modifier = Modifier
                    .padding(end = if (spaced) gap else 0.dp)
                    .clipToBounds(),
                label = "totpDigit",
            ) { (value, _) ->
                Text(text = value.toString(), style = face, color = color, maxLines = 1, softWrap = false)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun RvTotpRing(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    period: Int = 30,
    color: Color = RenVault.accents.strong,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    var now by remember { mutableLongStateOf(nowMillis()) }
    var refilledAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(period) {
        var slot = slotAt(nowMillis(), period)
        while (true) {
            withFrameMillis {
                val stamp = nowMillis()
                val current = slotAt(stamp, period)
                if (current != slot) {
                    slot = current
                    refilledAt = stamp
                }
                now = stamp
            }
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val left = (period - (now / 1000.0) % period) / period
        val gone = now - refilledAt
        val filled = if (refilledAt == 0L || gone >= RefillMs) {
            left
        } else {
            left * (1.0 - (1.0 - gone / RefillMs.toDouble()).pow(3))
        }.coerceIn(0.0, 1.0)

        val stroke = this.size.minDimension * 0.16f
        val radius = (this.size.minDimension - stroke) / 2f
        val corner = Offset(center.x - radius, center.y - radius)
        val box = Size(radius * 2f, radius * 2f)

        drawCircle(color = track, radius = radius, center = center, style = Stroke(width = stroke))

        if (filled <= 0.0) return@Canvas

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = (filled * 360.0).toFloat(),
            useCenter = false,
            topLeft = corner,
            size = box,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

private fun slotAt(millis: Long, period: Int): Long = floor(millis / 1000.0 / period).toLong()
