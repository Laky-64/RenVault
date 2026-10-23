package io.lauren.renvault.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object DeviceSecret {
    private const val Alias = "io.lauren.renvault.device"
    private const val Store = "AndroidKeyStore"
    private const val Transform = "AES/GCM/NoPadding"
    private const val IvSize = 12
    private const val TagBits = 128
    private const val SecretSize = 32

    @Synchronized
    fun load(context: Context): ByteArray? = runCatching {
        val file = File(context.noBackupFilesDir, "device.wrap")
        val key = keystoreKey()
        if (file.exists()) {
            val sealed = file.readBytes()
            val cipher = Cipher.getInstance(Transform)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagBits, sealed, 0, IvSize))
            return@runCatching cipher.doFinal(sealed, IvSize, sealed.size - IvSize)
        }
        val secret = ByteArray(SecretSize).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(Transform)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        file.writeBytes(cipher.iv + cipher.doFinal(secret))
        secret
    }.getOrNull()

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance(Store).apply { load(null) }
        (store.getKey(Alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(SecretSize * 8)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Store).run {
            init(spec)
            generateKey()
        }
    }
}
