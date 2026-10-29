package io.lauren.renvault.passkey

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import io.lauren.renvault.R
import io.lauren.renvault.bridge.PasskeyEntry
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class RvCredentialProviderService : CredentialProviderService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        PlatformStorage.attach(applicationContext)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onError(CreateCredentialUnknownException())
            return
        }
        val entry = CreateEntry.Builder(getString(R.string.app_name), passkeyIntent(this, PasskeyActivity.ModeCreate, null)).build()
        callback.onResult(BeginCreateCredentialResponse.Builder().addCreateEntry(entry).build())
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        scope.launch {
            val response = runCatching {
                runCatching { Vault.init() }
                if (!Vault.state().unlocked) {
                    val unlock = AuthenticationAction(
                        getString(R.string.passkey_unlock),
                        passkeyIntent(this@RvCredentialProviderService, PasskeyActivity.ModeUnlock, null),
                    )
                    BeginGetCredentialResponse.Builder().addAuthenticationAction(unlock).build()
                } else {
                    beginGetResponse(this@RvCredentialProviderService, request, Vault.passkeys())
                }
            }
            response.fold(callback::onResult) { callback.onError(GetCredentialUnknownException(it.message)) }
        }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun beginGetResponse(context: Context, request: BeginGetCredentialRequest, passkeys: List<PasskeyEntry>): BeginGetCredentialResponse {
    val builder = BeginGetCredentialResponse.Builder()
    val icon = Icon.createWithResource(context, R.drawable.autofill_inline_icon)
    for (option in request.beginGetCredentialOptions.filterIsInstance<BeginGetPublicKeyCredentialOption>()) {
        val asked = runCatching { parseAssertion(option.requestJson) }.getOrNull() ?: continue
        val allowed = asked.allowed.map { it.b64url() }.toSet()
        for (passkey in passkeys) {
            if (passkey.isDeleted || passkey.relyingParty.lowercase() != asked.rpId) continue
            val credential = passkey.credentialId.hexBytes() ?: continue
            if (allowed.isNotEmpty() && credential.b64url() !in allowed) continue
            val entry = PublicKeyCredentialEntry.Builder(
                context,
                passkey.username.ifEmpty { passkey.title },
                passkeyIntent(context, PasskeyActivity.ModeGet, passkey.id),
                option,
            )
                .setDisplayName(passkey.title)
                .setIcon(icon)
                .build()
            builder.addCredentialEntry(entry)
        }
    }
    return builder.build()
}

private var requestCodes = 0

internal fun passkeyIntent(context: Context, mode: String, passkeyId: String?): PendingIntent {
    val intent = Intent(context, PasskeyActivity::class.java)
        .putExtra(PasskeyActivity.ExtraMode, mode)
        .putExtra(PasskeyActivity.ExtraPasskey, passkeyId)
    return PendingIntent.getActivity(
        context,
        requestCodes++,
        intent,
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

internal fun String.hexBytes(): ByteArray? {
    if (length % 2 != 0 || isEmpty()) return null
    return runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
}
