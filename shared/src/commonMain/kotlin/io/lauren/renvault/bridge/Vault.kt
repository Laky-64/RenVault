@file:OptIn(ExperimentalSerializationApi::class)

package io.lauren.renvault.bridge

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import kotlin.random.Random

private const val STATUS_OK: Byte = 0

private val cbor = Cbor {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class VaultException(val failure: Failure) : Exception(failure.message)

object Vault {

    private val dispatcher: CoroutineDispatcher by lazy { ioDispatcher() }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun nextToken(): Long = Random.nextLong(1, Long.MAX_VALUE)

    fun cancel(token: Long) = Native.cancel(token)

    suspend fun init(base: String = PlatformStorage.base()) =
        send(Op.VAULT_INIT, InitRequest(base))

    suspend fun state(): VaultState = receive(Op.VAULT_STATE)

    suspend fun unlock(password: String) = send(Op.VAULT_UNLOCK, SecretRequest(password))

    suspend fun lock() = send(Op.VAULT_LOCK)

    suspend fun profile(): Profile = receive(Op.VAULT_PROFILE)

    suspend fun profilePhoto(): ByteArray = receive<BytesResponse>(Op.VAULT_PROFILE_PHOTO).data

    suspend fun settings(): Settings = receive(Op.VAULT_SETTINGS)

    suspend fun setSort(field: String, ascending: Boolean) =
        send(Op.VAULT_SET_SORT, SortRequest(field, ascending))

    suspend fun setAutoLock(minutes: Int) = send(Op.VAULT_SET_AUTO_LOCK, AutoLockRequest(minutes))

    suspend fun names(): List<NameSwap> = receive<NamesPayload>(Op.VAULT_NAMES).names

    suspend fun setNames(names: List<NameSwap>) = send(Op.VAULT_SET_NAMES, NamesPayload(names))

    suspend fun autoLockChoices(): List<Int> = receive<MinutesResponse>(Op.VAULT_AUTO_LOCK_CHOICES).minutes

    suspend fun signOut() = send(Op.VAULT_SIGN_OUT)

    suspend fun sync(): Boolean = receive<ChangedResponse>(Op.VAULT_SYNC).changed

    suspend fun syncState(): SyncState = receive(Op.VAULT_SYNC_STATE)

    suspend fun checkPwned(): PwnedReport = receive(Op.VAULT_CHECK_PWNED)

    suspend fun pwnedInfo(): PwnedReport = receive(Op.VAULT_PWNED_INFO)

    suspend fun web(): List<WebEntry> = receive(Op.LIST_WEB)

    suspend fun wifi(): List<WifiEntry> = receive(Op.LIST_WIFI)

    suspend fun passkeys(): List<PasskeyEntry> = receive(Op.LIST_PASSKEY)

    suspend fun password(id: String): String =
        exchange<IdentifierRequest, SecretResponse>(Op.GET_PASSWORD, IdentifierRequest(id)).secret

    suspend fun totp(id: String): String =
        exchange<IdentifierRequest, SecretResponse>(Op.GET_TOTP, IdentifierRequest(id)).secret

    suspend fun addPassword(
        title: String,
        website: String,
        username: String,
        password: String,
        note: String,
    ) = send(Op.ADD_PASSWORD, AddPasswordRequest(title, website, username, password, note))

    suspend fun editPassword(request: EditPassword): String =
        exchange<EditPasswordRequest, IdentifierResponse>(
            Op.EDIT_PASSWORD,
            EditPasswordRequest(
                request.id,
                request.title,
                request.website,
                request.username,
                request.password,
                request.note,
                request.totpUrl,
                request.domains,
                request.dropTotp,
            ),
        ).id

    suspend fun deletePasswords(ids: List<String>) =
        send(Op.DELETE_PASSWORDS, IdentifiersRequest(ids))

    suspend fun restorePassword(id: String) = send(Op.RESTORE_PASSWORD, IdentifierRequest(id))

    suspend fun purgePasswords(ids: List<String>) = send(Op.PURGE_PASSWORDS, IdentifiersRequest(ids))

    suspend fun changePasswordUrl(domain: String): String =
        exchange<DomainRequest, SecretResponse>(Op.CHANGE_PASSWORD_AT, DomainRequest(domain)).secret

    suspend fun icon(domain: String): ByteArray =
        exchange<DomainRequest, BytesResponse>(Op.ICON_TILE, DomainRequest(domain)).data

    suspend fun image(url: String): ByteArray =
        exchange<UrlRequest, BytesResponse>(Op.ICON_IMAGE, UrlRequest(url)).data

    suspend fun startLogin(appleId: String, password: String): Boolean =
        exchange<StartLoginRequest, StartLoginResponse>(
            Op.LOGIN_START,
            StartLoginRequest(appleId, password),
        ).needsCode

    suspend fun requestCode() = send(Op.LOGIN_CODE)

    suspend fun submitCode(code: String) = send(Op.LOGIN_SUBMIT, SubmitCodeRequest(code))

    suspend fun bottles(): List<TrustedDevice> = receive(Op.LOGIN_BOTTLES)

    suspend fun recoverBottle(index: Int, passcode: String) =
        send(Op.LOGIN_RECOVER, RecoverRequest(index, passcode))

    suspend fun finishSetup(password: String) = send(Op.LOGIN_FINISH, SecretRequest(password))

    suspend fun anisetteServer(): AnisetteServer = receive(Op.ANISETTE_SERVER)

    suspend fun setAnisetteServer(url: String) = send(Op.ANISETTE_SET_SERVER, AnisetteRequest(url))

    suspend fun listen() {
        while (currentCoroutineContext().isActive) {
            pump()
        }
    }

    private suspend fun pump() {
        val envelope = withContext(dispatcher) {
            val raw = Native.nextEvent(eventPollMillis) ?: return@withContext null
            if (raw.isEmpty()) return@withContext null
            cbor.decodeFromByteArray(EventEnvelope.serializer(), raw)
        } ?: return
        _events.emit(Event(envelope.kind, envelope.payload))
    }

    private suspend fun send(op: Int, token: Long = 0) {
        call(op, token, null)
    }

    private suspend inline fun <reified Q : Any> send(op: Int, request: Q, token: Long = 0) {
        call(op, token, cbor.encodeToByteArray(serializer<Q>(), request))
    }

    private suspend inline fun <reified R : Any> receive(op: Int, token: Long = 0): R {
        val body = call(op, token, null)
        return withContext(dispatcher) { cbor.decodeFromByteArray(serializer<R>(), body) }
    }

    private suspend inline fun <reified Q : Any, reified R : Any> exchange(
        op: Int,
        request: Q,
        token: Long = 0,
    ): R {
        val body = call(op, token, cbor.encodeToByteArray(serializer<Q>(), request))
        return withContext(dispatcher) { cbor.decodeFromByteArray(serializer<R>(), body) }
    }

    suspend fun call(op: Int, token: Long, request: ByteArray?): ByteArray {
        val response = withContext(dispatcher) { Native.call(op, token, request) }
        request?.fill(0)

        if (response.isEmpty()) {
            throw VaultException(Failure(FailureKind.UNKNOWN, "bridge: empty response"))
        }

        val body = response.copyOfRange(1, response.size)
        if (response[0] == STATUS_OK) return body

        val reported = runCatching {
            cbor.decodeFromByteArray(FailureResponse.serializer(), body)
        }.getOrElse { FailureResponse(FailureKind.UNKNOWN, "bridge: undecodable failure") }

        throw VaultException(Failure(reported.kind, reported.message))
    }
}

data class Event(val kind: Int, val payload: ByteArray) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = 31 * kind + payload.contentHashCode()
}

data class EditPassword(
    val id: String,
    val title: String = "",
    val website: String = "",
    val username: String = "",
    val password: String = "",
    val note: String = "",
    val totpUrl: String = "",
    val domains: List<String> = emptyList(),
    val dropTotp: Boolean = false,
)

private const val eventPollMillis = 250

internal expect fun ioDispatcher(): CoroutineDispatcher
