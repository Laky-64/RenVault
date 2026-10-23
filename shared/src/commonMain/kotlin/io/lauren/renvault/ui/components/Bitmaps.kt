package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val fittedCache = LinkedHashMap<Pair<ImageBitmap, Int>, Painter>()

@Composable
fun rememberFitted(image: ImageBitmap?, size: Dp): Painter? {
    val target = with(LocalDensity.current) { size.toPx() }.roundToInt()
    if (image == null) return null
    val key = image to target

    val painter by produceState(fittedCache[key], key) {
        fittedCache[key]?.let {
            value = it
            return@produceState
        }
        val made = withContext(Dispatchers.Default) { BitmapPainter(image.fitted(target)) }
        fittedCache[key] = made
        if (fittedCache.size > FittedCacheSize) fittedCache.remove(fittedCache.keys.first())
        value = made
    }
    return painter
}

private const val FittedCacheSize = 8

fun ImageBitmap.fitted(target: Int): ImageBitmap {
    val widest = maxOf(width, height)
    if (target <= 0 || widest <= target) return this

    var source = this
    while (maxOf(source.width, source.height) / 2 >= target) source = source.halved()

    return if (maxOf(source.width, source.height) == target) source else source.resized(target)
}

private fun ImageBitmap.halved(): ImageBitmap = redraw(width / 2, height / 2)

private fun ImageBitmap.resized(target: Int): ImageBitmap {
    val factor = target.toFloat() / maxOf(width, height)
    return redraw((width * factor).roundToInt(), (height * factor).roundToInt())
}

private fun ImageBitmap.redraw(across: Int, down: Int): ImageBitmap {
    val out = ImageBitmap(across.coerceAtLeast(1), down.coerceAtLeast(1))
    val canvas = Canvas(out)
    val paint = Paint().apply { filterQuality = FilterQuality.Low }

    canvas.drawImageRect(
        image = this,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(width, height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(out.width, out.height),
        paint = paint,
    )

    return out
}
