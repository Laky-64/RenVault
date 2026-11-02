package io.lauren.renvault.lib

import android.graphics.Bitmap
import android.graphics.Canvas
import io.lauren.renvault.bridge.PlatformStorage
import java.io.ByteArrayOutputStream

private const val AppIconSide = 192

actual fun appLabel(packageName: String): String? {
    val manager = PlatformStorage.context?.packageManager ?: return null
    return runCatching { manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString() }.getOrNull()
}

actual fun appIcon(packageName: String): ByteArray? {
    val manager = PlatformStorage.context?.packageManager ?: return null
    return runCatching {
        val drawable = manager.getApplicationIcon(packageName)
        val bitmap = Bitmap.createBitmap(AppIconSide, AppIconSide, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, AppIconSide, AppIconSide)
        drawable.draw(Canvas(bitmap))
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }.getOrNull()
}
