package io.lauren.renvault.bridge

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

internal actual object Native {

    init {
        System.load(extract())
    }

    actual external fun call(op: Int, token: Long, request: ByteArray?): ByteArray

    actual external fun nextEvent(timeoutMillis: Int): ByteArray?

    actual external fun cancel(token: Long)

    actual external fun shutdown()
}

actual object PlatformStorage {
    actual fun base(): String = ""
}

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

private fun extract(): String {
    val resource = "/native/$hostGoos-$hostGoarch/$hostLibraryName"
    val stream = Native::class.java.getResourceAsStream(resource)
        ?: error("bridge: $resource is missing from the runtime classpath")

    val directory = Files.createTempDirectory("renvault-native")
    val target = directory.resolve(hostLibraryName)

    stream.use { source ->
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    target.toFile().deleteOnExit()
    directory.toFile().deleteOnExit()

    return target.toAbsolutePath().toString()
}

private val hostGoos: String
    get() {
        val name = System.getProperty("os.name").lowercase(Locale.ROOT)
        return when {
            name.startsWith("windows") -> "windows"
            name.startsWith("mac") || name.startsWith("darwin") -> "darwin"
            else -> "linux"
        }
    }

private val hostGoarch: String
    get() = when (val architecture = System.getProperty("os.arch").lowercase(Locale.ROOT)) {
        "aarch64", "arm64" -> "arm64"
        "amd64", "x86_64" -> "amd64"
        else -> architecture
    }

private val hostLibraryName: String
    get() = when (hostGoos) {
        "windows" -> "renvault.dll"
        "darwin" -> "librenvault.dylib"
        else -> "librenvault.so"
    }
