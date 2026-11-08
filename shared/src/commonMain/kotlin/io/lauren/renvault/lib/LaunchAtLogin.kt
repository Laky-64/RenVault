package io.lauren.renvault.lib

import androidx.compose.runtime.Composable

class RvLaunchAtLogin(val enabled: Boolean, val set: (Boolean) -> Unit)

@Composable
expect fun rememberLaunchAtLogin(): RvLaunchAtLogin?
