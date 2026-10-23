package io.lauren.renvault.lib

import platform.UIKit.UIDevice

internal actual object RvLan {
    actual fun addresses(): List<String> = emptyList()

    actual fun deviceName(): String = UIDevice.currentDevice.name

    actual fun start(node: String, port: Int, onSeen: (node: String, host: String, port: Int) -> Unit) = Unit

    actual fun stop() = Unit

    actual fun schedule(enabled: Boolean) = Unit
}
