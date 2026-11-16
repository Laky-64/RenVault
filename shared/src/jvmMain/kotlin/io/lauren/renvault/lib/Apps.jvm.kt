package io.lauren.renvault.lib

actual fun appLabel(packageName: String): String? = null

actual fun appIcon(packageName: String): ByteArray? = null

actual fun installedApps(): List<String> = emptyList()
