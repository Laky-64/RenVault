package io.lauren.renvault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import io.lauren.renvault.lib.RvZoneKind
import io.lauren.renvault.ui.theme.RvTone

@Composable
fun RvShapeBadge(
    icon: ImageVector,
    tone: RvTone,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = 48.dp,
    iconSize: Dp = size * 0.5f,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(polygon.toShape())
            .background(tone.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.content,
            modifier = Modifier.size(iconSize),
        )
    }
}

fun RvZoneKind.polygon(): RoundedPolygon = when (this) {
    RvZoneKind.All -> MaterialShapes.Cookie9Sided
    RvZoneKind.Passkeys -> MaterialShapes.Clover4Leaf
    RvZoneKind.Codes -> MaterialShapes.Gem
    RvZoneKind.Networks -> MaterialShapes.Sunny
    RvZoneKind.Security -> MaterialShapes.SoftBurst
    RvZoneKind.Deleted -> MaterialShapes.Cookie4Sided
}

suspend fun Animatable<Float, AnimationVector1D>.shake() {
    snapTo(0f)
    animateTo(
        targetValue = 0f,
        animationSpec = keyframes {
            durationMillis = ShakeMs
            0f at 0
            -10f at 60
            9f at 120
            -6f at 180
            4f at 240
            -2f at 320
            0f at ShakeMs
        },
    )
}

fun Modifier.shaken(offset: () -> Float): Modifier = graphicsLayer {
    translationX = offset() * density
}

private const val ShakeMs = 400
