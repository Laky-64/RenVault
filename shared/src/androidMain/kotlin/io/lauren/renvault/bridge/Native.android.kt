package io.lauren.renvault.bridge

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual object Native {

    init {
        System.loadLibrary("renvault")
    }

    actual external fun call(op: Int, token: Long, request: ByteArray?): ByteArray

    actual external fun nextEvent(timeoutMillis: Int): ByteArray?

    actual external fun cancel(token: Long)

    actual external fun shutdown()
}

actual object PlatformStorage {

    @Volatile
    private var directory: String = ""

    @Volatile
    internal var context: Context? = null
        private set

    fun attach(context: Context) {
        directory = context.filesDir.absolutePath
        this.context = context.applicationContext
    }

    actual fun base(): String = directory

    actual fun secret(): ByteArray? = context?.let(DeviceSecret::load)
}

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
