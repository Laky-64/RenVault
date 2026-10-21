package io.lauren.renvault.lib

import io.lauren.renvault.bridge.PasskeyEntry
import io.lauren.renvault.bridge.WebEntry
import io.lauren.renvault.bridge.WifiEntry

const val DeletedDays = 30

sealed interface RvItem {
    val id: String
    val created: Long
    val modified: Long
    val isDeleted: Boolean
}

data class RvWebItem(
    val entry: WebEntry,
    val passkey: RvPasskeyItem? = null,
) : RvItem {
    override val id: String get() = entry.id
    override val created: Long get() = entry.created
    override val modified: Long get() = entry.modified
    override val isDeleted: Boolean get() = entry.isDeleted
}

data class RvWifiItem(val entry: WifiEntry) : RvItem {
    override val id: String get() = entry.id
    override val created: Long get() = entry.created
    override val modified: Long get() = entry.modified
    override val isDeleted: Boolean get() = false
}

data class RvPasskeyItem(
    val entry: PasskeyEntry,
    val linked: RvWebItem? = null,
) : RvItem {
    override val id: String get() = entry.id
    override val created: Long get() = entry.created
    override val modified: Long get() = entry.modified
    override val isDeleted: Boolean get() = entry.isDeleted
}

internal fun normalized(value: String): String = value.trim().lowercase()

internal fun hostOf(value: String): String = normalized(value).removePrefix("www.")

private val Scheme = Regex("^[a-z][a-z0-9+.\\-]*:/*")
private val StrictScheme = Regex("^[a-z][a-z0-9+.\\-]*://")
private val Credentials = Regex("^[^/@]*@")
private val Port = Regex(":\\d+$")
private val TrailingSlashes = Regex("/+$")
private val OuterDots = Regex("^\\.+|\\.+$")
private val PathBreak = Regex("[/?#\\\\]")
private val Ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

private fun labelOk(label: String, tld: Boolean): Boolean {
    if (label.isEmpty() || label.length > 63) return false
    if (label.first() == '-' || label.last() == '-') return false
    if (tld) return label.all { it.isLetter() }
    return label.all { it.isLetter() || it.isDigit() || it == '-' }
}

fun isDomain(value: String): Boolean {
    if (value.isEmpty() || value.length > 253) return false

    val quad = Ipv4.matchEntire(value)
    if (quad != null) {
        return quad.groupValues.drop(1).all { part ->
            val number = part.toIntOrNull() ?: return@all false
            number <= 255 && (part == "0" || !part.startsWith("0"))
        }
    }

    val labels = value.split('.')
    if (labels.size < 2) return false
    val tld = labels.last()
    if (tld.length < 2 || tld.length > 63) return false
    return labels.dropLast(1).all { labelOk(it, tld = false) } && labelOk(tld, tld = true)
}

fun bareHost(value: String): String =
    normalized(value)
        .replace(Scheme, "")
        .removePrefix("www.")
        .replace(TrailingSlashes, "")

fun domainOf(value: String): String {
    var host = normalized(value)
        .replace(StrictScheme, "")
        .replace(Credentials, "")
    host = host.split(PathBreak, limit = 2).first()
        .replace(Port, "")
        .replace(OuterDots, "")
    return hostOf(host)
}

private fun hostsOf(item: RvWebItem): List<String> =
    (listOf(item.entry.domain) + item.entry.domains).map(::hostOf).filter { it.isNotEmpty() }

private fun linkKey(host: String, username: String): String = "$host\n${normalized(username)}"

data class RvLinkedItems(val web: List<RvWebItem>, val passkeys: List<RvPasskeyItem>)

fun linkItems(web: List<RvWebItem>, passkeys: List<RvPasskeyItem>): RvLinkedItems {
    val byHost = HashMap<String, RvWebItem>()
    for (entry in web) {
        if (entry.entry.isDeleted) continue
        for (host in hostsOf(entry)) {
            byHost.getOrPut(linkKey(host, entry.entry.username)) { entry }
        }
    }

    val byWebId = HashMap<String, RvPasskeyItem>()
    val linkedPasskeys = passkeys.map { passkey ->
        if (passkey.entry.isDeleted) return@map passkey
        val match = byHost[linkKey(hostOf(passkey.entry.relyingParty), passkey.entry.username)]
            ?: return@map passkey
        byWebId.getOrPut(match.entry.id) { passkey }
        passkey.copy(linked = match.copy(passkey = passkey))
    }

    val linkedWeb = web.map { entry ->
        val match = byWebId[entry.entry.id]
        if (match == null) entry else entry.copy(passkey = match)
    }

    return RvLinkedItems(linkedWeb, linkedPasskeys)
}

fun labelOf(item: RvWebItem): String {
    val entry = item.entry
    val passkeyTitle = item.passkey?.entry?.title.orEmpty()
    if (passkeyTitle.isNotEmpty()) return passkeyTitle
    if (entry.title.isNotEmpty()) return entry.title
    if (entry.website && entry.domain.isNotEmpty()) return entry.domain
    return entry.username
}

fun siteOf(item: RvWebItem): String = if (item.entry.website) item.entry.domain else ""

fun editableOf(item: RvItem?): RvWebItem? = when (item) {
    null -> null
    is RvWebItem -> if (item.entry.isDeleted) null else item
    is RvPasskeyItem -> if (item.entry.isDeleted) null else item.linked
    is RvWifiItem -> null
}

sealed interface RvItemIcon {
    data class Favicon(val domain: String, val fallback: String) : RvItemIcon
    data object WifiGlyph : RvItemIcon
}

data class RvItemView(
    val title: String,
    val subtitle: String,
    val icon: RvItemIcon,
    val hasTotp: Boolean,
)

fun viewOf(item: RvItem, strings: RvStrings): RvItemView = when (item) {
    is RvWebItem -> {
        val label = labelOf(item)
        RvItemView(
            title = label,
            subtitle = if (label == item.entry.username) "" else item.entry.username,
            icon = RvItemIcon.Favicon(domain = siteOf(item), fallback = label),
            hasTotp = item.entry.hasTotp,
        )
    }

    is RvWifiItem -> RvItemView(
        title = item.entry.ssid,
        subtitle = strings.itemWifiNetwork,
        icon = RvItemIcon.WifiGlyph,
        hasTotp = false,
    )

    is RvPasskeyItem -> RvItemView(
        title = item.entry.title,
        subtitle = item.entry.username,
        icon = RvItemIcon.Favicon(domain = item.entry.relyingParty, fallback = item.entry.title),
        hasTotp = false,
    )
}

sealed interface RvFieldValue {
    data class Shown(val text: String) : RvFieldValue
    data class Secret(val load: suspend () -> String) : RvFieldValue
    data class Code(val load: suspend () -> String) : RvFieldValue
}

data class RvDetailField(
    val label: String,
    val value: RvFieldValue,
    val copyable: Boolean = false,
    val wrap: Boolean = false,
    val bare: Boolean = false,
)

val RvDetailField.secret: (suspend () -> String)?
    get() = (value as? RvFieldValue.Secret)?.load

val RvDetailField.code: (suspend () -> String)?
    get() = (value as? RvFieldValue.Code)?.load

val RvDetailField.plain: String?
    get() = (value as? RvFieldValue.Shown)?.text

interface RvSecretSource {
    suspend fun password(id: String): String
    suspend fun totp(id: String): String
}

data class RvItemDetail(val title: String, val fields: List<RvDetailField>)

private fun websiteLabel(item: RvWebItem, strings: RvStrings): String {
    val others = item.entry.domains.filter { it.isNotEmpty() && it != item.entry.domain }
    if (others.isEmpty()) return item.entry.domain
    return strings.fieldWebsiteMore(item.entry.domain, others.size)
}

private fun MutableList<RvDetailField>.pushNote(note: String, strings: RvStrings) {
    if (note.isBlank()) return
    add(
        RvDetailField(
            label = strings.fieldNotes,
            value = RvFieldValue.Shown(note),
            wrap = true,
            bare = true,
        ),
    )
}

private fun MutableList<RvDetailField>.pushModified(modified: Long, strings: RvStrings) {
    val shown = formatDate(modified)
    if (shown.isEmpty()) return
    add(RvDetailField(label = strings.fieldModified, value = RvFieldValue.Shown(shown)))
}

private fun MutableList<RvDetailField>.pushPasskeyCreated(created: Long, strings: RvStrings) {
    val date = formatDate(created)
    if (date.isEmpty()) return
    add(
        RvDetailField(
            label = strings.fieldPasskey,
            value = RvFieldValue.Shown(strings.fieldPasskeyCreationDate(date)),
        ),
    )
}

fun detailOf(
    item: RvItem,
    secrets: RvSecretSource,
    strings: RvStrings,
): RvItemDetail = when (item) {
    is RvWebItem -> {
        val fields = mutableListOf(
            RvDetailField(
                label = strings.fieldUsername,
                value = RvFieldValue.Shown(item.entry.username),
                copyable = true,
            ),
        )
        if (item.entry.hasPassword) {
            fields += RvDetailField(
                label = strings.fieldPassword,
                value = RvFieldValue.Secret { secrets.password(item.entry.id) },
                copyable = true,
            )
        }
        item.passkey?.let { fields.pushPasskeyCreated(it.entry.created, strings) }
        if (item.entry.website) {
            fields += RvDetailField(
                label = strings.fieldWebsite,
                value = RvFieldValue.Shown(websiteLabel(item, strings)),
            )
        }
        if (item.entry.hasTotp) {
            fields += RvDetailField(
                label = strings.fieldVerificationCode,
                value = RvFieldValue.Code { secrets.totp(item.entry.id) },
                copyable = true,
            )
        }
        fields.pushModified(item.entry.modified, strings)
        fields.pushNote(item.entry.note, strings)
        RvItemDetail(title = labelOf(item), fields = fields)
    }

    is RvWifiItem -> {
        val fields = mutableListOf(
            RvDetailField(
                label = strings.fieldNetworkName,
                value = RvFieldValue.Shown(item.entry.ssid),
                copyable = true,
            ),
            RvDetailField(
                label = strings.fieldPassword,
                value = RvFieldValue.Secret { secrets.password(item.entry.id) },
                copyable = true,
            ),
        )
        fields.pushModified(item.entry.modified, strings)
        RvItemDetail(title = item.entry.ssid, fields = fields)
    }

    is RvPasskeyItem -> {
        val linked = item.linked
        val fields = mutableListOf(
            RvDetailField(
                label = strings.fieldUsername,
                value = RvFieldValue.Shown(item.entry.username),
                copyable = true,
            ),
        )
        if (linked != null) {
            fields += RvDetailField(
                label = strings.fieldPassword,
                value = RvFieldValue.Secret { secrets.password(linked.entry.id) },
                copyable = true,
            )
        }
        fields.pushPasskeyCreated(item.entry.created, strings)
        if (linked != null) {
            if (linked.entry.website) {
                fields += RvDetailField(
                    label = strings.fieldWebsite,
                    value = RvFieldValue.Shown(websiteLabel(linked, strings)),
                )
            }
            if (linked.entry.hasTotp) {
                fields += RvDetailField(
                    label = strings.fieldVerificationCode,
                    value = RvFieldValue.Code { secrets.totp(linked.entry.id) },
                    copyable = true,
                )
            }
            fields.pushModified(linked.entry.modified, strings)
            fields.pushNote(linked.entry.note, strings)
        } else {
            fields.pushModified(item.entry.modified, strings)
        }
        RvItemDetail(title = item.entry.title, fields = fields)
    }
}

private fun haystack(item: RvItem): List<String> = when (item) {
    is RvWebItem -> listOf(labelOf(item), item.entry.title, item.entry.username, item.entry.domain) +
        item.entry.domains

    is RvPasskeyItem -> listOf(item.entry.title, item.entry.username, item.entry.relyingParty)
    is RvWifiItem -> listOf(item.entry.ssid)
}

fun matchesQuery(item: RvItem, query: String): Boolean {
    val needle = normalized(query)
    if (needle.isEmpty()) return true
    return haystack(item).any { it.isNotEmpty() && normalized(it).contains(needle) }
}
