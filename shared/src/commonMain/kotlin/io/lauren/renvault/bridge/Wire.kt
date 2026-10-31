package io.lauren.renvault.bridge

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class InitRequest(
    @SerialName("base") val base: String,
    @ByteString @SerialName("secret") val secret: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = base.hashCode()
}

@Serializable
internal data class SecretRequest(@SerialName("secret") val secret: String)

@Serializable
internal data class IdentifierRequest(@SerialName("id") val id: String)

@Serializable
internal data class IdentifiersRequest(@SerialName("ids") val ids: List<String>)

@Serializable
internal data class DomainRequest(@SerialName("domain") val domain: String)

@Serializable
internal data class SortRequest(
    @SerialName("field") val field: String,
    @SerialName("ascending") val ascending: Boolean,
)

@Serializable
data class NameSwap(
    @SerialName("from") val from: String = "",
    @SerialName("to") val to: String = "",
)

@Serializable
internal data class NamesPayload(@SerialName("names") val names: List<NameSwap> = emptyList())

@Serializable
internal data class AutoLockRequest(@SerialName("minutes") val minutes: Int)

@Serializable
internal data class StartLoginRequest(
    @SerialName("apple_id") val appleId: String,
    @SerialName("password") val password: String,
)

@Serializable
internal data class UrlRequest(@SerialName("url") val url: String)

@Serializable
internal data class AnisetteRequest(@SerialName("url") val url: String)

@Serializable
data class AnisetteServer(
    @SerialName("url") val url: String = "",
    @SerialName("default_list") val defaultList: String = "",
)

@Serializable
data class AnisetteEntry(
    @SerialName("name") val name: String = "",
    @SerialName("address") val address: String = "",
    @SerialName("kind") val kind: Int = 0,
    @SerialName("latency_ms") val latencyMs: Long = 0,
    @SerialName("reachable") val reachable: Boolean = false,
)

@Serializable
internal data class SubmitCodeRequest(@SerialName("code") val code: String)

@Serializable
internal data class RecoverRequest(
    @SerialName("index") val index: Int,
    @SerialName("passcode") val passcode: String,
)

@Serializable
internal data class AddPasswordRequest(
    @SerialName("title") val title: String,
    @SerialName("website") val website: String,
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("note") val note: String,
)

@Serializable
internal data class EditPasskeyRequest(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("username") val username: String,
    @SerialName("note") val note: String,
    @SerialName("sites") val sites: List<String>,
)

@Serializable
internal data class EditPasswordRequest(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("website") val website: String,
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("note") val note: String,
    @SerialName("totp_url") val totpUrl: String,
    @SerialName("domains") val domains: List<String>,
    @SerialName("drop_totp") val dropTotp: Boolean,
)

@Serializable
data class VaultState(
    @SerialName("configured") val configured: Boolean = false,
    @SerialName("unlocked") val unlocked: Boolean = false,
)

@Serializable
internal data class StartLoginResponse(
    @SerialName("needs_code") val needsCode: Boolean = false,
)

@Serializable
internal data class IdentifierResponse(@SerialName("id") val id: String = "")

@Serializable
internal data class SecretResponse(@SerialName("secret") val secret: String = "")

@Serializable
internal data class ChangedResponse(@SerialName("changed") val changed: Boolean = false)

@Serializable
internal data class MinutesResponse(@SerialName("minutes") val minutes: List<Int> = emptyList())

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class BytesResponse(
    @ByteString @SerialName("data") val data: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = data.contentHashCode()
}

@Serializable
internal data class FailureResponse(
    @SerialName("kind") val kind: Int = FailureKind.UNKNOWN,
    @SerialName("message") val message: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class EventEnvelope(
    @SerialName("kind") val kind: Int = 0,
    @ByteString @SerialName("payload") val payload: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = 31 * kind + payload.contentHashCode()
}

@Serializable
data class Settings(
    @SerialName("sort_field") val sortField: String = "title",
    @SerialName("sort_ascending") val sortAscending: Boolean = true,
    @SerialName("auto_lock_minutes") val autoLockMinutes: Int = 5,
)

@Serializable
data class Profile(
    @SerialName("name") val name: String = "",
    @SerialName("apple_id") val appleId: String = "",
    @SerialName("has_photo") val hasPhoto: Boolean = false,
    @SerialName("local") val local: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class LocalSetupRequest(
    @SerialName("secret") val secret: String,
    @SerialName("name") val name: String,
    @SerialName("device") val device: String,
    @ByteString @SerialName("photo") val photo: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = photo.contentHashCode()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ProfileRequest(
    @SerialName("name") val name: String,
    @ByteString @SerialName("photo") val photo: ByteArray,
    @SerialName("set_photo") val setPhoto: Boolean,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = photo.contentHashCode()
}

@Serializable
data class Device(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("self") val self: Boolean = false,
    @SerialName("synced_at") val syncedAt: Long = 0,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Pairing(
    @SerialName("code") val code: String = "",
    @SerialName("payload") val payload: String = "",
    @SerialName("size") val size: Int = 0,
    @ByteString @SerialName("modules") val modules: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = code.hashCode()
}

@Serializable
internal data class PickRequest(
    @SerialName("title") val title: String,
    @SerialName("filter") val filter: String,
)

@Serializable
data class FileFilter(
    @SerialName("name") val name: String,
    @SerialName("extensions") val extensions: List<String>,
    @SerialName("types") val types: List<String>,
)

@Serializable
internal data class PickFileRequest(
    @SerialName("title") val title: String,
    @SerialName("filter") val filter: FileFilter,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SaveFileRequest(
    @SerialName("title") val title: String,
    @SerialName("name") val name: String,
    @SerialName("filter") val filter: FileFilter,
    @ByteString @SerialName("data") val data: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = data.contentHashCode()
}

@Serializable
internal data class SavedResponse(@SerialName("saved") val saved: Boolean = false)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class BackupRequest(
    @SerialName("password") val password: String,
    @ByteString @SerialName("data") val data: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = data.contentHashCode()
}

@Serializable
data class ImportResult(
    @SerialName("added") val added: Int = 0,
    @SerialName("updated") val updated: Int = 0,
    @SerialName("skipped") val skipped: Int = 0,
)

@Serializable
internal data class JoinRequest(@SerialName("text") val text: String)

@Serializable
internal data class NameResponse(@SerialName("name") val name: String = "")

@Serializable
data class NetInfo(
    @SerialName("node") val node: String = "",
    @SerialName("port") val port: Int = 0,
)

@Serializable
internal data class AddressesRequest(@SerialName("ips") val ips: List<String>)

@Serializable
internal data class PeerSeenRequest(
    @SerialName("node") val node: String,
    @SerialName("host") val host: String,
    @SerialName("port") val port: Int,
)

@Serializable
data class SyncState(
    @SerialName("pending") val pending: Int = 0,
    @SerialName("synced_at") val syncedAt: Long = 0,
    @SerialName("error") val error: String = "",
)

@Serializable
data class PwnedReport(
    @SerialName("checked") val checked: Int = 0,
    @SerialName("pwned") val pwned: Int = 0,
    @SerialName("checked_at") val checkedAt: Long = 0,
)

@Serializable
data class Group(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
)

@Serializable
data class WebEntry(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("username") val username: String = "",
    @SerialName("domain") val domain: String = "",
    @SerialName("domains") val domains: List<String> = emptyList(),
    @SerialName("website") val website: Boolean = false,
    @SerialName("has_totp") val hasTotp: Boolean = false,
    @SerialName("note") val note: String = "",
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("groups") val groups: List<Group> = emptyList(),
    @SerialName("shared") val shared: Boolean = false,
    @SerialName("pwned") val pwned: Boolean = false,
    @SerialName("has_password") val hasPassword: Boolean = false,
    @SerialName("created") val created: Long = 0,
    @SerialName("modified") val modified: Long = 0,
    @SerialName("deleted") val deleted: Long = 0,
)

@Serializable
data class WifiEntry(
    @SerialName("id") val id: String = "",
    @SerialName("ssid") val ssid: String = "",
    @SerialName("created") val created: Long = 0,
    @SerialName("modified") val modified: Long = 0,
)

@Serializable
data class PasskeyEntry(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("custom_title") val customTitle: String = "",
    @SerialName("site_title") val siteTitle: String = "",
    @SerialName("relying_party") val relyingParty: String = "",
    @SerialName("username") val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("note") val note: String = "",
    @SerialName("sites") val sites: List<String>? = null,
    @SerialName("credential_id") val credentialId: String = "",
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("created") val created: Long = 0,
    @SerialName("modified") val modified: Long = 0,
    @SerialName("deleted") val deleted: Long = 0,
)

@Serializable
data class TrustedDevice(
    @SerialName("index") val index: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("model") val model: String = "",
    @SerialName("short_model") val shortModel: String = "",
    @SerialName("class") val deviceClass: String = "",
    @SerialName("os") val os: String = "",
    @SerialName("os_version") val osVersion: String = "",
    @SerialName("image_url") val imageUrl: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class QuickRequest(
    @SerialName("method") val method: String,
    @ByteString @SerialName("key") val key: ByteArray = ByteArray(0),
    @ByteString @SerialName("sealed") val sealed: ByteArray = ByteArray(0),
    @SerialName("credential") val credential: Boolean = false,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = key.contentHashCode()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class QuickState(
    @SerialName("methods") val methods: List<String> = emptyList(),
    @SerialName("method") val method: String = "",
    @ByteString @SerialName("sealed") val sealed: ByteArray = ByteArray(0),
    @SerialName("ready") val ready: Boolean = false,
    @SerialName("expired") val expired: Boolean = false,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = method.hashCode()
}

@Serializable
internal data class SecureRequest(@SerialName("on") val on: Boolean)

@Serializable
internal data class TitleBarRequest(
    @SerialName("dark") val dark: Boolean,
    @SerialName("caption") val caption: Int,
    @SerialName("text") val text: Int,
)

@Serializable
data class TrayItem(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class TrayIcon(@ByteString @SerialName("png") val png: ByteArray) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = png.contentHashCode()
}

@Serializable
internal data class TrayRequest(
    @SerialName("tooltip") val tooltip: String,
    @SerialName("icons") val icons: List<TrayIcon>,
    @SerialName("iconsOnLight") val iconsOnLight: List<TrayIcon>,
    @SerialName("items") val items: List<TrayItem>,
)

@Serializable
internal data class TrayEvent(@SerialName("item") val item: String = "")

@Serializable
internal data class RaiseRequest(@SerialName("token") val token: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class CreatePasskeyRequest(
    @SerialName("relying_party") val relyingParty: String,
    @SerialName("username") val username: String,
    @SerialName("display_name") val displayName: String,
    @ByteString @SerialName("user_handle") val userHandle: ByteArray,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class PasskeyRegistration(
    @ByteString @SerialName("credential_id") val credentialId: ByteArray = ByteArray(0),
    @ByteString @SerialName("authenticator_data") val authenticatorData: ByteArray = ByteArray(0),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SignPasskeyRequest(
    @SerialName("id") val id: String,
    @ByteString @SerialName("client_data_hash") val clientDataHash: ByteArray,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class PasskeyAssertion(
    @ByteString @SerialName("authenticator_data") val authenticatorData: ByteArray = ByteArray(0),
    @ByteString @SerialName("signature") val signature: ByteArray = ByteArray(0),
    @ByteString @SerialName("user_handle") val userHandle: ByteArray = ByteArray(0),
)

@Serializable
internal data class HybridStartRequest(
    @SerialName("qr") val qr: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class HybridStart(
    @SerialName("session") val session: String = "",
    @ByteString @SerialName("advert") val advert: ByteArray = ByteArray(0),
)

@Serializable
internal data class HybridDecideRequest(
    @SerialName("id") val id: String,
    @SerialName("approve") val approve: Boolean,
    @SerialName("account") val account: Int,
)

@Serializable
internal data class HybridSessionRequest(
    @SerialName("session") val session: String,
)

@Serializable
data class HybridProgress(
    @SerialName("session") val session: String = "",
    @SerialName("stage") val stage: String = "",
    @SerialName("error") val error: String = "",
    @SerialName("command") val command: Int = 0,
    @SerialName("status") val status: Int = 0,
)

@Serializable
data class HybridAccount(
    @SerialName("title") val title: String = "",
    @SerialName("username") val username: String = "",
)

@Serializable
data class HybridPrompt(
    @SerialName("id") val id: String = "",
    @SerialName("session") val session: String = "",
    @SerialName("relying_party") val relyingParty: String = "",
    @SerialName("create") val create: Boolean = false,
    @SerialName("username") val username: String = "",
    @SerialName("accounts") val accounts: List<HybridAccount> = emptyList(),
)

@Serializable
data class LocalKeyStatus(
    @SerialName("supported") val supported: Boolean = false,
    @SerialName("permitted") val permitted: Boolean = false,
    @SerialName("running") val running: Boolean = false,
)

@Serializable
internal data class NotifyRequest(
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
)

@Serializable
internal data class NotifyId(
    @SerialName("id") val id: Long = 0,
)
