package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun RvBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun RvPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float, Boolean, Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) = RvBackHandler(enabled, onBack)

@Composable
internal actual fun screenCorner(): Dp = 0.dp
