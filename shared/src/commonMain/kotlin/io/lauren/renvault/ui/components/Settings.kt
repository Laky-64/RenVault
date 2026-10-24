package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.lauren.renvault.lib.RvNameSwap
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.relativeOf
import io.lauren.renvault.ui.LocalLocale
import io.lauren.renvault.ui.LocalNameMask
import io.lauren.renvault.ui.theme.LocalThemeController
import io.lauren.renvault.ui.theme.RenVault
import io.lauren.renvault.ui.theme.RvTone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay

private const val ClockTickMs = 30_000L

data class RvSettingsState(
    val profileName: String = "",
    val appleId: String = "",
    val appName: String,
    val version: String = "",
    val versionCode: String = "",
    val channel: String = "",
    val commit: String = "",
    val platform: String = "",
    val core: String = "",
    val autoLockMinutes: Int = 5,
    val autoLockChoices: List<Int> = emptyList(),
    val pending: Int = 0,
    val syncedAt: Long = 0L,
    val anisette: String = "",
    val pwnedCheckedAt: Long = 0L,
    val names: List<RvNameSwap> = emptyList(),
    val nameMatches: List<RvNameMatch> = emptyList(),
    val nameNotice: Boolean = true,
    val local: Boolean = false,
    val devices: List<RvDevice> = emptyList(),
)

private enum class SettingsPage { Root, Language, Names, Devices }

@Composable
fun RvSettingsScreen(
    state: RvSettingsState,
    photo: ImageBitmap?,
    busy: Boolean,
    strings: RvStrings,
    dialog: Boolean,
    onClose: () -> Unit,
    onAutoLock: (Int) -> Unit,
    onLockNow: () -> Unit,
    onCheckPwned: suspend () -> Int,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onAnisette: suspend (String) -> Boolean = { true },
    onShowPwned: () -> Unit = {},
    onNames: suspend (List<RvNameSwap>) -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    onNameNotice: (Boolean) -> Unit = {},
    devices: RvDeviceActions? = null,
    backup: RvBackupActions? = null,
) {
    var page by remember { mutableStateOf(SettingsPage.Root) }
    val motion = MaterialTheme.motionScheme

    RvBackHandler(enabled = page != SettingsPage.Root) { page = SettingsPage.Root }

    AnimatedContent(
        targetState = page,
        modifier = modifier,
        transitionSpec = {
            val spatial = motion.defaultSpatialSpec<IntOffset>()
            val forward = targetState != SettingsPage.Root
            (slideInHorizontally(spatial) { if (forward) it / 3 else -it / 3 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                (slideOutHorizontally(spatial) { if (forward) -it / 3 else it / 3 } + fadeOut(motion.fastEffectsSpec()))
        },
        label = "settingsPage",
    ) { shown ->
        when (shown) {
            SettingsPage.Language -> LanguagePage(strings = strings, dialog = dialog, onBack = { page = SettingsPage.Root })
            SettingsPage.Names -> NamesPage(
                names = state.names,
                matches = state.nameMatches,
                notice = state.nameNotice,
                onNotice = onNameNotice,
                strings = strings,
                dialog = dialog,
                onBack = { page = SettingsPage.Root },
                onNames = onNames,
                onOpenItem = onOpenItem,
            )
            SettingsPage.Devices -> devices?.let { actions ->
                DevicesPage(
                    devices = state.devices,
                    actions = actions,
                    strings = strings,
                    dialog = dialog,
                    onBack = { page = SettingsPage.Root },
                )
            }
            SettingsPage.Root -> RootPage(
                state = state,
                photo = photo,
                strings = strings,
                dialog = dialog,
                onClose = onClose,
                onLanguage = { page = SettingsPage.Language },
                onNames = { page = SettingsPage.Names },
                onDevices = { page = SettingsPage.Devices },
                devices = devices,
                backup = backup,
                onAutoLock = onAutoLock,
                onLockNow = onLockNow,
                onCheckPwned = onCheckPwned,
                onSignOut = onSignOut,
                onAnisette = onAnisette,
                onShowPwned = onShowPwned,
            )
        }
    }

    RvLoadingDialog(shown = busy)
}

@Composable
fun RvSettingsDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 760.dp)
                .fillMaxSize(0.88f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun RootPage(
    state: RvSettingsState,
    photo: ImageBitmap?,
    strings: RvStrings,
    dialog: Boolean,
    onClose: () -> Unit,
    onLanguage: () -> Unit,
    onNames: () -> Unit,
    onDevices: () -> Unit,
    devices: RvDeviceActions?,
    backup: RvBackupActions?,
    onAutoLock: (Int) -> Unit,
    onLockNow: () -> Unit,
    onCheckPwned: suspend () -> Int,
    onSignOut: () -> Unit,
    onAnisette: suspend (String) -> Boolean,
    onShowPwned: () -> Unit,
) {
    val locale = LocalLocale.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var checking by remember { mutableStateOf(false) }
    val accents = RenVault.accents
    val list = rememberLazyListState()
    val scrolled by list.scrolledPast()
    val background = if (dialog) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface

    var lockOpen by remember { mutableStateOf(false) }
    var signOutOpen by remember { mutableStateOf(false) }
    var anisetteOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    val editable = state.local && devices != null
    val copy = rememberClipboardCopy()
    val backupFlow = backup?.let {
        rememberBackupFlow(it, strings, onMessage = { text ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        })
    }
    val copyBuild: () -> Unit = {
        copy(
            listOf(
                "${state.appName} v${state.version}",
                state.versionCode.takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty(),
                "#${state.channel}",
                state.commit.takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty(),
                state.platform,
            ).filter { it.isNotEmpty() }.joinToString(" "),
        )
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(strings.fieldCopied)
        }
    }

    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ClockTickMs)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }

    val synced = relativeOf(state.syncedAt, now)?.let { strings.relativeTime(it) } ?: strings.settingsSyncedNever
    val pwnedChecked = relativeOf(state.pwnedCheckedAt, now)
        ?.let { strings.settingsPwnedLast(strings.relativeTime(it)) }
        ?: strings.settingsPwnedNever

    val checkPwned: () -> Unit = {
        if (!checking) {
            scope.launch {
                checking = true
                val found = runCatching { onCheckPwned() }
                checking = false
                snackbar.currentSnackbarData?.dismiss()
                found.fold(
                    onSuccess = { count ->
                        if (count == 0) {
                            snackbar.showSnackbar(strings.pwnedNone)
                        } else {
                            val result = snackbar.showSnackbar(
                                message = strings.pwnedFound(count),
                                actionLabel = strings.pwnedShow,
                            )
                            if (result == SnackbarResult.ActionPerformed) onShowPwned()
                        }
                    },
                    onFailure = { snackbar.showSnackbar(strings.pwnedFailed) },
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RvPageBar(
                title = strings.settingsTitle,
                scrolled = scrolled,
                background = background,
                navigationIcon = {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(if (dialog) RvCloseIcon else RvBackIcon, contentDescription = strings.settingsClose)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = RvLayout.gutter,
                end = RvLayout.gutter,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { RvPageTitle(strings.settingsTitle) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RvProfileAvatar(size = 96.dp, photo = photo, shape = MaterialShapes.Cookie12Sided.toShape())
                    Text(
                        text = LocalNameMask.current.mask(state.profileName).ifEmpty { state.appName },
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    val account = if (state.local) strings.settingsLocalVault else LocalNameMask.current.mask(state.appleId)
                    if (account.isNotEmpty()) {
                        Text(
                            text = account,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (editable) {
                        OutlinedButton(
                            onClick = { profileOpen = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Icon(RvEditIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text(strings.profileEditTitle, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                    }
                }
            }

            if (editable) {
                item {
                    val lastPeer = relativeOf(state.syncedAt, now)?.let { strings.devicesSynced(strings.relativeTime(it)) }
                    RvSection(title = strings.settingsSyncTitle) {
                        RvSegment(
                            index = 0,
                            count = 1,
                            onClick = onDevices,
                            leading = { SettingBadge(RvDevicesIcon, accents.blue) },
                            supporting = {
                                Text(strings.devicesCount(state.devices.size) + " · " + (lastPeer ?: strings.devicesNever))
                            },
                            trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                        ) {
                            Text(strings.devicesTitle)
                        }
                    }
                }
            }

            item {
                RvSection(title = strings.settingsGeneralTitle) {
                    RvSegment(
                        index = 0,
                        count = 3,
                        onClick = onLanguage,
                        leading = { SettingBadge(RvLanguageIcon, accents.blue) },
                        supporting = { Text(locale.nameOf(locale.current)) },
                        trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                    ) {
                        Text(strings.settingsLanguage)
                    }
                    RvSegment(
                        index = 1,
                        count = 3,
                        leading = { SettingBadge(RvPaletteIcon, accents.orange) },
                        supporting = { Text(LocalThemeController.current.mode.label(strings)) },
                    ) {
                        Text(strings.settingsTheme)
                    }
                    RvSegment(index = 2, count = 3) {
                        RvThemeButtons()
                    }
                }
            }

            item {
                RvSection(title = strings.settingsIdentityTitle) {
                    RvSegment(
                        index = 0,
                        count = 1,
                        onClick = onNames,
                        leading = { SettingBadge(RvBadgeIcon, accents.teal) },
                        supporting = {
                            Text(
                                when {
                                    state.names.isEmpty() -> strings.namesOff
                                    state.nameMatches.isEmpty() -> strings.namesOn
                                    else -> strings.namesFound(state.nameMatches.size)
                                },
                            )
                        },
                        trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                    ) {
                        Text(strings.namesTitle)
                    }
                }
            }

            item {
                RvSection(title = strings.settingsSecurityTitle, footer = strings.settingsAutoLockNote) {
                    RvSegment(
                        index = 0,
                        count = 3,
                        onClick = { lockOpen = true },
                        leading = { SettingBadge(RvTimerIcon, accents.teal) },
                        supporting = { Text(lockLabel(state.autoLockMinutes, strings)) },
                    ) {
                        Text(strings.settingsAutoLock)
                    }
                    RvSegment(
                        index = 1,
                        count = 3,
                        onClick = onLockNow,
                        leading = { SettingBadge(RvLockIcon, accents.yellow) },
                    ) {
                        Text(strings.settingsLockNow)
                    }
                    RvSegment(
                        index = 2,
                        count = 3,
                        onClick = checkPwned,
                        leading = {
                            if (checking) {
                                LoadingIndicator(modifier = Modifier.size(40.dp))
                            } else {
                                SettingBadge(RvVerifiedIcon, accents.green)
                            }
                        },
                        supporting = { Text(if (checking) strings.pwnedChecking else pwnedChecked) },
                    ) {
                        Text(strings.settingsCheckPwned)
                    }
                }
            }

            if (backupFlow != null) item {
                RvSection(title = strings.settingsBackupTitle, footer = strings.settingsBackupNote) {
                    val rows = listOfNotNull(
                        backupFlow.export?.let {
                            BackupRow(it, RvDownloadIcon, accents.blue, strings.backupExport, strings.backupExportDesc)
                        },
                        backupFlow.restore?.let {
                            BackupRow(it, RvRestoreIcon, accents.green, strings.backupRestore, strings.backupRestoreDesc)
                        },
                        backupFlow.importCsv?.let {
                            BackupRow(it, RvUploadFileIcon, accents.orange, strings.backupImportCsv, strings.backupImportCsvDesc)
                        },
                    )
                    rows.forEachIndexed { index, row ->
                        RvSegment(
                            index = index,
                            count = rows.size,
                            onClick = row.onClick,
                            enabled = !backupFlow.busy,
                            leading = { SettingBadge(row.icon, row.tone) },
                            supporting = { Text(row.detail) },
                        ) {
                            Text(row.title)
                        }
                    }
                }
            }

            if (!state.local) item {
                RvSection(title = strings.settingsAdvancedTitle) {
                    RvSegment(
                        index = 0,
                        count = 1,
                        onClick = { anisetteOpen = true },
                        leading = { SettingBadge(RvServerIcon, accents.red) },
                        supporting = {
                            Text(state.anisette.ifEmpty { strings.anisetteDefault }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                    ) {
                        Text(strings.anisetteTitle)
                    }
                }
            }

            item {
                RvSection(
                    title = strings.settingsAboutTitle,
                    footer = if (state.pending > 0) strings.settingsPending(state.pending) else null,
                ) {
                    RvSegment(
                        index = 0,
                        count = 2,
                        leading = { SettingBadge(RvSyncIcon, accents.orange) },
                        supporting = { Text(synced) },
                    ) {
                        Text(strings.settingsLastSync)
                    }
                    RvSegment(
                        index = 1,
                        count = 2,
                        onClick = copyBuild,
                        leading = { SettingBadge(RvInfoIcon, accents.blue) },
                        supporting = {
                            Text(
                                listOfNotNull(
                                    listOf(state.commit, state.platform).filter { it.isNotEmpty() }.joinToString(" · "),
                                    state.core.takeIf { it.isNotEmpty() }?.let(strings::settingsBasedOn),
                                ).filter { it.isNotEmpty() }.joinToString("\n"),
                            )
                        },
                        trailing = { ChannelPill(state.channel, strings) },
                    ) {
                        Text(listOf("${state.appName} ${state.version}", state.versionCode.takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty()).filter { it.isNotEmpty() }.joinToString(" "))
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RvButton(
                        text = if (state.local) strings.settingsDeleteVault else strings.settingsSignOut,
                        onClick = { signOutOpen = true },
                        kind = RvButtonKind.DangerTonal,
                        icon = if (state.local) RvDeleteIcon else RvLogoutIcon,
                    )
                    Text(
                        text = if (state.local) strings.settingsDeleteVaultNote else strings.settingsSignOutNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    RvChoiceDialog(
        open = lockOpen,
        onOpenChange = { lockOpen = it },
        title = strings.settingsAutoLock,
        choices = state.autoLockChoices,
        selected = state.autoLockMinutes,
        label = { lockLabel(it, strings) },
        cancel = strings.cancel,
        onSelect = onAutoLock,
    )

    RvAnisetteDialog(
        open = anisetteOpen,
        onOpenChange = { anisetteOpen = it },
        current = state.anisette,
        strings = strings,
        onSave = onAnisette,
    )

    RvConfirmDialog(
        open = signOutOpen,
        onOpenChange = { signOutOpen = it },
        title = if (state.local) strings.settingsDeleteVaultTitle else strings.settingsSignOutTitle,
        description = if (state.local) strings.settingsDeleteVaultDesc else strings.settingsSignOutDesc,
        confirm = if (state.local) strings.settingsDeleteVault else strings.settingsSignOut,
        cancel = strings.cancel,
        danger = true,
        icon = if (state.local) RvDeleteIcon else RvLogoutIcon,
        onConfirm = onSignOut,
    )

    if (profileOpen && devices != null) {
        ProfileDialog(
            name = state.profileName,
            photo = photo,
            actions = devices,
            strings = strings,
            onDismiss = { profileOpen = false },
        )
    }
}

private class BackupRow(
    val onClick: () -> Unit,
    val icon: ImageVector,
    val tone: RvTone,
    val title: String,
    val detail: String,
)

@Composable
private fun ChannelPill(channel: String, strings: RvStrings) {
    val accents = RenVault.accents
    val tone = when (channel) {
        "stable" -> accents.green
        "beta" -> accents.yellow
        else -> accents.red
    }
    Surface(shape = CircleShape, color = tone.container, contentColor = tone.content) {
        Text(
            text = if (channel == "stable") strings.settingsChannelStable else channel.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun SettingBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: RvTone) {
    RvShapeBadge(icon = icon, tone = tone, polygon = MaterialShapes.Circle, size = 40.dp, iconSize = 22.dp)
}

@Composable
private fun LanguagePage(strings: RvStrings, dialog: Boolean, onBack: () -> Unit) {
    val locale = LocalLocale.current
    var query by remember { mutableStateOf("") }
    val choices = locale.search(query)
    val list = rememberLazyListState()
    val scrolled by list.scrolledPast()
    val background = if (dialog) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            RvPageBar(
                title = strings.settingsLanguage,
                scrolled = scrolled,
                background = background,
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(RvBackIcon, contentDescription = strings.navBack)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = RvLayout.gutter,
                end = RvLayout.gutter,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { RvPageTitle(strings.settingsLanguage) }

            item {
                RvSearchInput(
                    query = query,
                    onQuery = { query = it },
                    placeholder = strings.searchPlaceholder,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }

            item {
                if (choices.isEmpty()) {
                    Text(
                        text = strings.searchEmpty(query.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                } else {
                    RvSection {
                        choices.forEachIndexed { index, tag ->
                            val picked = tag == locale.current
                            RvSegment(
                                index = index,
                                count = choices.size,
                                selected = picked,
                                onClick = { locale.choose(tag) },
                                leading = { RadioButton(selected = picked, onClick = null) },
                            ) {
                                Text(locale.nameOf(tag))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun lockLabel(value: Int, strings: RvStrings): String = when {
    value <= 0 -> strings.settingsAutoLockNever
    value == 1 -> strings.settingsAutoLockMinute
    value == 60 -> strings.settingsAutoLockHour
    else -> strings.settingsAutoLockMinutes(value)
}
