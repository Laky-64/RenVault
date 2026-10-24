package io.lauren.renvault.lib

actual fun platformTag(): String {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val name = when {
        os.startsWith("mac") -> "macos"
        os.startsWith("windows") -> "windows"
        else -> os.substringBefore(' ')
    }
    return "$name ${System.getProperty("os.arch").orEmpty()}".trim()
}
