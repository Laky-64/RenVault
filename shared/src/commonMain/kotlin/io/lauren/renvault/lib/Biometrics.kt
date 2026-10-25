package io.lauren.renvault.lib

import androidx.compose.runtime.Composable

class RvBiometricCancelled : Exception("biometric: cancelled")

class RvBiometricGone : Exception("biometric: key invalidated")

interface RvBiometrics {
    val method: String
    val credentialSupported: Boolean
    fun available(): Boolean
    fun hasKey(): Boolean
    suspend fun seal(key: ByteArray, title: String, cancel: String): ByteArray
    suspend fun open(sealed: ByteArray, title: String, cancel: String): ByteArray
    fun forget()
}

@Composable
expect fun rememberBiometrics(): RvBiometrics?

private const val QuickCredentialKey = "quick_device_credential"

fun quickCredential(): Boolean = readPreference(QuickCredentialKey) == "on"

fun setQuickCredential(on: Boolean) = writePreference(QuickCredentialKey, if (on) "on" else "off")

fun quickKey(): ByteArray = ByteArray(32).also { key ->
    for (i in key.indices step 4) {
        val word = secureRandomUInt()
        for (j in 0 until 4) key[i + j] = (word shr (8 * j)).toByte()
    }
}
