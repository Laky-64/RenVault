package io.lauren.renvault.ui.components

import io.lauren.renvault.RvAppName
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lauren.renvault.bridge.FileFilter
import io.lauren.renvault.lib.RvPasswordStrength
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.strengthOf
import io.lauren.renvault.ui.theme.RenVault
import io.lauren.renvault.ui.theme.RvTone
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal enum class TransferMode { Export, Import }

private enum class TransferFormat { Backup, Csv, Zip }

private const val TransferSteps = 3

private val SourceNames = mapOf(
    "renvault" to RvAppName,
    "chrome" to "Chrome",
    "bitwarden" to "Bitwarden",
    "protonpass" to "Proton Pass",
    "1password" to "1Password",
    "firefox" to "Firefox",
    "lastpass" to "LastPass",
    "dashlane" to "Dashlane",
    "keepassxc" to "KeePassXC",
    "apple" to "Apple Passwords",
    "keyguard" to "Keyguard",
    "nordpass" to "NordPass",
)

private class FormatOption(
    val format: TransferFormat,
    val icon: ImageVector,
    val tone: RvTone,
    val title: String,
    val detail: String,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TransferPage(
    mode: TransferMode,
    actions: RvBackupActions,
    strings: RvStrings,
    dialog: Boolean,
    onBack: () -> Unit,
    onFinished: (() -> Unit)? = null,
) {
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val accents = RenVault.accents
    val background = if (dialog) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    val title = if (mode == TransferMode.Export) strings.transferExportTitle else strings.transferImportTitle

    var step by remember { mutableStateOf(1) }
    var format by remember { mutableStateOf(TransferFormat.Backup) }
    var data by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<RvImportPreview?>(null) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<RvImported?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val options = if (mode == TransferMode.Export) {
        listOf(
            FormatOption(TransferFormat.Backup, RvShieldIcon, accents.blue, strings.backupFileType, strings.transferBackupExportDesc),
            FormatOption(TransferFormat.Zip, RvFolderZipIcon, accents.teal, strings.transferZip, strings.transferZipExportDesc),
            FormatOption(TransferFormat.Csv, RvUploadFileIcon, accents.orange, "CSV", strings.transferCsvExportDesc),
        )
    } else {
        listOf(
            FormatOption(TransferFormat.Backup, RvShieldIcon, accents.green, strings.backupFileType, strings.transferBackupImportDesc),
            FormatOption(TransferFormat.Csv, RvUploadFileIcon, accents.orange, "CSV", strings.backupImportCsvDesc),
            FormatOption(TransferFormat.Zip, RvFolderZipIcon, accents.teal, strings.transferZip, strings.transferZipDesc),
        )
    }
    val chosen = options.first { it.format == format }

    val backupFilter = remember(strings) {
        FileFilter(strings.backupFileType, listOf("renvault"), listOf("application/octet-stream"))
    }
    val csvFilter = remember {
        FileFilter("CSV", listOf("csv"), listOf("text/csv", "text/comma-separated-values", "text/plain"))
    }
    val zipFilter = remember(strings) {
        FileFilter(
            strings.transferZip,
            listOf("zip", "pgp"),
            listOf("application/zip", "application/x-zip-compressed", "application/pgp-encrypted", "application/octet-stream"),
        )
    }

    val run: (suspend () -> Unit) -> Unit = { block ->
        if (!busy) {
            scope.launch {
                busy = true
                failure = null
                runCatching { block() }.onFailure { failure = actions.describe(it) }
                busy = false
            }
        }
    }

    val onSaved: (Boolean) -> Unit = { saved -> if (saved) step = TransferSteps }
    val saveBackup = rememberFileSaver(strings.backupExport, backupFilter, onSaved)
    val saveCsv = rememberFileSaver(strings.transferExportTitle, csvFilter, onSaved)
    val zipSaveFilter = remember(strings) { FileFilter(strings.transferZip, listOf("zip"), listOf("application/zip")) }
    val saveZip = rememberFileSaver(strings.transferExportTitle, zipSaveFilter, onSaved)

    val opened: (ByteArray) -> Unit = { bytes ->
        data = bytes
        preview = null
        password = ""
        run {
            preview = when (format) {
                TransferFormat.Backup -> actions.inspectBackup(bytes, "")
                TransferFormat.Csv -> actions.inspectCsv(bytes)
                TransferFormat.Zip -> actions.inspectArchive(bytes, "")
            }
            step = 2
        }
    }
    val openBackup = rememberFileOpener(strings.backupRestore, backupFilter, opened)
    val openCsv = rememberFileOpener(strings.backupImportCsv, csvFilter, opened)
    val openZip = rememberFileOpener(strings.transferZip, zipFilter, opened)

    val back: () -> Unit = {
        if (step == 2 && !busy) {
            step = 1
            failure = null
            data = null
            preview = null
            password = ""
            again = ""
        } else {
            onBack()
        }
    }

    val action: Pair<String, (() -> Unit)?> = when {
        step == TransferSteps -> strings.transferDone to (onFinished ?: onBack)
        step == 1 && mode == TransferMode.Export -> strings.transferContinue to { step = 2 }
        step == 1 -> strings.transferChooseFile to when (format) {
            TransferFormat.Backup -> openBackup
            TransferFormat.Csv -> openCsv
            TransferFormat.Zip -> openZip
        }
        mode == TransferMode.Export && format != TransferFormat.Csv -> {
            val ready = password.length >= MinBackupPassword && again == password
            val zip = format == TransferFormat.Zip
            strings.transferExportAction to (if (zip) saveZip else saveBackup)?.takeIf { ready }?.let { save ->
                {
                    run {
                        val bytes = if (zip) actions.exportArchive(password) else actions.export(password)
                        savedName = transferName(if (zip) "zip" else "renvault")
                        save(savedName, bytes)
                    }
                }
            }
        }
        mode == TransferMode.Export -> strings.transferCsvConfirm to saveCsv?.let { save ->
            {
                run {
                    val bytes = actions.exportCsv()
                    savedName = transferName("csv")
                    save(savedName, bytes)
                }
            }
        }
        preview?.locked == true -> strings.transferOpen to data?.takeIf { password.isNotEmpty() }?.let { bytes ->
            {
                run {
                    preview = if (format == TransferFormat.Backup) {
                        actions.inspectBackup(bytes, password)
                    } else {
                        actions.inspectArchive(bytes, password)
                    }
                }
            }
        }
        else -> strings.transferImportAction to data?.takeIf { (preview?.entries ?: 0) + (preview?.passkeys ?: 0) > 0 }?.let { bytes ->
            {
                run {
                    result = when (format) {
                        TransferFormat.Backup -> actions.restore(bytes, password)
                        TransferFormat.Csv -> actions.importCsv(bytes)
                        TransferFormat.Zip -> actions.importArchive(bytes, password)
                    }
                    step = TransferSteps
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            RvPageBar(
                title = title,
                scrolled = false,
                background = background,
                navigationIcon = {
                    IconButton(onClick = back, shapes = IconButtonDefaults.shapes()) {
                        Icon(RvBackIcon, contentDescription = strings.navBack)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .padding(horizontal = RvLayout.gutter),
        ) {
            RvPageTitle(title)
            AnimatedVisibility(
                visible = step != TransferSteps,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                StepHeader(step = step, strings = strings)
            }

            AnimatedContent(
                targetState = step to (preview?.locked == true),
                transitionSpec = {
                    val forward = targetState.first >= initialState.first
                    val shift = if (forward) 1 else -1
                    (
                        slideInHorizontally(motion.defaultSpatialSpec()) { shift * it / 10 } +
                            fadeIn(motion.defaultEffectsSpec())
                        ) togetherWith (
                        slideOutHorizontally(motion.fastSpatialSpec()) { -shift * it / 10 } +
                            fadeOut(motion.fastEffectsSpec())
                        ) using SizeTransform(clip = false)
                },
                modifier = Modifier.weight(1f),
                label = "transferStep",
            ) { (shownStep, _) ->
                val done = shownStep == TransferSteps
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (done) Modifier else Modifier.verticalScroll(rememberScrollState()))
                        .padding(bottom = 16.dp),
                    verticalArrangement = if (done) {
                        Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    } else {
                        Arrangement.spacedBy(16.dp)
                    },
                ) {
                    when {
                        shownStep == 1 -> {
                            StepIntro(
                                strings.transferFormatTitle,
                                if (mode == TransferMode.Export) strings.transferExportFormatIntro else strings.transferImportFormatIntro,
                            )
                            RvSection(
                                footer = when {
                                    mode == TransferMode.Import -> null
                                    format == TransferFormat.Backup -> strings.transferBackupNote
                                    format == TransferFormat.Zip -> strings.transferZipNote
                                    else -> strings.transferCsvNote
                                },
                            ) {
                                options.forEachIndexed { index, option ->
                                    RvSegment(
                                        index = index,
                                        count = options.size,
                                        onClick = { format = option.format },
                                        selected = format == option.format,
                                        leading = { SettingBadge(option.icon, option.tone) },
                                        supporting = { Text(option.detail) },
                                        trailing = { SelectionDot(format == option.format) },
                                    ) {
                                        Text(option.title)
                                    }
                                }
                            }
                        }
                        shownStep == TransferSteps && mode == TransferMode.Export -> {
                            DoneHero(strings.transferSavedTitle, null)
                            FileCard(savedName, chosen)
                        }
                        shownStep == TransferSteps -> {
                            val outcome = result ?: RvImported(0, 0)
                            if (outcome.added + outcome.updated == 0) {
                                DoneHero(strings.transferUpToDate, strings.backupNothing, RvInfoIcon, accents.blue)
                            } else {
                                DoneHero(strings.transferResultTitle, null)
                                ResultTiles(
                                    listOfNotNull(
                                        Triple(outcome.added, strings.transferCountAdded, accents.green).takeIf { outcome.added > 0 },
                                        Triple(outcome.updated, strings.transferCountUpdated, accents.blue).takeIf { outcome.updated > 0 },
                                    ),
                                )
                            }
                        }
                        mode == TransferMode.Export && format != TransferFormat.Csv -> {
                            StepIntro(
                                strings.transferProtectTitle,
                                if (format == TransferFormat.Zip) strings.transferZipHint else strings.backupExportHint,
                            )
                            BackupPasswordFields(
                                password = password,
                                again = again,
                                shown = shown,
                                busy = busy,
                                failure = failure,
                                strings = strings,
                                onPassword = { password = it; failure = null },
                                onAgain = { again = it },
                                onShown = { shown = it },
                                onDone = { action.second?.invoke() },
                            )
                        }
                        mode == TransferMode.Export -> {
                            StepIntro(strings.transferCsvTitle, null)
                            WarningCard(strings.transferCsvWarning)
                            Text(
                                text = strings.transferCsvLeftOut,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            FailureText(failure)
                        }
                        else -> {
                            val current = preview
                            if (current?.locked == true) {
                                val backup = format == TransferFormat.Backup
                                StepIntro(if (backup) strings.transferUnlockBackupTitle else strings.transferUnlockArchiveTitle, null)
                                SourceCard(current, chosen, strings)
                                Text(
                                    text = if (backup) strings.backupRestoreHint else strings.transferArchiveHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                                PasswordField(
                                    value = password,
                                    label = if (backup) strings.backupPassword else strings.transferArchivePassword,
                                    shown = shown,
                                    busy = busy,
                                    failure = failure,
                                    onValue = { password = it; failure = null },
                                    onShown = { shown = it },
                                    onDone = { action.second?.invoke() },
                                )
                            } else if (current != null) {
                                StepIntro(strings.transferCheckTitle, null)
                                SourceCard(current, chosen, strings)
                                CountList(
                                    listOfNotNull(
                                        CountRow(RvKeyIcon, accents.blue, strings.transferCountEntries, current.entries),
                                        CountRow(RvCodesIcon, accents.yellow, strings.transferCountCodes, current.codes).takeIf { current.codes > 0 },
                                        CountRow(RvPasskeyIcon, accents.green, strings.transferCountPasskeys, current.passkeys).takeIf { current.passkeys > 0 },
                                        CountRow(RvRemoveCircleIcon, accents.red, strings.transferCountSkipped, current.skipped).takeIf { current.skipped > 0 },
                                    ),
                                )
                                FailureText(failure)
                            }
                        }
                    }
                }
            }

            if (step == 1) FailureText(failure)

            Button(
                onClick = { action.second?.invoke() },
                enabled = action.second != null && !busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight).padding(bottom = 16.dp),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(action.first, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepHeader(step: Int, strings: RvStrings) {
    val motion = MaterialTheme.motionScheme
    val progress by animateFloatAsState(step / TransferSteps.toFloat(), motion.slowSpatialSpec(), label = "transferProgress")
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = strings.transferStep(step.toString(), TransferSteps.toString()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            amplitude = { if (it >= 1f) 0f else 1f },
        )
    }
}

@Composable
private fun StepIntro(title: String, body: String?) {
    Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SourceCard(preview: RvImportPreview, option: FormatOption, strings: RvStrings) {
    val name = SourceNames[preview.source] ?: if (option.format == TransferFormat.Csv) strings.transferSourceUnknown else option.title
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = option.tone.container,
        contentColor = option.tone.content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RvShapeBadge(
                icon = option.icon,
                tone = RvTone(option.tone.content, option.tone.container),
                polygon = MaterialShapes.Cookie9Sided,
                size = 48.dp,
            )
            Column {
                Text(strings.transferDetected, style = MaterialTheme.typography.labelMedium)
                Text(name, style = MaterialTheme.typography.titleLargeEmphasized)
            }
        }
    }
}

private class CountRow(val icon: ImageVector, val tone: RvTone, val label: String, val value: Int)

@Composable
private fun CountList(rows: List<CountRow>) {
    RvSection {
        rows.forEachIndexed { index, row ->
            RvSegment(
                index = index,
                count = rows.size,
                leading = { SettingBadge(row.icon, row.tone) },
                trailing = {
                    Text(row.value.toString(), style = MaterialTheme.typography.headlineSmallEmphasized)
                },
            ) {
                Text(row.label)
            }
        }
    }
}

@Composable
private fun ColumnScope.DoneHero(
    title: String,
    detail: String?,
    icon: ImageVector = RvCheckIcon,
    tone: RvTone = RenVault.accents.green,
) {
    val motion = MaterialTheme.motionScheme
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, motion.slowSpatialSpec()) }
    RvShapeBadge(
        icon = icon,
        tone = tone,
        polygon = MaterialShapes.Cookie12Sided,
        size = 120.dp,
        iconSize = 56.dp,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
                rotationZ = (1f - entrance.value) * -90f
            },
    )
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMediumEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (!detail.isNullOrEmpty()) {
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultTiles(tiles: List<Triple<Int, String, RvTone>>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.forEach { (value, label, tone) ->
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = tone.container,
                contentColor = tone.content,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value.toString(), style = MaterialTheme.typography.displaySmallEmphasized)
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun FileCard(name: String, option: FormatOption) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingBadge(option.icon, option.tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    val accents = RenVault.accents
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = accents.red.container,
        contentColor = accents.red.content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(RvWarningIcon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FailureText(failure: String?) {
    AnimatedVisibility(visible = failure != null) {
        Text(
            text = failure.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    shown: Boolean,
    busy: Boolean,
    failure: String?,
    onValue: (String) -> Unit,
    onShown: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    RvTextField(
        value = value,
        onValueChange = onValue,
        label = label,
        enabled = !busy,
        secret = true,
        shown = shown,
        onShownChange = onShown,
        leading = RvKeyIcon,
        error = failure,
        imeAction = ImeAction.Done,
        onDone = onDone,
    )
}

@Composable
private fun BackupPasswordFields(
    password: String,
    again: String,
    shown: Boolean,
    busy: Boolean,
    failure: String?,
    strings: RvStrings,
    onPassword: (String) -> Unit,
    onAgain: (String) -> Unit,
    onShown: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val short = password.isNotEmpty() && password.length < MinBackupPassword
    val mismatch = again.isNotEmpty() && again != password
    val level = strengthOf(password)
    RvTextField(
        value = password,
        onValueChange = onPassword,
        label = strings.backupPassword,
        enabled = !busy,
        secret = true,
        shown = shown,
        onShownChange = onShown,
        leading = RvKeyIcon,
        error = failure ?: if (short) strings.backupPasswordShort else null,
        imeAction = ImeAction.Next,
    )
    AnimatedVisibility(visible = password.isNotEmpty()) {
        RvStrengthMeter(
            strength = level,
            label = when (level) {
                RvPasswordStrength.Weak -> strings.strengthWeak
                RvPasswordStrength.Fair -> strings.strengthFair
                RvPasswordStrength.Strong -> strings.strengthStrong
                RvPasswordStrength.None -> null
            },
        )
    }
    RvTextField(
        value = again,
        onValueChange = onAgain,
        label = strings.backupPasswordConfirm,
        enabled = !busy,
        secret = true,
        shown = shown,
        onShownChange = onShown,
        leading = RvKeyIcon,
        error = if (mismatch) strings.backupPasswordMismatch else null,
        imeAction = ImeAction.Done,
        onDone = onDone,
    )
}

@Composable
private fun SelectionDot(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val dot by animateDpAsState(if (selected) 10.dp else 0.dp, tween(180), label = "selectionDot")
    val ring by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant, tween(180), label = "selectionRing")
    Box(
        modifier = Modifier.size(20.dp).border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(dot.coerceAtLeast(0.dp)).background(ring, CircleShape))
    }
}

@OptIn(ExperimentalTime::class)
private fun transferName(extension: String): String =
    "$RvAppName-${Clock.System.now().toString().substringBefore('T')}.$extension"
