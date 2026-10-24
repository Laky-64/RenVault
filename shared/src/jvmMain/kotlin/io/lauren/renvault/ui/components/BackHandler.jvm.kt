package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun RvBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun RvPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) = RvBackHandler(enabled, onBack)
