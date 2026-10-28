package io.lauren.renvault.lib

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val KeyAlias = "io.lauren.renvault.quick"
private const val Transformation = "AES/GCM/NoPadding"
private const val IvSize = 12

@Composable
actual fun rememberBiometrics(): RvBiometrics? {
    val activity = LocalContext.current as? FragmentActivity ?: return null
    return remember(activity) { AndroidBiometrics(activity) }
}

private class AndroidBiometrics(private val activity: FragmentActivity) : RvBiometrics {
    override val method = "android-biometric"

    override val credentialSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

    override fun available(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override fun hasKey(): Boolean = runCatching { storedKey() != null }.getOrDefault(false)

    override suspend fun seal(key: ByteArray, title: String, cancel: String): ByteArray {
        forget()
        val credential = quickCredential() && credentialSupported
        val cipher = Cipher.getInstance(Transformation).apply { init(Cipher.ENCRYPT_MODE, createKey(credential)) }
        val ready = prompt(cipher, title, cancel)
        return ready.iv + ready.doFinal(key)
    }

    override suspend fun open(sealed: ByteArray, title: String, cancel: String): ByteArray {
        val key = storedKey() ?: throw RvBiometricGone()
        val cipher = try {
            Cipher.getInstance(Transformation).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, IvSize))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            forget()
            throw RvBiometricGone()
        }
        val ready = prompt(cipher, title, cancel)
        return ready.doFinal(sealed, IvSize, sealed.size - IvSize)
    }

    override fun forget() {
        runCatching { keyStore().deleteEntry(KeyAlias) }
    }

    private suspend fun prompt(cipher: Cipher, title: String, cancel: String): Cipher = try {
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val ready = result.cryptoObject?.cipher
                        if (ready == null) cont.resumeWithException(RvBiometricCancelled()) else cont.resume(ready)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resumeWithException(RvBiometricCancelled())
                    }
                },
            )
            val credential = quickCredential() && credentialSupported
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .apply {
                    if (credential) {
                        setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                    } else {
                        setAllowedAuthenticators(BIOMETRIC_STRONG)
                        setNegativeButtonText(cancel)
                    }
                }
                .build()
            RvLockGuard.expectLeave()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    } finally {
        RvLockGuard.disarm()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun storedKey(): SecretKey? = keyStore().getKey(KeyAlias, null) as? SecretKey

    private fun createKey(credential: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(KeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val types = if (credential) {
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    } else {
                        KeyProperties.AUTH_BIOMETRIC_STRONG
                    }
                    setUserAuthenticationParameters(0, types)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }
}
