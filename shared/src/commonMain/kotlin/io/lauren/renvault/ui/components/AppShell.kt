package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RvVaultShell(
    authenticated: Boolean,
    modifier: Modifier = Modifier,
    titleBar: @Composable (() -> Unit)? = null,
    auth: @Composable () -> Unit = {},
    home: @Composable () -> Unit = {},
) {
    val motion = MaterialTheme.motionScheme

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            titleBar?.invoke()

            AnimatedContent(
                targetState = authenticated,
                modifier = Modifier.weight(1f).fillMaxSize(),
                transitionSpec = {
                    (fadeIn(motion.slowEffectsSpec()) + scaleIn(motion.slowSpatialSpec(), initialScale = 0.94f)) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.slowSpatialSpec(), targetScale = 1.04f))
                },
                label = "screenSwap",
            ) { open ->
                if (open) {
                    Box(modifier = Modifier.fillMaxSize()) { home() }
                } else {
                    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { auth() }
                }
            }
        }
    }
}
