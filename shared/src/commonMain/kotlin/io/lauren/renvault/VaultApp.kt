package io.lauren.renvault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.lauren.renvault.bridge.EventKind
import io.lauren.renvault.bridge.EditPassword
import io.lauren.renvault.bridge.Profile
import io.lauren.renvault.bridge.Settings
import io.lauren.renvault.bridge.SyncState
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.RvDraft
import io.lauren.renvault.lib.RvPasskeyItem
import io.lauren.renvault.lib.RvSecretSource
import io.lauren.renvault.lib.RvSortField
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.RvWebItem
import io.lauren.renvault.lib.RvWifiItem
import io.lauren.renvault.lib.linkItems
import io.lauren.renvault.ui.LocalStrings
import io.lauren.renvault.ui.components.RvAuthDevice
import io.lauren.renvault.ui.components.RvAuthFlow
import io.lauren.renvault.ui.components.RvAuthProfile
import io.lauren.renvault.ui.components.RvEditorDraft
import io.lauren.renvault.ui.components.RvFavicon
import io.lauren.renvault.ui.components.RvHomePage
import io.lauren.renvault.ui.components.RvSettingsState
import io.lauren.renvault.ui.components.RvVaultShell
import io.lauren.renvault.ui.components.fitted
import io.lauren.renvault.ui.components.homeZones
import io.lauren.renvault.ui.components.rememberClipboardCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun RvVaultApp(
    modifier: Modifier = Modifier,
    title: String = "RenVault",
    version: String = RvAppVersion,
    strings: RvStrings = LocalStrings.current,
    titleBar: @Composable (() -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
) {
    val links = LocalUriHandler.current
    val openUrl: (String) -> Unit = onOpenUrl ?: { target -> runCatching { links.openUri(target) } }

    val scope = rememberCoroutineScope()
    val copy = rememberClipboardCopy()
    val tile = with(LocalDensity.current) { TileSize.toPx() }.roundToInt()

    var ready by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf(emptyList<RvWebItem>()) }
    var wifi by remember { mutableStateOf(emptyList<RvWifiItem>()) }
    var passkeys by remember { mutableStateOf(emptyList<RvPasskeyItem>()) }
    var profile by remember { mutableStateOf(Profile()) }
    var settings by remember { mutableStateOf(Settings()) }
    var sync by remember { mutableStateOf(SyncState()) }
    var choices by remember { mutableStateOf(emptyList<Int>()) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var revision by remember { mutableStateOf(0) }
    var anisette by remember { mutableStateOf("") }

    val icons = remember { mutableStateMapOf<String, Painter?>() }
    val wanted = remember { mutableStateListOf<String>() }

    suspend fun loadItems() {
        val loadedWeb = runCatching { Vault.web() }.getOrDefault(emptyList())
        val loadedWifi = runCatching { Vault.wifi() }.getOrDefault(emptyList())
        val loadedKeys = runCatching { Vault.passkeys() }.getOrDefault(emptyList())
        val loadedSync = runCatching { Vault.syncState() }.getOrDefault(SyncState())

        val wrapped = withContext(Dispatchers.Default) {
            Triple(
                loadedWeb.map(::RvWebItem),
                loadedWifi.map(::RvWifiItem),
                loadedKeys.map(::RvPasskeyItem),
            )
        }

        // One snapshot, so the tree recomposes once instead of four times.
        Snapshot.withMutableSnapshot {
            web = wrapped.first
            wifi = wrapped.second
            passkeys = wrapped.third
            sync = loadedSync
        }
    }

    suspend fun loadAccount() {
        profile = runCatching { Vault.profile() }.getOrDefault(Profile())
        settings = runCatching { Vault.settings() }.getOrDefault(Settings())
        choices = runCatching { Vault.autoLockChoices() }.getOrDefault(emptyList())
        photo = if (profile.hasPhoto) {
            val raw = runCatching { Vault.profilePhoto() }.getOrNull()
            raw?.let {
                withContext(Dispatchers.Default) {
                    runCatching { it.decodeToImageBitmap() }.getOrNull()
                }
            }
        } else {
            null
        }
    }

    LaunchedEffect(Unit) {
        runCatching { Vault.init() }
        anisette = runCatching { Vault.anisetteServer().url }.getOrDefault("")
        unlocked = runCatching { Vault.state().unlocked }.getOrDefault(false)
        ready = true
    }

    LaunchedEffect(unlocked, revision) {
        if (!unlocked) return@LaunchedEffect
        loadAccount()
        loadItems()
    }

    LaunchedEffect(Unit) {
        scope.launch { runCatching { Vault.listen() } }
    }

    LaunchedEffect(Unit) {
        Vault.events.collect { event ->
            when (event.kind) {
                EventKind.LOCKED -> unlocked = false
                EventKind.SYNC_CHANGED -> if (unlocked) loadItems()
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { wanted.toList() }.collect { pending ->
            pending.forEach { domain ->
                if (icons.containsKey(domain)) return@forEach
                val bytes = runCatching { Vault.icon(domain) }.getOrNull()
                icons[domain] = bytes?.let {
                    withContext(Dispatchers.Default) {
                        runCatching { RvFavicon(it.decodeToImageBitmap().fitted(tile)) }.getOrNull()
                    }
                }
            }
        }
    }

    val favicon: (String) -> Painter? = { domain ->
        if (domain.isNotEmpty() && !icons.containsKey(domain) && domain !in wanted) wanted += domain
        icons[domain]
    }

    val secrets = remember {
        object : RvSecretSource {
            override suspend fun password(id: String): String =
                runCatching { Vault.password(id) }.getOrDefault("")

            override suspend fun totp(id: String): String =
                runCatching { Vault.totp(id) }.getOrDefault("")
        }
    }

    val chooseAnisette: suspend (String) -> Boolean = { url ->
        runCatching {
            Vault.setAnisetteServer(url)
            anisette = Vault.anisetteServer().url
        }.isSuccess
    }

    val linked = remember(web, passkeys) { linkItems(web, passkeys) }
    val zones = homeZones(linked.web, wifi, linked.passkeys)

    RvVaultShell(
        authenticated = unlocked,
        modifier = modifier.fillMaxSize(),
        titleBar = titleBar,
        auth = {
            if (!ready) return@RvVaultShell
            RvAuthFlow(
                photo = photo,
                strings = strings,
                describe = { strings.failureText(it) },
                unlocked = { Vault.state().unlocked },
                configured = { Vault.state().configured },
                startLogin = { appleId, secret -> Vault.startLogin(appleId, secret) },
                requestCode = { Vault.requestCode() },
                submitCode = { code -> Vault.submitCode(code) },
                loadDevices = {
                    Vault.bottles().map { device ->
                        RvAuthDevice(name = device.name, model = device.shortModel)
                    }
                },
                loadProfile = {
                    val meta = runCatching { Vault.profile() }.getOrNull()
                    val raw = if (meta?.hasPhoto == true) {
                        runCatching { Vault.profilePhoto() }.getOrNull()
                    } else {
                        null
                    }

                    RvAuthProfile(
                        name = meta?.name.orEmpty(),
                        photo = raw?.let {
                            withContext(Dispatchers.Default) {
                                runCatching { it.decodeToImageBitmap() }.getOrNull()
                            }
                        },
                    )
                },
                recoverDevice = { index, passcode -> Vault.recoverBottle(index, passcode) },
                finishSetup = { secret -> Vault.finishSetup(secret) },
                unlock = { secret -> Vault.unlock(secret) },
                onPrivacy = { openUrl(PrivacyUrl) },
                anisette = anisette,
                onAnisette = chooseAnisette,
                onDone = { unlocked = true },
            )
        },
        home = {
            Box(modifier = Modifier.fillMaxSize()) {
                RvHomePage(
                    zones = zones,
                    secrets = secrets,
                    title = title,
                    photo = photo,
                    favicon = favicon,
                    settings = RvSettingsState(
                        profileName = profile.name,
                        appleId = profile.appleId,
                        version = version,
                        autoLockMinutes = settings.autoLockMinutes,
                        autoLockChoices = choices,
                        pending = sync.pending,
                        syncedAt = sync.syncedAt,
                        anisette = anisette,
                    ),
                    sortField = sortFieldOf(settings.sortField),
                    sortAscending = settings.sortAscending,
                    strings = strings,
                    onSort = { field, ascending ->
                        settings = settings.copy(
                            sortField = sortKeyOf(field),
                            sortAscending = ascending,
                        )
                        scope.launch {
                            runCatching { Vault.setSort(sortKeyOf(field), ascending) }
                        }
                    },
                    onCopy = copy,
                    onChangePassword = { domain ->
                        scope.launch {
                            val target = runCatching { Vault.changePasswordUrl(domain) }.getOrNull()
                            if (!target.isNullOrEmpty()) openUrl(target)
                        }
                    },
                    onLearnMore = { openUrl(PasskeyUrl) },
                    onCreate = { draft -> create(draft); revision++ },
                    onSave = { item, draft -> save(item, draft).also { revision++ } },
                    onDelete = { id -> Vault.deletePasswords(listOf(id)); revision++ },
                    onRestore = { id -> Vault.restorePassword(id); revision++ },
                    onPurge = { id -> Vault.purgePasswords(listOf(id)); revision++ },
                    onDeleteMany = { ids, purge ->
                        if (purge) Vault.purgePasswords(ids) else Vault.deletePasswords(ids)
                        revision++
                    },
                    onAutoLock = { minutes ->
                        settings = settings.copy(autoLockMinutes = minutes)
                        scope.launch { runCatching { Vault.setAutoLock(minutes) } }
                    },
                    onLockNow = {
                        scope.launch {
                            runCatching { Vault.lock() }
                            unlocked = false
                        }
                    },
                    onCheckPwned = { scope.launch { runCatching { Vault.checkPwned() }; revision++ } },
                    onAnisette = chooseAnisette,
                    onSignOut = {
                        scope.launch {
                            runCatching { Vault.signOut() }
                            unlocked = false
                        }
                    },
                )
            }
        },
    )
}

private val TileSize = 56.dp

private const val PrivacyUrl = "https://www.apple.com/legal/privacy/data/en/apple-id/"

private const val PasskeyUrl = "https://support.apple.com/en-us/102195"

private suspend fun create(draft: RvEditorDraft) {
    runCatching {
        Vault.addPassword(
            title = draft.title,
            website = draft.website,
            username = draft.username,
            password = draft.password,
            note = draft.notes,
        )
    }
}

private suspend fun save(item: RvWebItem, draft: RvDraft): String? = runCatching {
    Vault.editPassword(
        EditPassword(
            id = item.entry.id,
            title = draft.title,
            website = draft.websites.firstOrNull().orEmpty(),
            username = draft.username,
            password = draft.password,
            note = draft.note,
            totpUrl = draft.totpKey.orEmpty(),
            domains = draft.websites,
            dropTotp = draft.totpRemoved,
        ),
    )
}.getOrNull()

private fun sortFieldOf(key: String): RvSortField = when (key) {
    "website" -> RvSortField.Website
    "modified" -> RvSortField.Modified
    "created" -> RvSortField.Created
    else -> RvSortField.Title
}

private fun sortKeyOf(field: RvSortField): String = when (field) {
    RvSortField.Website -> "website"
    RvSortField.Modified -> "modified"
    RvSortField.Created -> "created"
    RvSortField.Title -> "title"
}
