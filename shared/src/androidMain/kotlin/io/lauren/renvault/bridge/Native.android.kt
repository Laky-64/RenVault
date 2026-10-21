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

    fun attach(context: Context) {
        directory = context.filesDir.absolutePath
    }

    actual fun base(): String = directory
}

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
