package io.lauren.renvault.lib

import java.io.File

actual fun devicePlatform(): String {
    val os = System.getProperty("os.name").orEmpty()
    return when {
        os.startsWith("Mac", ignoreCase = true) -> "macOS ${System.getProperty("os.version").orEmpty()}".trim()
        os.startsWith("Windows", ignoreCase = true) -> os
        else -> releaseName() ?: os
    }
}

actual fun deviceModel(): String {
    val vendor = dmi("sys_vendor").replace(CompanySuffix, "").trim()
    val product = dmi("product_name")
    if (product.isEmpty() || product.equals("To Be Filled By O.E.M.", ignoreCase = true)) return ""
    return if (vendor.isEmpty() || product.startsWith(vendor, ignoreCase = true)) product else "$vendor $product"
}

private val CompanySuffix = Regex("""(?i)[,.]?\s+(technology|technologies|computer|corporation|corp\.?|co\.?|ltd\.?|inc\.?|international|gmbh|s\.?r\.?l\.?)\b\.?""")

private fun releaseName(): String? = runCatching {
    File("/etc/os-release").readLines()
        .firstOrNull { it.startsWith("PRETTY_NAME=") }
        ?.substringAfter('=')
        ?.trim('"')
        ?.substringBefore(" (")
}.getOrNull()?.takeIf { it.isNotBlank() }

private fun dmi(name: String): String =
    runCatching { File("/sys/class/dmi/id/$name").readText().trim() }.getOrDefault("")
