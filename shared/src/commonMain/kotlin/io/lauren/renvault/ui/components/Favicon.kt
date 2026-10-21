package io.lauren.renvault.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class RvFavicon(image: ImageBitmap) : Painter() {
    private val painter = BitmapPainter(image)

    val bleed: Boolean
    val tint: Color?

    val ink: Float

    init {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.readPixels(pixels)

        bleed = edgesOpaque(pixels, width, height)
        tint = strongest(pixels)
        ink = luminance(pixels)
    }

    override val intrinsicSize get() = painter.intrinsicSize

    override fun DrawScope.onDraw() {
        with(painter) { draw(size) }
    }
}

private fun edgesOpaque(pixels: IntArray, width: Int, height: Int): Boolean {
    if (width < 8 || height < 8) return false
    val inset = max(1, min(width, height) / 24)
    var opaque = 0
    var total = 0

    fun sample(x: Int, y: Int) {
        total++
        if ((pixels[y * width + x] ushr 24) > 230) opaque++
    }

    for (x in width / 5 until width - width / 5) {
        sample(x, inset)
        sample(x, height - 1 - inset)
    }
    for (y in height / 5 until height - height / 5) {
        sample(inset, y)
        sample(width - 1 - inset, y)
    }

    return total > 0 && opaque >= total * 0.95
}

private fun strongest(pixels: IntArray): Color? {
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var weight = 0.0

    for (pixel in pixels) {
        val alpha = pixel ushr 24
        if (alpha < 128) continue
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val chroma = (high - low) / 255.0
        if (chroma < 0.15) continue
        red += r * chroma
        green += g * chroma
        blue += b * chroma
        weight += chroma
    }

    if (weight < pixels.size * 0.02) return null
    return Color((red / weight / 255).toFloat(), (green / weight / 255).toFloat(), (blue / weight / 255).toFloat())
}

private fun luminance(pixels: IntArray): Float {
    var sum = 0.0
    var count = 0
    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        sum += 0.2126 * linear((pixel shr 16) and 0xFF) +
            0.7152 * linear((pixel shr 8) and 0xFF) +
            0.0722 * linear(pixel and 0xFF)
        count++
    }
    return if (count == 0) 1f else (sum / count).toFloat()
}

private fun linear(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

fun contrastOf(a: Float, b: Float): Float = (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
