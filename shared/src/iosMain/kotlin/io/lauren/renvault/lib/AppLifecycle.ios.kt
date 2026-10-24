package io.lauren.renvault.lib

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle

actual val leaveEvent: Lifecycle.Event = Lifecycle.Event.ON_PAUSE

@Composable
actual fun RvHideFromRecents(hidden: Boolean) = Unit
