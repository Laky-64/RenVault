package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.clip
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.app_logo
import io.lauren.renvault.resources.author
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.lauren.renvault.lib.RvBiometricCancelled
import io.lauren.renvault.lib.ClipboardClearChoices
import io.lauren.renvault.lib.RvScreenshots
import io.lauren.renvault.lib.screenshotBlockSupported
import io.lauren.renvault.lib.clipboardClearSeconds
import io.lauren.renvault.lib.autofillCopyTotp
import io.lauren.renvault.lib.autofillInline
import io.lauren.renvault.lib.rememberAutofillSetup
import io.lauren.renvault.lib.setAutofillCopyTotp
import io.lauren.renvault.lib.setAutofillInline
import io.lauren.renvault.lib.setClipboardClearSeconds
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
import kotlin.time.Duration.Companion.seconds

private val ClockTick = 30.seconds

private const val Author = "LaurenTheStoat"

private const val Handle = "@Laky-64"

private const val SourceLabel = "github.com/Laky-64/RenVault"

private const val SourceUrl = "https://github.com/Laky-64/RenVault"

private const val ProfileUrl = "https://github.com/Laky-64"

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
    val quickLabel: String = "",
    val quickOn: Boolean = false,
    val quickCredential: Boolean? = null,
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

private enum class SettingsPage { Root, Language, Names, Devices, About }

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
    onQuick: suspend (Boolean) -> Unit = {},
    onQuickCredential: suspend (Boolean) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    var page by remember { mutableStateOf(SettingsPage.Root) }
    val motion = MaterialTheme.motionScheme
    val rootList = rememberLazyListState()

    val navigation = rememberBackTransition(
        screen = page,
        previous = SettingsPage.Root.takeIf { page != SettingsPage.Root },
        label = "settingsPage",
    ) { page = SettingsPage.Root }

    navigation.transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = { slideBetween(targetState != SettingsPage.Root, motion, 3, navigation.peeking) },
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
            SettingsPage.About -> AboutPage(
                state = state,
                strings = strings,
                dialog = dialog,
                onBack = { page = SettingsPage.Root },
                onOpenUrl = onOpenUrl,
            )
            SettingsPage.Root -> RootPage(
                list = rootList,
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
                onQuick = onQuick,
                onQuickCredential = onQuickCredential,
                onAbout = { page = SettingsPage.About },
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
    list: LazyListState,
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
    onQuick: suspend (Boolean) -> Unit,
    onQuickCredential: suspend (Boolean) -> Unit,
    onAbout: () -> Unit,
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
    var quickBusy by remember { mutableStateOf(false) }
    var clipboardOpen by remember { mutableStateOf(false) }
    var autofillStyleOpen by remember { mutableStateOf(false) }
    var inlineFill by remember { mutableStateOf(autofillInline()) }
    var copyTotp by remember { mutableStateOf(autofillCopyTotp()) }
    var clipboardClear by remember { mutableIntStateOf(clipboardClearSeconds()) }
    val accents = RenVault.accents
    val scrolled by list.scrolledPast()
    val background = if (dialog) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface

    var lockOpen by remember { mutableStateOf(false) }
    var signOutOpen by remember { mutableStateOf(false) }
    var anisetteOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    val editable = state.local && devices != null
    val copy = rememberClipboardCopy()
    val autofill = rememberAutofillSetup()
    val backupFlow = backup?.let {
        rememberBackupFlow(it, strings, onMessage = { text ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        })
    }

    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ClockTick)
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
                    val active = autofill != null && autofill.enabled
                    val styled = active && autofill.inlineSupported
                    val fill = (if (autofill != null) 1 else 0) + (if (styled) 1 else 0) + (if (active) 1 else 0)
                    RvSegment(
                        index = 0,
                        count = 3 + fill,
                        onClick = onLanguage,
                        leading = { SettingBadge(RvLanguageIcon, accents.blue) },
                        supporting = { Text(locale.nameOf(locale.current)) },
                        trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                    ) {
                        Text(strings.settingsLanguage)
                    }
                    autofill?.let { setup ->
                        RvSegment(
                            index = 1,
                            count = 3 + fill,
                            onClick = { setup.set(!setup.enabled) },
                            leading = { SettingBadge(RvAutofillIcon, accents.green) },
                            supporting = { Text(strings.settingsAutofillDesc) },
                            trailing = { Switch(checked = setup.enabled, onCheckedChange = null) },
                        ) {
                            Text(strings.settingsAutofill)
                        }
                    }
                    if (styled) {
                        RvSegment(
                            index = 2,
                            count = 3 + fill,
                            onClick = { autofillStyleOpen = true },
                            leading = { SettingBadge(RvAutofillIcon, accents.teal) },
                            supporting = { Text(if (inlineFill) strings.settingsAutofillInline else strings.settingsAutofillPopup) },
                        ) {
                            Text(strings.settingsAutofillStyle)
                        }
                    }
                    if (active) {
                        RvSegment(
                            index = if (styled) 3 else 2,
                            count = 3 + fill,
                            onClick = {
                                copyTotp = !copyTotp
                                setAutofillCopyTotp(copyTotp)
                            },
                            leading = { SettingBadge(RvCodesIcon, accents.yellow) },
                            supporting = { Text(strings.settingsAutofillTotpDesc) },
                            trailing = { Switch(checked = copyTotp, onCheckedChange = null) },
                        ) {
                            Text(strings.settingsAutofillTotp)
                        }
                    }
                    RvSegment(
                        index = 1 + fill,
                        count = 3 + fill,
                        leading = { SettingBadge(RvPaletteIcon, accents.orange) },
                        supporting = { Text(LocalThemeController.current.mode.label(strings)) },
                    ) {
                        Text(strings.settingsTheme)
                    }
                    RvSegment(index = 2 + fill, count = 3 + fill) {
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
                    val credential = if (state.quickOn && state.quickCredential != null) 1 else 0
                    val quick = (if (state.quickLabel.isNotEmpty()) 1 else 0) + credential
                    if (state.quickLabel.isNotEmpty()) {
                        RvSegment(
                            index = 0,
                            count = 3 + quick,
                            onClick = {
                                if (!quickBusy) {
                                    scope.launch {
                                        quickBusy = true
                                        try {
                                            onQuick(!state.quickOn)
                                        } catch (_: RvBiometricCancelled) {
                                        } catch (_: Throwable) {
                                            snackbar.currentSnackbarData?.dismiss()
                                            snackbar.showSnackbar(strings.quickFailed)
                                        }
                                        quickBusy = false
                                    }
                                }
                            },
                            enabled = !quickBusy,
                            leading = { SettingBadge(RvFingerprintIcon, accents.blue) },
                            supporting = { Text(strings.quickNote) },
                            trailing = { Switch(checked = state.quickOn, onCheckedChange = null) },
                        ) {
                            Text(strings.quickUnlockWith(state.quickLabel))
                        }
                    }
                    if (credential == 1) {
                        RvSegment(
                            index = 1,
                            count = 3 + quick,
                            onClick = {
                                if (!quickBusy) {
                                    scope.launch {
                                        quickBusy = true
                                        try {
                                            onQuickCredential(state.quickCredential != true)
                                        } catch (_: RvBiometricCancelled) {
                                        } catch (_: Throwable) {
                                            snackbar.currentSnackbarData?.dismiss()
                                            snackbar.showSnackbar(strings.quickFailed)
                                        }
                                        quickBusy = false
                                    }
                                }
                            },
                            enabled = !quickBusy,
                            leading = { SettingBadge(RvPinIcon, accents.orange) },
                            supporting = { Text(strings.quickCredentialDesc) },
                            trailing = { Switch(checked = state.quickCredential == true, onCheckedChange = null) },
                        ) {
                            Text(strings.quickCredential)
                        }
                    }
                    RvSegment(
                        index = quick,
                        count = 3 + quick,
                        onClick = { lockOpen = true },
                        leading = { SettingBadge(RvTimerIcon, accents.teal) },
                        supporting = { Text(lockLabel(state.autoLockMinutes, strings)) },
                    ) {
                        Text(strings.settingsAutoLock)
                    }
                    RvSegment(
                        index = 1 + quick,
                        count = 3 + quick,
                        onClick = onLockNow,
                        leading = { SettingBadge(RvLockIcon, accents.yellow) },
                    ) {
                        Text(strings.settingsLockNow)
                    }
                    RvSegment(
                        index = 2 + quick,
                        count = 3 + quick,
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

            item {
                RvSection(title = strings.settingsPrivacyTitle) {
                    val shots = if (screenshotBlockSupported) 1 else 0
                    RvSegment(
                        index = 0,
                        count = 1 + shots,
                        onClick = { clipboardOpen = true },
                        leading = { SettingBadge(RvCopyIcon, accents.blue) },
                        supporting = {
                            Text(
                                if (clipboardClear == 0) {
                                    strings.settingsAutoLockNever
                                } else {
                                    strings.settingsClipboardAfter(durationLabel(clipboardClear, strings).lowercase())
                                },
                            )
                        },
                    ) {
                        Text(strings.settingsClipboard)
                    }
                    if (shots == 1) {
                        RvSegment(
                            index = 1,
                            count = 2,
                            onClick = { RvScreenshots.setBlocked(!RvScreenshots.blocked) },
                            leading = { SettingBadge(RvVisibilityOffIcon, accents.red) },
                            supporting = { Text(strings.settingsScreenshotsDesc) },
                            trailing = { Switch(checked = RvScreenshots.blocked, onCheckedChange = null) },
                        ) {
                            Text(strings.settingsScreenshots)
                        }
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
                        onClick = onAbout,
                        leading = { SettingBadge(RvInfoIcon, accents.blue) },
                        supporting = {
                            Column {
                                Text(
                                    listOfNotNull(
                                        listOf(state.commit, state.platform).filter { it.isNotEmpty() }.joinToString(" · "),
                                        state.core.takeIf { it.isNotEmpty() }?.let(strings::settingsBasedOn),
                                    ).filter { it.isNotEmpty() }.joinToString("\n"),
                                )
                            }
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChannelPill(state.channel, strings)
                                Icon(RvChevronRightIcon, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                            }
                        },
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

    RvChoiceDialog(
        open = autofillStyleOpen,
        onOpenChange = { autofillStyleOpen = it },
        title = strings.settingsAutofillStyle,
        choices = listOf(true, false),
        selected = inlineFill,
        label = { if (it) strings.settingsAutofillInline else strings.settingsAutofillPopup },
        cancel = strings.cancel,
        onSelect = {
            inlineFill = it
            setAutofillInline(it)
        },
    )

    RvChoiceDialog(
        open = clipboardOpen,
        onOpenChange = { clipboardOpen = it },
        title = strings.settingsClipboard,
        choices = ClipboardClearChoices,
        selected = clipboardClear,
        label = { durationLabel(it, strings) },
        cancel = strings.cancel,
        onSelect = {
            clipboardClear = it
            setClipboardClearSeconds(it)
        },
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

private fun channelLabel(channel: String, strings: RvStrings): String =
    if (channel == "stable") strings.settingsChannelStable else channel.replaceFirstChar { it.uppercase() }

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
            text = channelLabel(channel, strings),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AboutPage(
    state: RvSettingsState,
    strings: RvStrings,
    dialog: Boolean,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val accents = RenVault.accents
    val copy = rememberClipboardCopy()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()
    val scrolled by list.scrolledPast()
    val background = if (dialog) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    val copyBuild: () -> Unit = {
        copy(
            listOf(
                "${state.appName} v${state.version}",
                state.versionCode.takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty(),
                "#${state.channel}",
                state.commit.takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty(),
                state.platform,
            ).filter { it.isNotEmpty() }.joinToString(" "),
            false,
        )
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(strings.fieldCopied)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RvPageBar(
                title = strings.settingsAboutTitle,
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
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(MaterialShapes.Cookie12Sided.toShape())
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(78.dp),
                        )
                    }
                    Text(
                        text = state.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    Text(
                        text = strings.settingsAboutTagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                    )
                    Text(
                        text = listOf(
                            listOf("v${state.version}", state.versionCode.takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty()).filter { it.isNotEmpty() }.joinToString(" "),
                            channelLabel(state.channel, strings),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            item {
                RvSection {
                    RvSegment(
                        index = 0,
                        count = 4,
                        onClick = { onOpenUrl(ProfileUrl) },
                        leading = {
                            Image(
                                painter = painterResource(Res.drawable.author),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(MaterialShapes.Cookie9Sided.toShape()),
                            )
                        },
                        overline = { Text(strings.settingsMadeBy) },
                        supporting = { Text(Handle) },
                        trailing = { Icon(RvOpenInNewIcon, contentDescription = null) },
                    ) {
                        Text(Author)
                    }
                    RvSegment(
                        index = 1,
                        count = 4,
                        onClick = { onOpenUrl(SourceUrl) },
                        leading = { SettingBadge(RvGithubIcon, accents.teal) },
                        supporting = { Text(SourceLabel) },
                        trailing = { Icon(RvOpenInNewIcon, contentDescription = null) },
                    ) {
                        Text(strings.settingsSourceCode)
                    }
                    RvSegment(
                        index = 2,
                        count = 4,
                        onClick = { onOpenUrl("$SourceUrl/issues") },
                        leading = { SettingBadge(RvWarningIcon, accents.orange) },
                        trailing = { Icon(RvOpenInNewIcon, contentDescription = null) },
                    ) {
                        Text(strings.settingsReportIssue)
                    }
                    RvSegment(
                        index = 3,
                        count = 4,
                        onClick = copyBuild,
                        leading = { SettingBadge(RvInfoIcon, accents.blue) },
                        supporting = {
                            state.core.takeIf { it.isNotEmpty() }?.let { Text(strings.settingsBasedOn(it)) }
                        },
                        trailing = { Icon(RvCopyIcon, contentDescription = null) },
                    ) {
                        Text(listOf(state.commit, state.platform).filter { it.isNotEmpty() }.joinToString(" · "))
                    }
                }
            }
        }
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

private fun durationLabel(seconds: Int, strings: RvStrings): String = when {
    seconds <= 0 -> strings.settingsAutoLockNever
    seconds < 60 -> strings.settingsSeconds(seconds.toString())
    seconds == 60 -> strings.settingsAutoLockMinute
    else -> strings.settingsAutoLockMinutes(seconds / 60)
}

private fun lockLabel(value: Int, strings: RvStrings): String = when {
    value < 0 -> strings.settingsAutoLockImmediate
    value == 0 -> strings.settingsAutoLockNever
    value == 1 -> strings.settingsAutoLockMinute
    value == 60 -> strings.settingsAutoLockHour
    else -> strings.settingsAutoLockMinutes(value)
}
