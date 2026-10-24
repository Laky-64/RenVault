package io.lauren.renvault.lib

import android.os.Build

actual fun platformTag(): String = "android ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}".trim()
