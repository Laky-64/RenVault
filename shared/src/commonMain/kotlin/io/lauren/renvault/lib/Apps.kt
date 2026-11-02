package io.lauren.renvault.lib

const val AppLinkScheme = "androidapp://"

fun isAppLink(value: String): Boolean = value.startsWith(AppLinkScheme)

fun appLink(packageName: String): String = AppLinkScheme + packageName

fun appPackageOf(link: String): String = link.removePrefix(AppLinkScheme)

expect fun appLabel(packageName: String): String?

expect fun appIcon(packageName: String): ByteArray?
