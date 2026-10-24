package io.lauren.renvault.lib

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual fun platformTag(): String = "ios ${Platform.cpuArchitecture.name.lowercase()}"
