package io.lauren.renvault.passkey

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.lauren.renvault.R
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun FragmentActivity.confirmUser(): Boolean? {
    val allowed = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) return null
    return suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.passkey_verify))
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(info)
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
