package io.lauren.renvault.passkey

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import io.lauren.renvault.RvUnlockGate
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.ui.theme.RenVaultTheme

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlatformStorage.attach(applicationContext)
        val mode = intent.getStringExtra(ExtraMode)
        val passkeyId = intent.getStringExtra(ExtraPasskey)

        setContent {
            RenVaultTheme {
                var ready by remember { mutableStateOf(false) }
                var unlocked by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    runCatching { Vault.init() }
                    val open = runCatching { Vault.state().unlocked }.getOrDefault(false)
                    if (open && mode != ModeUnlock && !consumeVerification()) {
                        when (confirmUser()) {
                            true -> unlocked = true
                            false -> return@LaunchedEffect cancel(mode)
                            null -> runCatching { Vault.lock() }
                        }
                    } else {
                        unlocked = open
                    }
                    ready = true
                }
                LaunchedEffect(unlocked) {
                    if (!unlocked) return@LaunchedEffect
                    when (mode) {
                        ModeUnlock -> finishUnlock()
                        ModeGet -> finishGet(passkeyId)
                        ModeCreate -> finishCreate()
                        else -> finish()
                    }
                }

                when {
                    !ready || unlocked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (ready) ContainedLoadingIndicator()
                    }
                    else -> RvUnlockGate(onUnlocked = { unlocked = true })
                }
            }
        }
    }

    private fun cancel(mode: String?) {
        val result = Intent()
        when (mode) {
            ModeGet -> PendingIntentHandler.setGetCredentialException(result, GetCredentialCancellationException())
            ModeCreate -> PendingIntentHandler.setCreateCredentialException(result, CreateCredentialCancellationException())
        }
        done(result)
    }

    private suspend fun finishUnlock() {
        verifiedAt = SystemClock.elapsedRealtime()
        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent) ?: return finish()
        val response = beginGetResponse(this, request, runCatching { Vault.passkeys() }.getOrDefault(emptyList()))
        val result = Intent()
        PendingIntentHandler.setBeginGetCredentialResponse(result, response)
        done(result)
    }

    private suspend fun finishGet(passkeyId: String?) {
        val result = Intent()
        val outcome = runCatching {
            val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent) ?: error("missing request")
            val option = request.credentialOptions.filterIsInstance<GetPublicKeyCredentialOption>().firstOrNull()
                ?: error("missing passkey option")
            val asked = parseAssertion(option.requestJson)
            val passkey = Vault.passkeys().firstOrNull { it.id == passkeyId && !it.isDeleted } ?: error("passkey not found")
            if (passkey.relyingParty.lowercase() != asked.rpId) error("passkey belongs to another site")
            val caller = callerOf(this, request.callingAppInfo, asked.rpId)
                ?: return@runCatching GetPublicKeyCredentialDomException(NotAllowedError(), "caller not allowed for ${asked.rpId}")
            val clientData = clientDataJson("webauthn.get", asked.challenge, caller.origin, caller.packageName)
            val hash = option.clientDataHash ?: sha256(clientData)
            val credential = passkey.credentialId.hexBytes() ?: error("bad credential id")
            val signed = Vault.signPasskey(passkey.id, hash)
            val json = assertionJson(credential, signed.authenticatorData, signed.signature, signed.userHandle, clientData)
            PendingIntentHandler.setGetCredentialResponse(result, GetCredentialResponse(PublicKeyCredential(json)))
            null
        }
        val failure = outcome.getOrNull() ?: outcome.exceptionOrNull()?.let { GetCredentialUnknownException(it.message) }
        if (failure != null) PendingIntentHandler.setGetCredentialException(result, failure)
        done(result)
    }

    private suspend fun finishCreate() {
        val result = Intent()
        val outcome = runCatching {
            val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent) ?: error("missing request")
            val call = request.callingRequest as? CreatePublicKeyCredentialRequest ?: error("not a passkey request")
            val asked = parseCreation(call.requestJson)
            if (!asked.supported) {
                return@runCatching CreatePublicKeyCredentialDomException(NotSupportedError(), "only ES256 is supported")
            }
            val caller = callerOf(this, request.callingAppInfo, asked.rpId)
                ?: return@runCatching CreatePublicKeyCredentialDomException(NotAllowedError(), "caller not allowed for ${asked.rpId}")
            val excluded = asked.excluded.map { it.b64url() }.toSet()
            val existing = Vault.passkeys().any {
                !it.isDeleted && it.relyingParty.lowercase() == asked.rpId && it.credentialId.hexBytes()?.b64url() in excluded
            }
            if (existing) {
                return@runCatching CreatePublicKeyCredentialDomException(InvalidStateError(), "passkey already saved")
            }
            val registration = Vault.createPasskey(asked.rpId, asked.userName, asked.displayName, asked.userHandle)
            val clientData = clientDataJson("webauthn.create", asked.challenge, caller.origin, caller.packageName)
            val json = registrationJson(registration.credentialId, registration.authenticatorData, clientData)
            PendingIntentHandler.setCreateCredentialResponse(result, CreatePublicKeyCredentialResponse(json))
            null
        }
        val failure = outcome.getOrNull() ?: outcome.exceptionOrNull()?.let { CreateCredentialUnknownException(it.message) }
        if (failure != null) PendingIntentHandler.setCreateCredentialException(result, failure)
        done(result)
    }

    private fun done(result: Intent) {
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        private const val VerificationWindow = 30_000L
        private var verifiedAt = 0L

        private fun consumeVerification(): Boolean {
            val recent = verifiedAt != 0L && SystemClock.elapsedRealtime() - verifiedAt < VerificationWindow
            verifiedAt = 0L
            return recent
        }

        const val ExtraMode = "io.lauren.renvault.passkey.MODE"
        const val ExtraPasskey = "io.lauren.renvault.passkey.ID"
        const val ModeUnlock = "unlock"
        const val ModeGet = "get"
        const val ModeCreate = "create"
    }
}
