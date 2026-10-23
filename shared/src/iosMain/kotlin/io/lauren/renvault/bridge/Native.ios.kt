package io.lauren.renvault.bridge

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual object Native {

    actual fun call(op: Int, token: Long, request: ByteArray?): ByteArray = missing()

    actual fun nextEvent(timeoutMillis: Int): ByteArray? = missing()

    actual fun cancel(token: Long): Unit = missing()

    actual fun shutdown(): Unit = missing()

    private fun missing(): Nothing =
        throw UnsupportedOperationException("bridge: the ios binding is not wired yet")
}

actual object PlatformStorage {
    actual fun secret(): ByteArray? = null

    actual fun base(): String {
        val directories = NSFileManager.defaultManager.URLsForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask,
        )
        return (directories.firstOrNull() as? platform.Foundation.NSURL)?.path.orEmpty()
    }
}

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.Default
