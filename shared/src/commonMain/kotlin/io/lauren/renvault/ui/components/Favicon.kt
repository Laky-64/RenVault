package io.lauren.renvault.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class RvFavicon(image: ImageBitmap) : Painter() {
    private val painter = BitmapPainter(image, filterQuality = FilterQuality.High)

    val bleed: Boolean
    val plate: Color?
    val zoom: Float
    val tint: Color?

    val ink: Float

    init {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.readPixels(pixels)

        val ground = plateOf(pixels, width, height)
        plate = ground?.let { Color(it.color) }
        bleed = ground != null || edgesOpaque(pixels, width, height) && squareCorners(pixels, width, height)
        zoom = when {
            ground == null -> 1f
            ground.even -> plateZoom(pixels, width, height, ground.color)
            else -> max(1f, plateZoom(pixels, width, height, ground.color))
        }
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

private const val BandTolerance = 48
private const val MaxZoom = 1.14f
private const val MinZoom = 0.6f
private const val SafeReach = 0.74f
private const val FrameReach = 0.95f
private const val HoleReach = 0.92f
private const val InkAlpha = 250

private fun near(a: Int, b: Int, slack: Int = BandTolerance): Boolean =
    kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
        kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
        kotlin.math.abs((a and 0xFF) - (b and 0xFF)) <= slack

private fun reachOf(x: Int, y: Int, width: Int, height: Int): Float {
    val dx = (x + 0.5f - width / 2f) / (width / 2f)
    val dy = (y + 0.5f - height / 2f) / (height / 2f)
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private class Ground(val color: Int, val even: Boolean)

private fun plateOf(pixels: IntArray, width: Int, height: Int): Ground? {
    if (width < 8 || height < 8) return null
    val inset = max(1, min(width, height) / 24)
    val sides = listOf(
        pixels[inset * width + width / 2],
        pixels[(height - 1 - inset) * width + width / 2],
        pixels[(height / 2) * width + inset],
        pixels[(height / 2) * width + width - 1 - inset],
    )
    if (sides.any { (it ushr 24) <= 230 }) return null
    val mean = (sides.sumOf { (it shr 16) and 0xFF } / sides.size shl 16) or
        (sides.sumOf { (it shr 8) and 0xFF } / sides.size shl 8) or
        (sides.sumOf { it and 0xFF } / sides.size)
    if (!sides.all { near(it, mean) }) return null
    for (y in 0 until height) {
        for (x in 0 until width) {
            if ((pixels[y * width + x] ushr 24) < 128 && reachOf(x, y, width, height) < HoleReach) return null
        }
    }
    return Ground(mean or (0xFF shl 24), sides.all { near(it, mean, BandTolerance / 2) })
}

private fun plateZoom(pixels: IntArray, width: Int, height: Int, plate: Int): Float {
    var reach = 0f
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            if ((pixel ushr 24) < InkAlpha || near(pixel, plate)) continue
            val r = reachOf(x, y, width, height)
            if (r < FrameReach) reach = max(reach, r)
        }
    }
    if (reach <= 0f) return MaxZoom
    return (SafeReach / reach).coerceIn(MinZoom, MaxZoom)
}

private fun squareCorners(pixels: IntArray, width: Int, height: Int): Boolean {
    val inset = max(1, min(width, height) / 24)
    val corners = listOf(
        pixels[inset * width + inset],
        pixels[inset * width + width - 1 - inset],
        pixels[(height - 1 - inset) * width + inset],
        pixels[(height - 1 - inset) * width + width - 1 - inset],
    )
    if (corners.any { (it ushr 24) <= 230 }) return false
    val first = corners.first()
    return corners.all { pixel ->
        kotlin.math.abs(((pixel shr 16) and 0xFF) - ((first shr 16) and 0xFF)) +
            kotlin.math.abs(((pixel shr 8) and 0xFF) - ((first shr 8) and 0xFF)) +
            kotlin.math.abs((pixel and 0xFF) - (first and 0xFF)) <= BandTolerance
    }
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
