package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.RvDraft
import io.lauren.renvault.lib.RvItem
import io.lauren.renvault.lib.RvPasskeyItem
import io.lauren.renvault.lib.RvSecretSource
import io.lauren.renvault.lib.RvSortField
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.RvWebItem
import io.lauren.renvault.lib.RvWifiItem
import io.lauren.renvault.lib.RvZoneKind
import io.lauren.renvault.lib.canSave
import io.lauren.renvault.lib.draftOf
import io.lauren.renvault.lib.editableOf
import io.lauren.renvault.lib.matchesQuery
import io.lauren.renvault.lib.sortItems
import io.lauren.renvault.ui.LocalStrings
import io.lauren.renvault.ui.theme.RenVault
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.app_name
import org.jetbrains.compose.resources.stringResource
import io.lauren.renvault.ui.theme.RvTone
import kotlinx.coroutines.launch
import io.lauren.renvault.ui.LocalNameMask
import io.lauren.renvault.lib.viewOf
import io.lauren.renvault.lib.masked
import io.lauren.renvault.lib.hidesName
import io.lauren.renvault.lib.RvNameSwap
import io.lauren.renvault.ui.LocalNameNotice

data class RvHomeZone(
    val kind: RvZoneKind,
    val icon: ZoneGlyph,
    val tone: RvTone,
    val items: List<RvItem>,
)

data class RvEditorDraft(
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
)

@Composable
fun homeZones(
    web: List<RvWebItem>,
    wifi: List<RvWifiItem>,
    passkeys: List<RvPasskeyItem>,
): List<RvHomeZone> {
    val accents = RenVault.accents

    return remember(web, wifi, passkeys, accents) {
        val live = web.filter { !it.entry.isDeleted && !it.entry.shared }

        listOf(
            RvHomeZone(RvZoneKind.All, ZoneGlyph.All, accents.blue, live),
            RvHomeZone(RvZoneKind.Passkeys, ZoneGlyph.Passkey, accents.green, passkeys.filter { !it.entry.isDeleted }),
            RvHomeZone(RvZoneKind.Codes, ZoneGlyph.Codes, accents.yellow, live.filter { it.entry.hasTotp }),
            RvHomeZone(RvZoneKind.Networks, ZoneGlyph.Wifi, accents.teal, wifi),
            RvHomeZone(RvZoneKind.Security, ZoneGlyph.Security, accents.red, live.filter { it.entry.pwned }),
            RvHomeZone(
                kind = RvZoneKind.Deleted,
                icon = ZoneGlyph.Deleted,
                tone = accents.orange,
                items = web.filter { it.entry.isDeleted && !it.entry.shared },
            ),
        )
    }
}

private enum class Screen { Zones, List, Item, Settings }

@Composable
fun RvHomePage(
    zones: List<RvHomeZone>,
    secrets: RvSecretSource,
    modifier: Modifier = Modifier,
    title: String = "",
    photo: ImageBitmap? = null,
    favicon: (domain: String) -> Painter? = { null },
    settings: RvSettingsState = RvSettingsState(appName = stringResource(Res.string.app_name)),
    settingsBusy: Boolean = false,
    sortField: RvSortField = RvSortField.Title,
    sortAscending: Boolean = true,
    strings: RvStrings = LocalStrings.current,
    onSort: (RvSortField, Boolean) -> Unit = { _, _ -> },
    onCopy: (String) -> Unit = {},
    onChangePassword: (String) -> Unit = {},
    onLearnMore: () -> Unit = {},
    onCreate: suspend (RvEditorDraft) -> Unit = {},
    onSave: suspend (RvWebItem, RvDraft) -> String? = { _, _ -> null },
    onDelete: suspend (String) -> Unit = {},
    onRestore: suspend (String) -> Unit = {},
    onPurge: suspend (String) -> Unit = {},
    onDeleteMany: suspend (List<String>, Boolean) -> Unit = { _, _ -> },
    onAutoLock: (Int) -> Unit = {},
    onLockNow: () -> Unit = {},
    onCheckPwned: suspend () -> Int = { 0 },
    onSignOut: () -> Unit = {},
    onAnisette: suspend (String) -> Boolean = { true },
    onNames: suspend (List<RvNameSwap>) -> Unit = {},
    onNameNotice: (Boolean) -> Unit = {},
    devices: RvDeviceActions? = null,
) {
    if (zones.isEmpty()) return

    val compact = isCompact()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var zoneKind by remember { mutableStateOf<RvZoneKind?>(null) }
    var itemId by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(RvDraft()) }

    val lastZone = remember { arrayOf(RvZoneKind.All) }
    zoneKind?.let { lastZone[0] = it }
    val shownKind = zoneKind ?: if (compact) lastZone[0] else RvZoneKind.All
    val active = zones.firstOrNull { it.kind == shownKind } ?: zones.first()

    val listStates = remember { HashMap<RvZoneKind, LazyListState>() }
    val listState = listStates.getOrPut(active.kind) { LazyListState() }

    val names = LocalNameMask.current
    val notice = LocalNameNotice.current
    val found = remember(active, query, names) {
        if (query.isBlank()) active.items else active.items.filter { matchesQuery(it, query, names) }
    }
    val items = remember(found, sortField, sortAscending, strings) {
        sortItems(found, sortField, sortAscending, strings)
    }

    val everything = remember(zones) { zones.flatMap { it.items }.distinctBy { it.id } }
    val current = remember(everything, itemId) { everything.firstOrNull { it.id == itemId } }
    var lastOpen by remember { mutableStateOf<RvItem?>(null) }

    val target = editableOf(current)
    val purging = active.kind == RvZoneKind.Deleted
    val pickedWeb = items.filter { it is RvWebItem && it.id in picked }
    val draftOk = canSave(draft)

    LaunchedEffect(active.kind, zoneKind == null) {
        if (zoneKind == null && compact) return@LaunchedEffect
        query = ""
        searching = false
    }
    LaunchedEffect(selecting, active.kind, zoneKind == null) { picked = emptySet() }
    LaunchedEffect(itemId) { editing = false }
    LaunchedEffect(current) { if (current != null) lastOpen = current }

    LaunchedEffect(everything, itemId, editing) {
        if (itemId == null || editing || current != null) return@LaunchedEffect
        val gone = lastOpen as? RvWebItem
        val moved = gone?.let { previous ->
            everything.filterIsInstance<RvWebItem>().firstOrNull {
                it.entry.domain == previous.entry.domain && it.entry.username == previous.entry.username
            }
        }
        itemId = moved?.id
    }

    LaunchedEffect(editing, target?.id) {
        if (!editing) return@LaunchedEffect
        val item = target ?: return@LaunchedEffect
        draft = draftOf(item)
        if (!item.entry.hasPassword) return@LaunchedEffect
        val secret = runCatching { secrets.password(item.entry.id) }.getOrNull() ?: return@LaunchedEffect
        draft = draft.copy(password = secret)
    }

    val copy: (String) -> Unit = { text ->
        onCopy(text)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(if (notice && names.hides(text)) strings.fieldCopiedOriginal else strings.fieldCopied)
        }
    }

    val write: (String, suspend (String) -> Unit) -> Unit = { id, run ->
        scope.launch { runCatching { run(id) }.onSuccess { itemId = null } }
    }

    val saveEdit: () -> Unit = save@{
        if (!draftOk || saving) return@save
        val item = target
        if (item == null) {
            editing = false
            return@save
        }
        scope.launch {
            saving = true
            try {
                val next = runCatching { onSave(item, draft) }.getOrNull()
                itemId = next
                editing = false
            } finally {
                saving = false
            }
        }
    }

    val back: () -> Unit = {
        when {
            editing -> editing = false
            settingsOpen && compact -> settingsOpen = false
            selecting -> selecting = false
            itemId != null -> itemId = null
            searching -> {
                searching = false
                query = ""
            }
            zoneKind != null -> zoneKind = null
        }
    }

    val canGoBack = editing || (compact && (settingsOpen || itemId != null || zoneKind != null)) ||
        selecting || searching
    RvBackHandler(enabled = canGoBack, onBack = back)

    val zonesPane: @Composable (Modifier) -> Unit = { paneModifier ->
        RvZonesPane(
            zones = zones,
            title = title,
            photo = photo,
            strings = strings,
            selected = if (compact) null else active.kind,
            modifier = paneModifier,
            onZone = { kind ->
                zoneKind = kind
                itemId = null
                selecting = false
            },
            onSearch = {
                zoneKind = RvZoneKind.All
                searching = true
            },
            onSettings = { settingsOpen = true },
            onAdd = { creating = true },
        )
    }

    val listPane: @Composable (Modifier) -> Unit = { paneModifier ->
        RvListPane(
            zone = active,
            items = items,
            strings = strings,
            secrets = secrets,
            favicon = favicon,
            listState = listState,
            modifier = paneModifier,
            compact = compact,
            query = query,
            onQuery = { query = it },
            searching = searching,
            onSearching = {
                searching = it
                if (!it) query = ""
            },
            selecting = selecting,
            onSelecting = { selecting = it },
            picked = picked,
            onPicked = { picked = it },
            openId = if (compact) null else itemId,
            sortField = sortField,
            sortAscending = sortAscending,
            onSort = onSort,
            onBack = { zoneKind = null },
            onOpen = { itemId = it },
            onAdd = { creating = true },
            onDeleteSelected = { confirmDelete = true },
            canDeleteSelected = pickedWeb.isNotEmpty(),
        )
    }

    val detailPane: @Composable (Modifier) -> Unit = { paneModifier ->
        RvDetailPane(
            zone = active,
            item = if (compact) current ?: lastOpen else current,
            secrets = secrets,
            strings = strings,
            favicon = favicon,
            modifier = paneModifier,
            compact = compact,
            editing = editing,
            onEditing = { editing = it },
            canEdit = active.kind != RvZoneKind.Deleted && target != null,
            draft = draft,
            onDraft = { draft = it },
            canSave = draftOk && !saving,
            saving = saving,
            onSave = saveEdit,
            onBack = { itemId = null },
            onCopy = copy,
            onChangePassword = onChangePassword,
            onLearnMore = onLearnMore,
            onDelete = {
                editing = false
                target?.let { write(it.entry.id, onDelete) }
            },
            onRecover = { current?.let { write(it.id, onRestore) } },
            onPurge = { current?.let { write(it.id, onPurge) } },
        )
    }

    val nameMatches = remember(everything, names, strings) {
        everything.filter { hidesName(it, names) }
            .map { RvNameMatch(it.id, viewOf(it, strings).masked(names).title) }
            .sortedBy { it.title.lowercase() }
    }

    val settingsPane: @Composable (Modifier, Boolean) -> Unit = { paneModifier, dialog ->
        RvSettingsScreen(
            state = settings.copy(nameMatches = nameMatches),
            photo = photo,
            busy = settingsBusy,
            strings = strings,
            dialog = dialog,
            modifier = paneModifier,
            onClose = { settingsOpen = false },
            onAutoLock = onAutoLock,
            onLockNow = onLockNow,
            onCheckPwned = onCheckPwned,
            onSignOut = onSignOut,
            onAnisette = onAnisette,
            onShowPwned = {
                settingsOpen = false
                itemId = null
                zoneKind = RvZoneKind.Security
            },
            onNames = onNames,
            onNameNotice = onNameNotice,
            devices = devices,
            onOpenItem = { id ->
                settingsOpen = false
                zoneKind = RvZoneKind.All
                itemId = id
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (compact) {
            val screen = when {
                settingsOpen -> Screen.Settings
                itemId != null && current != null -> Screen.Item
                zoneKind != null -> Screen.List
                else -> Screen.Zones
            }
            val motion = MaterialTheme.motionScheme

            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val spatial = motion.defaultSpatialSpec<IntOffset>()
                    (slideInHorizontally(spatial) { if (forward) it / 3 else -it / 3 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutHorizontally(spatial) { if (forward) -it / 3 else it / 3 } + fadeOut(motion.fastEffectsSpec()))
                },
                label = "homeScreen",
            ) { shown ->
                val fill = Modifier.fillMaxSize()
                when (shown) {
                    Screen.Zones -> zonesPane(fill)
                    Screen.List -> listPane(fill)
                    Screen.Item -> detailPane(fill)
                    Screen.Settings -> settingsPane(fill, false)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                zonesPane(Modifier.width(RvLayout.zonesPane).fillMaxHeight())

                Surface(
                    modifier = Modifier.width(RvLayout.listPane).fillMaxHeight().padding(top = 16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    listPane(Modifier.fillMaxSize())
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    detailPane(Modifier.fillMaxSize())
                }
            }

            if (settingsOpen) {
                RvSettingsDialog(onDismiss = { settingsOpen = false }) { settingsPane(Modifier.fillMaxSize(), true) }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
    }

    RvNewPasswordSheet(
        open = creating,
        onOpenChange = { creating = it },
        strings = strings,
        favicon = favicon,
        onSave = { entry -> scope.launch { onCreate(entry) } },
    )

    RvConfirmDialog(
        open = confirmDelete,
        onOpenChange = { confirmDelete = it },
        title = strings.deleteTitle(pickedWeb.size),
        description = if (purging) strings.deleteHardDesc(pickedWeb.size) else strings.deleteSoftDesc(pickedWeb.size),
        confirm = strings.deleteConfirm(pickedWeb.size),
        cancel = strings.cancel,
        danger = true,
        icon = RvDeleteIcon,
        onConfirm = {
            val ids = pickedWeb.map { it.id }
            if (ids.isNotEmpty()) {
                scope.launch {
                    runCatching { onDeleteMany(ids, purging) }.onSuccess { selecting = false }
                }
            }
        },
    )
}
