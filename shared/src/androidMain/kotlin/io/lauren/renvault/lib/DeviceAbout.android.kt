package io.lauren.renvault.lib

import android.os.Build

actual fun devicePlatform(): String = "Android ${Build.VERSION.RELEASE}"

actual fun deviceModel(): String {
    val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
    val model = Build.MODEL.orEmpty()
    return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
}
