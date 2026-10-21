package io.lauren.renvault.lib

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomUInt(): UInt = memScoped {
    val bytes = allocArray<ByteVar>(4)
    SecRandomCopyBytes(kSecRandomDefault, 4.convert(), bytes)
    var value = 0u
    for (index in 0 until 4) {
        value = (value shl 8) or (bytes[index].toInt() and 0xFF).toUInt()
    }
    value
}
