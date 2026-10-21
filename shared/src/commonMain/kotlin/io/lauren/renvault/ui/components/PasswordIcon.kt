package io.lauren.renvault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lauren.renvault.ui.theme.RenVault

@Composable
fun RvSiteIcon(
    fallback: String,
    modifier: Modifier = Modifier,
    glyph: ImageVector? = null,
    favicon: Painter? = null,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(percent = 30),
) {
    val accents = RenVault.accents
    val tone = remember(fallback, accents) { accents.forIndex(fallback.seed()) }

    val face = favicon as? RvFavicon
    val colors = MaterialTheme.colorScheme
    val settled = remember(face, colors.surfaceContainerHighest) {
        groundFor(face, colors.surfaceContainerHighest)
    }

    val arrival = remember { Animatable(if (favicon != null) 1f else 0f) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(favicon) {
        if (favicon == null) arrival.snapTo(0f) else arrival.animateTo(1f, spring)
    }
    val shown by remember { derivedStateOf { arrival.value.coerceIn(0f, 1f) } }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .drawBehind { drawRect(lerp(tone.container, settled, shown)) },
        contentAlignment = Alignment.Center,
    ) {
        if (shown < 1f) {
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - shown },
                contentAlignment = Alignment.Center,
            ) {
                if (glyph != null) {
                    Icon(
                        imageVector = glyph,
                        contentDescription = null,
                        tint = tone.content,
                        modifier = Modifier.size(size * 0.55f),
                    )
                } else {
                    Text(
                        text = (fallback.trim().firstOrNull() ?: '?').uppercaseChar().toString(),
                        style = MaterialTheme.typography.titleMediumEmphasized.copy(
                            fontSize = (size.value * 0.45f).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = tone.content,
                    )
                }
            }
        }

        if (favicon != null && glyph == null) {
            val bleed = face?.bleed == true
            Image(
                painter = favicon,
                contentDescription = null,
                contentScale = if (bleed) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .then(
                        if (bleed) Modifier.fillMaxSize() else Modifier.size(size * 0.58f),
                    )
                    .graphicsLayer {
                        val pop = 0.6f + 0.4f * arrival.value
                        val scale = if (bleed) 1.14f * pop else pop
                        scaleX = scale
                        scaleY = scale
                        alpha = shown
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            )
        }
    }
}

private fun groundFor(face: RvFavicon?, neutral: Color): Color {
    if (face == null || face.bleed) return neutral
    val hue = face.tint ?: neutral
    val choices = listOf(
        lerp(neutral, hue, 0.3f),
        lerp(Color.White, hue, 0.16f),
        lerp(Color(0xFF16171C), hue, 0.28f),
    )
    return choices.firstOrNull { contrastOf(it.luminance(), face.ink) >= 3f }
        ?: choices.maxBy { contrastOf(it.luminance(), face.ink) }
}

private fun String.seed(): Int {
    var seed = 0
    trim().lowercase().forEach { seed = seed * 31 + it.code }
    return seed
}
