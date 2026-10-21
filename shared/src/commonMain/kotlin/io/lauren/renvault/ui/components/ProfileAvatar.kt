package io.lauren.renvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RvProfileAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    photo: ImageBitmap? = null,
    shape: Shape = CircleShape,
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = RvPersonIcon,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(size * 0.6f),
        )

        val painter = rememberFitted(photo, size) ?: return@Box

        var revealed by remember(photo) { mutableStateOf(false) }
        LaunchedEffect(photo) { revealed = true }

        val shown by animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            label = "photo",
        )

        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = shown
                    val zoom = 1.12f - 0.12f * shown
                    scaleX = zoom
                    scaleY = zoom
                },
        )
    }
}
