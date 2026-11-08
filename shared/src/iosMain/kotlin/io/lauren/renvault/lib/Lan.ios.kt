package io.lauren.renvault.lib

import androidx.compose.runtime.Composable

import platform.UIKit.UIDevice

internal actual object RvLan {
    actual fun addresses(): List<String> = emptyList()

    actual fun deviceName(): String = UIDevice.currentDevice.name

    actual fun start(node: String, port: Int, onSeen: (node: String, host: String, port: Int) -> Unit) = Unit

    actual fun stop() = Unit

    actual fun schedule(enabled: Boolean) = Unit
}

@Composable
internal actual fun rememberLocalNetworkAccess(needed: Boolean): Boolean = true
