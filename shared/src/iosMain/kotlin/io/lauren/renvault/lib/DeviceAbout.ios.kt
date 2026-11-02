package io.lauren.renvault.lib

import platform.UIKit.UIDevice

actual fun devicePlatform(): String =
    "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"

actual fun deviceModel(): String = UIDevice.currentDevice.model
