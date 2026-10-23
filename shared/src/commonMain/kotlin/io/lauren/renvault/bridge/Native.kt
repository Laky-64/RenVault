package io.lauren.renvault.bridge

internal expect object Native {
    fun call(op: Int, token: Long, request: ByteArray?): ByteArray
    fun nextEvent(timeoutMillis: Int): ByteArray?
    fun cancel(token: Long)
    fun shutdown()
}

expect object PlatformStorage {
    fun base(): String
    fun secret(): ByteArray?
}
