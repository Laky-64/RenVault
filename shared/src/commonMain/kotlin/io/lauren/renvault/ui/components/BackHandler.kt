package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun RvBackHandler(enabled: Boolean, onBack: () -> Unit)
