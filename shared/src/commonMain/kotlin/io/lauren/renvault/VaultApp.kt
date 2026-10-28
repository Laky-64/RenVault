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
import io.lauren.renvault.bridge.ImportResult
import io.lauren.renvault.bridge.QuickState
import io.lauren.renvault.bridge.NetInfo
import io.lauren.renvault.lib.RvHideFromRecents
import io.lauren.renvault.lib.RvLan
import io.lauren.renvault.lib.RvLeaveSignal
import io.lauren.renvault.lib.RvLockGuard
import io.lauren.renvault.lib.quickCredential
import io.lauren.renvault.lib.setQuickCredential
import io.lauren.renvault.lib.leaveEvent
import io.lauren.renvault.lib.quickKey
import io.lauren.renvault.lib.rememberBiometrics
import io.lauren.renvault.lib.platformTag
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.app_name
import org.jetbrains.compose.resources.stringResource
import io.lauren.renvault.ui.components.RvDevice
import io.lauren.renvault.ui.components.LocalAnisetteServers
import io.lauren.renvault.ui.components.RvAnisetteChoice
import io.lauren.renvault.ui.components.RvBackupActions
import io.lauren.renvault.ui.components.RvDeviceActions
import io.lauren.renvault.ui.components.RvImported
import io.lauren.renvault.ui.components.RvPairCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import io.lauren.renvault.bridge.EditPassword
import io.lauren.renvault.bridge.Profile
import io.lauren.renvault.bridge.Settings
import io.lauren.renvault.bridge.SyncState
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.writePreference
import io.lauren.renvault.lib.readPreference
import io.lauren.renvault.ui.LocalNameNotice
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.lauren.renvault.ui.LocalNameMask
import io.lauren.renvault.lib.RvNameMask
import io.lauren.renvault.lib.RvNameSwap
import io.lauren.renvault.bridge.NameSwap
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun RvVaultApp(
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.app_name),
    version: String = RvAppVersion,
    strings: RvStrings = LocalStrings.current,
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
    var pwnedAt by remember { mutableStateOf(0L) }
    var names by remember { mutableStateOf(emptyList<RvNameSwap>()) }
    var nameNotice by remember { mutableStateOf(readPreference(NameNoticeKey) != "off") }
    var devices by remember { mutableStateOf(emptyList<RvDevice>()) }
    var lanOpen by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val biometrics = rememberBiometrics()
    var quick by remember { mutableStateOf(QuickState()) }
    val quickMethod = biometrics?.takeIf { it.available() }?.method ?: quick.methods.firstOrNull()
    val credentialSupported = when (quickMethod) {
        null -> false
        biometrics?.method -> biometrics.credentialSupported
        else -> quickMethod == TouchIdMethod
    }
    var credential by remember { mutableStateOf(quickCredential()) }

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

    suspend fun loadDevices() {
        devices = if (profile.local) {
            runCatching { Vault.devices() }.getOrDefault(emptyList())
                .map { RvDevice(it.id, it.name, it.self, it.syncedAt) }
        } else {
            emptyList()
        }
    }

    suspend fun loadQuick() {
        quick = runCatching { Vault.quickState() }.getOrDefault(QuickState())
        val local = biometrics
        if (local != null && quick.method == local.method && !local.hasKey()) {
            runCatching { Vault.disableQuick() }
            quick = runCatching { Vault.quickState() }.getOrDefault(QuickState())
        }
    }

    suspend fun loadAccount() {
        profile = runCatching { Vault.profile() }.getOrDefault(Profile())
        loadQuick()
        loadDevices()
        settings = runCatching { Vault.settings() }.getOrDefault(Settings())
        choices = runCatching { Vault.autoLockChoices() }.getOrDefault(emptyList())
        pwnedAt = runCatching { Vault.pwnedInfo().checkedAt }.getOrDefault(0L)
        names = runCatching { Vault.names() }.getOrDefault(emptyList()).map { RvNameSwap(it.from, it.to) }
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

    LaunchedEffect(unlocked, profile.local) {
        if (unlocked && profile.local) RvLan.schedule(true)
    }

    LaunchedEffect(unlocked, revision) {
        if (!unlocked) return@LaunchedEffect
        loadAccount()
        loadItems()
    }

    LaunchedEffect(Unit) {
        scope.launch { runCatching { Vault.listen() } }
    }

    val lockOnLeave = settings.autoLockMinutes < 0
    RvHideFromRecents(lockOnLeave)
    var away by remember { mutableStateOf(false) }
    var authKey by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Job?>(null) }
    val lockIfImmediate: suspend () -> Unit = {
        if (runCatching { Vault.settings().autoLockMinutes < 0 }.getOrDefault(false)) {
            unlocked = false
            lanOpen = false
            runCatching { Vault.lock() }
        }
    }
    val scheduleLock: () -> Unit = {
        pending?.cancel()
        pending = scope.launch(NonCancellable) {
            delay(LeaveDebounce)
            if (!away || RvLockGuard.expected()) return@launch
            lockIfImmediate()
        }
    }
    val hidden: () -> Unit = {
        away = true
        pending?.cancel()
        if (!RvLockGuard.expected()) {
            if (settings.autoLockMinutes < 0 && unlocked) {
                unlocked = false
                lanOpen = false
            }
            scope.launch(NonCancellable) { lockIfImmediate() }
        }
    }
    val leave: () -> Unit = {
        away = true
        scheduleLock()
    }
    val comeBack: () -> Unit = {
        away = false
        pending?.cancel()
        RvLockGuard.disarm()
    }
    LifecycleEventEffect(leaveEvent) { leave() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { hidden() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { comeBack() }
    LaunchedEffect(Unit) {
        RvLeaveSignal.events.collect { leave() }
    }
    LaunchedEffect(Unit) {
        RvLeaveSignal.comebacks.collect { comeBack() }
    }

    LaunchedEffect(Unit) {
        Vault.events.collect { event ->
            when (event.kind) {
                EventKind.LOCKED -> {
                    unlocked = false
                    lanOpen = false
                }
                EventKind.SYNC_CHANGED -> if (unlocked) loadItems()
                EventKind.PROFILE -> if (unlocked) loadAccount()
                EventKind.DEVICES -> if (unlocked) {
                    loadDevices()
                    sync = runCatching { Vault.syncState() }.getOrDefault(sync)
                }
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

    LaunchedEffect(ready, unlocked, lanOpen) {
        if (!ready) return@LaunchedEffect
        var announced = NetInfo()
        try {
            while (true) {
                withContext(Dispatchers.Default) {
                    val info = runCatching { Vault.netInfo() }.getOrDefault(NetInfo())
                    if (info.port > 0) runCatching { Vault.setAddresses(RvLan.addresses()) }
                    if (info.port == 0 && announced.port > 0) {
                        announced = info
                        RvLan.stop()
                    }
                    if (info.port > 0 && info != announced) {
                        announced = info
                        RvLan.start(info.node, info.port) { node, host, port ->
                            scope.launch { runCatching { Vault.peerSeen(node, host, port) } }
                        }
                    }
                }
                delay(LanRefresh)
            }
        } finally {
            RvLan.stop()
        }
    }

    val deviceActions = remember(strings) {
        object : RvDeviceActions {
            override suspend fun startPairing(): RvPairCode {
                runCatching { Vault.setAddresses(RvLan.addresses()) }
                val pairing = Vault.startPairing()
                return RvPairCode(pairing.code, pairing.size, pairing.modules)
            }

            override suspend fun waitPairing(): String = Vault.waitPairing().also { loadDevices() }

            override suspend fun join(text: String): String = Vault.joinPairing(text).also { revision++ }

            override suspend fun remove(id: String) {
                Vault.removeDevice(id)
                loadDevices()
            }

            override suspend fun syncNow() {
                Vault.sync()
                loadDevices()
                sync = runCatching { Vault.syncState() }.getOrDefault(sync)
            }

            override suspend fun setProfile(name: String, photo: ByteArray?, setPhoto: Boolean) {
                Vault.setProfile(name, photo, setPhoto)
                loadAccount()
            }

            override fun describe(failure: Throwable): String = strings.failureText(failure)
        }
    }

    val backupActions = remember(strings) {
        object : RvBackupActions {
            override suspend fun export(password: String): ByteArray = Vault.exportBackup(password)

            override suspend fun restore(data: ByteArray, password: String): RvImported =
                Vault.importBackup(data, password).imported().also { revision++ }

            override suspend fun importCsv(data: ByteArray): RvImported =
                Vault.importCsv(data).imported().also { revision++ }

            override fun describe(failure: Throwable): String = strings.failureText(failure)
        }
    }

    val quickUnlock = rememberQuickUnlock(strings, biometrics)

    val setQuick: suspend (Boolean) -> Unit = { on ->
        val local = biometrics
        when {
            !on -> {
                Vault.disableQuick()
                local?.forget()
            }
            local != null && quickMethod == local.method -> {
                val key = quickKey()
                try {
                    val sealed = local.seal(key, strings.quickEnablePrompt, strings.cancel)
                    Vault.enableQuick(local.method, key, sealed)
                } finally {
                    key.fill(0)
                }
            }
            quickMethod != null -> {
                RvLockGuard.expectLeave()
                nativeQuick { Vault.enableQuick(quickMethod, credential = credential && credentialSupported) }
            }
        }
        loadQuick()
    }

    val setCredential: suspend (Boolean) -> Unit = { on ->
        setQuickCredential(on)
        credential = on
        try {
            setQuick(true)
        } catch (failure: Throwable) {
            setQuickCredential(!on)
            credential = !on
            if (quickMethod == biometrics?.method) runCatching { setQuick(false) }
            throw failure
        }
    }

    val linked = remember(web, passkeys) { linkItems(web, passkeys) }
    val zones = homeZones(linked.web, wifi, linked.passkeys)

    val loadAnisette: suspend () -> List<RvAnisetteChoice> = {
        Vault.anisetteServers().map { RvAnisetteChoice(it.name, it.address, it.latencyMs, it.reachable) }
    }

    CompositionLocalProvider(LocalAnisetteServers provides loadAnisette) {
        RvVaultShell(
            authenticated = unlocked,
            modifier = modifier.fillMaxSize(),
            auth = {
                if (!ready) return@RvVaultShell
                key(authKey) {
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
                            coroutineScope {
                                Vault.bottles().map { device ->
                                    async {
                                        RvAuthDevice(
                                            name = device.name,
                                            model = device.shortModel,
                                            art = deviceArt(device.imageUrl),
                                        )
                                    }
                                }.awaitAll()
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
                                local = meta?.local == true,
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
                        finishLocal = { secret, name, picture ->
                            Vault.localSetup(secret, name, withContext(Dispatchers.Default) { RvLan.deviceName() }, picture)
                            lanOpen = true
                        },
                        joinPairing = { text -> Vault.joinPairing(text) },
                        unlock = { secret ->
                            Vault.unlock(secret)
                            if (away) scheduleLock()
                        },
                        quick = quickUnlock,
                        reset = {
                            Vault.signOut()
                            biometrics?.forget()
                            quick = QuickState()
                        },
                        onPrivacy = { openUrl(PrivacyUrl) },
                        anisette = anisette,
                        onAnisette = chooseAnisette,
                        backup = backupActions,
                        onDone = {
                            scope.launch {
                                val open = runCatching { Vault.state().unlocked }.getOrDefault(false)
                                unlocked = open
                                if (!open) authKey++
                            }
                        },
                    )
                }
            },
            home = {
                val mask = remember(names) { RvNameMask(names) }
                CompositionLocalProvider(LocalNameMask provides mask, LocalNameNotice provides nameNotice) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        RvHomePage(
                            zones = zones,
                            secrets = secrets,
                            title = title,
                            photo = photo,
                            favicon = favicon,
                            settings = RvSettingsState(
                                appName = title,
                                profileName = profile.name,
                                appleId = profile.appleId,
                                version = version,
                                versionCode = RvAppVersionCode,
                                channel = RvAppChannel,
                                commit = RvAppCommit,
                                platform = platformTag(),
                                core = RvAppleServicesVersion.takeIf { it.isNotEmpty() }?.let { "appleservices $it" }.orEmpty(),
                                autoLockMinutes = settings.autoLockMinutes,
                                quickLabel = quickMethod?.let { quickLabel(it, strings) }.orEmpty(),
                                quickOn = quick.method.isNotEmpty(),
                                quickCredential = credential.takeIf { credentialSupported },
                                autoLockChoices = choices,
                                pending = sync.pending,
                                syncedAt = sync.syncedAt,
                                anisette = anisette,
                                pwnedCheckedAt = pwnedAt,
                                names = names,
                                nameNotice = nameNotice,
                                local = profile.local,
                                devices = devices,
                            ),
                            sortField = sortFieldOf(settings.sortField),
                            sortAscending = settings.sortAscending,
                            strings = strings,
                            settingsBusy = leaving,
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
                            onCheckPwned = {
                                val report = Vault.checkPwned()
                                pwnedAt = report.checkedAt
                                revision++
                                report.pwned
                            },
                            onAnisette = chooseAnisette,
                            onNames = { next ->
                                Vault.setNames(next.map { NameSwap(it.from, it.to) })
                                names = Vault.names().map { RvNameSwap(it.from, it.to) }
                            },
                            onNameNotice = { shown ->
                            nameNotice = shown
                            writePreference(NameNoticeKey, if (shown) "on" else "off")
                        },
                        devices = if (profile.local) deviceActions else null,
                        backup = backupActions,
                        onQuick = setQuick,
                        onQuickCredential = setCredential,
                        onOpenUrl = openUrl,
                        onSignOut = {
                                scope.launch {
                                    leaving = true
                                    if (profile.local) RvLan.schedule(false)
                                    runCatching { Vault.signOut() }
                                    leaving = false
                                    unlocked = false
                                }
                            },
                        )
                    }
                }
            },
        )
    }
}

private val TileSize = 56.dp

private const val PrivacyUrl = "https://www.apple.com/legal/privacy/data/en/apple-id/"

private const val PasskeyUrl = "https://support.apple.com/en-us/102195"

private val DeviceArtTimeout = 4.seconds

private val LeaveDebounce = 300.milliseconds

private const val NameNoticeKey = "name_notice"

private val LanRefresh = 30.seconds

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

private suspend fun deviceArt(url: String): ImageBitmap? {
    if (url.isEmpty()) return null
    val bytes = withTimeoutOrNull(DeviceArtTimeout) {
        runCatching { Vault.image(url) }.getOrNull()
    } ?: return null
    return withContext(Dispatchers.Default) {
        runCatching { bytes.decodeToImageBitmap() }.getOrNull()
    }
}

private fun ImportResult.imported(): RvImported = RvImported(added, updated)
