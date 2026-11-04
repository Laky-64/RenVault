package io.lauren.renvault.lib

import java.io.File
import java.util.concurrent.TimeUnit

actual fun devicePlatform(): String {
    val os = System.getProperty("os.name").orEmpty()
    return when {
        os.startsWith("Mac", ignoreCase = true) -> "macOS ${System.getProperty("os.version").orEmpty()}".trim()
        os.startsWith("Windows", ignoreCase = true) -> os
        else -> releaseName() ?: os
    }
}

actual fun deviceModel(): String = cachedModel

private val cachedModel: String by lazy {
    val os = System.getProperty("os.name").orEmpty()
    runCatching {
        when {
            os.startsWith("Mac", ignoreCase = true) -> macModel()
            os.startsWith("Windows", ignoreCase = true) -> windowsModel()
            else -> linuxModel()
        }
    }.getOrDefault("")
}

private val CompanySuffix = Regex("""(?i)[,.]?\s+(technology|technologies|computer|corporation|corp\.?|co\.?|ltd\.?|inc\.?|international|gmbh|s\.?r\.?l\.?)\b\.?""")

private val Placeholders = setOf(
    "to be filled by o.e.m.",
    "system product name",
    "system manufacturer",
    "default string",
    "not applicable",
    "o.e.m.",
    "none",
)

private fun modelOf(vendor: String, product: String): String? {
    val maker = vendor.replace(CompanySuffix, "").trim().takeUnless { it.lowercase() in Placeholders }.orEmpty()
    val name = product.trim().takeUnless { it.isEmpty() || it.lowercase() in Placeholders } ?: return null
    return if (maker.isEmpty() || name.startsWith(maker, ignoreCase = true)) name else "$maker $name"
}

private fun linuxModel(): String =
    modelOf(dmi("sys_vendor"), dmi("product_name"))
        ?: modelOf(dmi("board_vendor"), dmi("board_name"))
        ?: ""

private fun windowsModel(): String {
    val bios = registry("""HKLM\HARDWARE\DESCRIPTION\System\BIOS""")
    return modelOf(bios["SystemManufacturer"].orEmpty(), bios["SystemProductName"].orEmpty())
        ?: modelOf(bios["BaseBoardManufacturer"].orEmpty(), bios["BaseBoardProduct"].orEmpty())
        ?: ""
}

private fun macModel(): String {
    val profile = command("system_profiler", "SPHardwareDataType")
    val name = Regex("""(?m)^\s*Model Name:\s*(.+)$""").find(profile)?.groupValues?.get(1)?.trim()
    if (!name.isNullOrEmpty()) return name
    return command("sysctl", "-n", "hw.model").trim()
}

private fun registry(key: String): Map<String, String> =
    command("reg", "query", key).lineSequence()
        .mapNotNull { Regex("""^\s+(\S+)\s+REG_\w+\s+(.*)$""").find(it) }
        .associate { it.groupValues[1] to it.groupValues[2].trim() }

private fun command(vararg args: String): String {
    val process = ProcessBuilder(*args).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return ""
    }
    return output
}

private fun releaseName(): String? = runCatching {
    File("/etc/os-release").readLines()
        .firstOrNull { it.startsWith("PRETTY_NAME=") }
        ?.substringAfter('=')
        ?.trim('"')
        ?.substringBefore(" (")
}.getOrNull()?.takeIf { it.isNotBlank() }

private fun dmi(name: String): String =
    runCatching { File("/sys/class/dmi/id/$name").readText().trim() }.getOrDefault("")
