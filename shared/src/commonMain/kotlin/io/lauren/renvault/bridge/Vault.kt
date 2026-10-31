@file:OptIn(ExperimentalSerializationApi::class)

package io.lauren.renvault.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        send(Op.VAULT_INIT, InitRequest(base, PlatformStorage.secret() ?: ByteArray(0)))

    suspend fun state(): VaultState = receive(Op.VAULT_STATE)

    suspend fun unlock(password: String) = send(Op.VAULT_UNLOCK, SecretRequest(password))

    suspend fun lock() = send(Op.VAULT_LOCK)

    suspend fun localKeyStatus(): LocalKeyStatus = receive(Op.LOCAL_KEY_STATUS)

    suspend fun localKeyStart() = send(Op.LOCAL_KEY_START)

    suspend fun localKeyStop() = send(Op.LOCAL_KEY_STOP)

    suspend fun localKeyGrant() = send(Op.LOCAL_KEY_GRANT)

    suspend fun winPasskeyStatus(): WinPasskeyStatus = receive(Op.WIN_PASSKEY_STATUS)

    suspend fun winPasskeyEnable(): WinPasskeyEnable = receive(Op.WIN_PASSKEY_ENABLE)

    suspend fun winPasskeySync() = send(Op.WIN_PASSKEY_SYNC)

    suspend fun winPasskeySettings() = send(Op.WIN_PASSKEY_SETTINGS)

    suspend fun winPasskeyRestart() = send(Op.WIN_PASSKEY_RESTART)

    suspend fun notify(title: String, body: String): Long =
        exchange<NotifyRequest, NotifyId>(Op.NOTIFY_SHOW, NotifyRequest(title, body)).id

    suspend fun closeNotification(id: Long) = send(Op.NOTIFY_CLOSE, NotifyId(id))

    suspend fun quickState(): QuickState = receive(Op.QUICK_STATE)

    suspend fun enableQuick(
        method: String,
        key: ByteArray = ByteArray(0),
        sealed: ByteArray = ByteArray(0),
        credential: Boolean = false,
    ) = send(Op.QUICK_ENABLE, QuickRequest(method, key, sealed, credential))

    suspend fun disableQuick() = send(Op.QUICK_DISABLE)

    suspend fun unlockQuick(method: String, key: ByteArray = ByteArray(0)) =
        send(Op.QUICK_UNLOCK, QuickRequest(method, key))

    suspend fun profile(): Profile = receive(Op.VAULT_PROFILE)

    suspend fun profilePhoto(): ByteArray = receive<BytesResponse>(Op.VAULT_PROFILE_PHOTO).data

    suspend fun settings(): Settings = receive(Op.VAULT_SETTINGS)

    suspend fun setSort(field: String, ascending: Boolean) =
        send(Op.VAULT_SET_SORT, SortRequest(field, ascending))

    suspend fun setAutoLock(minutes: Int) = send(Op.VAULT_SET_AUTO_LOCK, AutoLockRequest(minutes))

    suspend fun names(): List<NameSwap> = receive<NamesPayload>(Op.VAULT_NAMES).names

    suspend fun setNames(names: List<NameSwap>) = send(Op.VAULT_SET_NAMES, NamesPayload(names))

    suspend fun localSetup(password: String, name: String, device: String, photo: ByteArray?) =
        send(Op.VAULT_LOCAL_SETUP, LocalSetupRequest(password, name, device, photo ?: ByteArray(0)))

    suspend fun setProfile(name: String, photo: ByteArray?, setPhoto: Boolean) =
        send(Op.VAULT_SET_PROFILE, ProfileRequest(name, photo ?: ByteArray(0), setPhoto))

    suspend fun devices(): List<Device> = receive(Op.DEVICES)

    suspend fun removeDevice(id: String) = send(Op.DEVICE_REMOVE, IdentifierRequest(id))

    suspend fun startPairing(): Pairing = receive(Op.PAIR_START)

    suspend fun waitPairing(): String = withToken { token ->
        receive<NameResponse>(Op.PAIR_WAIT, token).name
    }

    suspend fun joinPairing(text: String): String = withToken { token ->
        exchange<JoinRequest, NameResponse>(Op.PAIR_JOIN, JoinRequest(text), token).name
    }

    suspend fun pickImage(title: String, filter: String): ByteArray = withToken { token ->
        exchange<PickRequest, BytesResponse>(Op.PICK_IMAGE, PickRequest(title, filter), token).data
    }

    suspend fun pickFile(title: String, filter: FileFilter): ByteArray = withToken { token ->
        exchange<PickFileRequest, BytesResponse>(Op.PICK_FILE, PickFileRequest(title, filter), token).data
    }

    suspend fun saveFile(title: String, name: String, filter: FileFilter, data: ByteArray): Boolean = withToken { token ->
        exchange<SaveFileRequest, SavedResponse>(Op.SAVE_FILE, SaveFileRequest(title, name, filter, data), token).saved
    }

    suspend fun exportBackup(password: String): ByteArray =
        exchange<BackupRequest, BytesResponse>(Op.BACKUP_EXPORT, BackupRequest(password, ByteArray(0))).data

    suspend fun importBackup(data: ByteArray, password: String): ImportResult =
        exchange(Op.BACKUP_IMPORT, BackupRequest(password, data))

    suspend fun importCsv(data: ByteArray): ImportResult =
        exchange(Op.IMPORT_CSV, BackupRequest("", data))

    suspend fun setSecure(on: Boolean) = send(Op.SECURE, SecureRequest(on))

    suspend fun setTitleBar(dark: Boolean, caption: Int, text: Int) =
        send(Op.TITLE_BAR, TitleBarRequest(dark, caption, text))

    suspend fun showTray(tooltip: String, icons: List<ByteArray>, iconsOnLight: List<ByteArray>, items: List<TrayItem>) =
        send(Op.TRAY, TrayRequest(tooltip, icons.map(::TrayIcon), iconsOnLight.map(::TrayIcon), items))

    suspend fun hideTray() = send(Op.TRAY_HIDE)

    suspend fun raiseWindow(token: String = "") = send(Op.RAISE, RaiseRequest(token))

    fun trayItem(event: Event): String = cbor.decodeFromByteArray(TrayEvent.serializer(), event.payload).item

    suspend fun syncPeers() = send(Op.PEERS_SYNC)

    suspend fun netInfo(): NetInfo = receive(Op.NET_INFO)

    suspend fun setAddresses(ips: List<String>) = send(Op.NET_ADDRESSES, AddressesRequest(ips))

    suspend fun peerSeen(node: String, host: String, port: Int) =
        send(Op.NET_PEER_SEEN, PeerSeenRequest(node, host, port))

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

    suspend fun deletePasskeys(ids: List<String>) = send(Op.DELETE_PASSKEYS, IdentifiersRequest(ids))

    suspend fun restorePasskey(id: String) = send(Op.RESTORE_PASSKEY, IdentifierRequest(id))

    suspend fun purgePasskeys(ids: List<String>) = send(Op.PURGE_PASSKEYS, IdentifiersRequest(ids))

    suspend fun createPasskey(relyingParty: String, username: String, displayName: String, userHandle: ByteArray): PasskeyRegistration =
        exchange(Op.CREATE_PASSKEY, CreatePasskeyRequest(relyingParty, username, displayName, userHandle))

    suspend fun signPasskey(id: String, clientDataHash: ByteArray): PasskeyAssertion =
        exchange(Op.SIGN_PASSKEY, SignPasskeyRequest(id, clientDataHash))

    suspend fun hybridStart(qr: String): HybridStart = exchange(Op.HYBRID_START, HybridStartRequest(qr))

    suspend fun hybridDecide(id: String, approve: Boolean, account: Int) =
        send(Op.HYBRID_DECIDE, HybridDecideRequest(id, approve, account))

    suspend fun hybridCancel(session: String) = send(Op.HYBRID_CANCEL, HybridSessionRequest(session))

    fun hybridProgress(event: Event): HybridProgress? =
        if (event.kind == EventKind.AGENT_PROGRESS) runCatching { cbor.decodeFromByteArray(HybridProgress.serializer(), event.payload) }.getOrNull() else null

    fun hybridPrompt(event: Event): HybridPrompt? =
        if (event.kind == EventKind.SIGN_REQUESTED) runCatching { cbor.decodeFromByteArray(HybridPrompt.serializer(), event.payload) }.getOrNull() else null

    suspend fun editPasskey(id: String, title: String, username: String, note: String, sites: List<String>): String =
        exchange<EditPasskeyRequest, IdentifierResponse>(
            Op.EDIT_PASSKEY,
            EditPasskeyRequest(id, title, username, note, sites),
        ).id

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

    suspend fun anisetteServers(): List<AnisetteEntry> = receive(Op.ANISETTE_SERVERS)

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

    private suspend fun <T> withToken(block: suspend (Long) -> T): T {
        val token = nextToken()
        return coroutineScope {
            val running = async { block(token) }
            try {
                running.await()
            } catch (cancelled: CancellationException) {
                Native.cancel(token)
                throw cancelled
            }
        }
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
