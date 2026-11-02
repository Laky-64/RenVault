package io.lauren.renvault.ui.components

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.lauren.renvault.lib.RvDateStyle
import io.lauren.renvault.lib.localizedDate
import io.lauren.renvault.ui.theme.RvTone
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.relativeOf
import io.lauren.renvault.ui.theme.RenVault
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

data class RvDevice(
    val id: String,
    val name: String,
    val self: Boolean,
    val syncedAt: Long,
    val online: Boolean = false,
    val platform: String = "",
    val model: String = "",
    val version: String = "",
    val joined: Long = 0,
    val address: String = "",
)

class RvPairCode(val code: String, val size: Int, val modules: ByteArray)

interface RvDeviceActions {
    suspend fun startPairing(): RvPairCode
    suspend fun waitPairing(): String
    suspend fun join(text: String): String
    suspend fun remove(id: String)
    suspend fun syncNow()
    suspend fun setProfile(name: String, photo: ByteArray?, setPhoto: Boolean)
    fun describe(failure: Throwable): String
}

private val ClockTick = 30.seconds
private val OnlineDot = 14.dp
private val OnlineGap = 2.5.dp

@OptIn(ExperimentalTime::class)
@Composable
internal fun DevicesPage(
    devices: List<RvDevice>,
    actions: RvDeviceActions,
    strings: RvStrings,
    dialog: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val accents = RenVault.accents
    val list = rememberLazyListState()
    val scrolled by list.scrolledPast()
    val background = if (dialog) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface

    var hosting by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<RvDevice?>(null) }
    var shown by remember { mutableStateOf<RvDevice?>(null) }

    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ClockTick)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }

    val syncNow: () -> Unit = {
        if (!syncing) {
            scope.launch {
                syncing = true
                val done = try {
                    actions.syncNow()
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    false
                }
                syncing = false
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(if (done) strings.devicesSyncDone else strings.devicesSyncFailed)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RvPageBar(
                title = strings.devicesTitle,
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { RvPageTitle(strings.devicesTitle) }

            item {
                Text(
                    text = strings.devicesIntro,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                RvSection {
                    devices.forEachIndexed { index, device ->
                        RvSegment(
                            index = index,
                            count = devices.size,
                            onClick = { shown = device },
                            leading = { DeviceBadge(device, accents.forIndex(index)) },
                            supporting = {
                                Column {
                                    machineOf(device)?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    DeviceStatus(device, statusOf(device, now, strings), MaterialTheme.typography.bodySmall)
                                }
                            },
                            trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                        ) {
                            Text(device.name)
                        }
                    }
                }
            }

            item {
                RvSection(footer = strings.devicesFooter) {
                    RvSegment(
                        index = 0,
                        count = 3,
                        onClick = { hosting = true },
                        leading = { SettingBadge(RvQrCodeIcon, accents.blue) },
                    ) {
                        Text(strings.devicesAdd)
                    }
                    RvSegment(
                        index = 1,
                        count = 3,
                        onClick = { joining = true },
                        leading = { SettingBadge(RvQrScanIcon, accents.teal) },
                    ) {
                        Text(strings.devicesJoin)
                    }
                    RvSegment(
                        index = 2,
                        count = 3,
                        onClick = syncNow,
                        leading = {
                            if (syncing) {
                                LoadingIndicator(modifier = Modifier.size(40.dp))
                            } else {
                                SettingBadge(RvSyncIcon, accents.orange)
                            }
                        },
                    ) {
                        Text(strings.devicesSyncNow)
                    }
                }
            }
        }
    }

    if (hosting) {
        PairHostDialog(actions = actions, strings = strings, onDismiss = { hosting = false })
    }

    if (joining) {
        PairJoinDialog(actions = actions, strings = strings, onDismiss = { joining = false })
    }

    shown?.let { device ->
        DeviceSheet(
            device = device,
            tone = accents.forIndex(devices.indexOfFirst { it.id == device.id }.coerceAtLeast(0)),
            status = statusOf(device, now, strings),
            strings = strings,
            onRemove = {
                shown = null
                removing = device
            },
            onDismiss = { shown = null },
        )
    }

    val target = removing
    RvConfirmDialog(
        open = target != null,
        onOpenChange = { if (!it) removing = null },
        title = strings.devicesRemoveTitle(target?.name.orEmpty()),
        description = strings.devicesRemoveDesc,
        confirm = strings.devicesRemove,
        cancel = strings.cancel,
        danger = true,
        icon = RvDeleteIcon,
        onConfirm = {
            if (target != null) scope.launch { runCatching { actions.remove(target.id) } }
        },
    )
}

@Composable
private fun PairHostDialog(actions: RvDeviceActions, strings: RvStrings, onDismiss: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var pairing by remember { mutableStateOf<RvPairCode?>(null) }
    var joined by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        pairing = null
        joined = null
        failure = null
        try {
            pairing = actions.startPairing()
            joined = actions.waitPairing()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: Throwable) {
            failure = actions.describe(problem)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(RvQrCodeIcon, contentDescription = null) },
        title = { Text(strings.devicesAdd) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val shown = pairing
                when {
                    joined != null -> Text(strings.pairDone(joined.orEmpty()), textAlign = TextAlign.Center)
                    failure != null -> Text(failure.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    shown == null -> LoadingIndicator()
                    else -> {
                        Text(strings.pairHostHint, textAlign = TextAlign.Center)
                        RvQrCode(size = shown.size, modules = shown.modules, modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth())
                        Text(
                            text = shown.code.chunked(4).joinToString(" "),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                            Text(
                                text = strings.pairWaiting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                joined != null -> Button(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(strings.pairClose) }
                failure != null -> Button(onClick = { attempt++ }, shapes = ButtonDefaults.shapes()) { Text(strings.pairRetry) }
            }
        },
        dismissButton = if (joined == null) {
            { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) } }
        } else {
            null
        },
    )
}

@Composable
private fun PairJoinDialog(actions: RvDeviceActions, strings: RvStrings, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var joined by remember { mutableStateOf<String?>(null) }

    val join: (String) -> Unit = { text ->
        if (!busy && text.isNotBlank()) {
            scope.launch {
                busy = true
                failure = null
                try {
                    joined = actions.join(text)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (problem: Throwable) {
                    failure = actions.describe(problem)
                }
                busy = false
            }
        }
    }
    val scan = rememberQrScanner(onScanned = join)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (scan != null) RvQrScanIcon else RvKeyIcon, contentDescription = null) },
        title = { Text(strings.devicesJoin) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    joined != null -> Text(strings.pairDone(joined.orEmpty()), textAlign = TextAlign.Center)
                    busy -> LoadingIndicator()
                    else -> PairJoinForm(
                        code = code,
                        onCode = { code = it },
                        failure = failure,
                        onScan = scan,
                        onSubmit = { join(code) },
                        strings = strings,
                    )
                }
            }
        },
        confirmButton = {
            when {
                joined != null -> Button(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(strings.pairClose) }
                else -> Button(
                    onClick = { join(code) },
                    enabled = !busy && code.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(strings.pairJoinAction) }
            }
        },
        dismissButton = if (joined == null) {
            { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) } }
        } else {
            null
        },
    )
}

@Composable
internal fun PairJoinForm(
    code: String,
    onCode: (String) -> Unit,
    failure: String?,
    onScan: (() -> Unit)?,
    onSubmit: () -> Unit,
    strings: RvStrings,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onScan != null) {
            RvButton(
                text = strings.pairScan,
                onClick = onScan,
                kind = RvButtonKind.Tonal,
                enabled = enabled,
                icon = RvQrScanIcon,
            )
        }
        RvTextField(
            value = code,
            onValueChange = { next -> onCode(next.filter { it.isDigit() || it == ' ' }.take(PairCodeLength + 1)) },
            label = strings.pairCodeLabel,
            enabled = enabled,
            leading = RvKeyIcon,
            keyboard = KeyboardType.Number,
            error = failure,
            onDone = onSubmit,
        )
    }
}

private const val PairCodeLength = 8

@Composable
internal fun RvQrCode(size: Int, modules: ByteArray, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(Color.White)
            .padding(16.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size <= 0 || modules.size < size * size) return@Canvas
            val cell = this.size.width / size
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (modules[y * size + x].toInt() == 0) continue
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileDialog(
    name: String,
    photo: ImageBitmap?,
    actions: RvDeviceActions,
    strings: RvStrings,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(name) }
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf(photo) }
    var changed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    val pick = rememberPhotoPicker { bytes ->
        scope.launch {
            val image = withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
            if (image != null) {
                picked = bytes
                preview = image
                changed = true
            }
        }
    }

    val save: () -> Unit = {
        if (!busy) {
            scope.launch {
                busy = true
                failure = null
                try {
                    actions.setProfile(draft, picked, changed)
                    onDismiss()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (problem: Throwable) {
                    failure = actions.describe(problem)
                }
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.profileEditTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RvProfileAvatar(size = 96.dp, photo = preview, shape = MaterialShapes.Cookie12Sided.toShape())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pick != null) {
                        OutlinedButton(onClick = pick, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                            Icon(RvAddPhotoIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text(strings.profilePhotoPick, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                    }
                    if (preview != null) {
                        TextButton(
                            onClick = {
                                picked = null
                                preview = null
                                changed = true
                            },
                            enabled = !busy,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(strings.profilePhotoRemove)
                        }
                    }
                }
                RvTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = strings.profileName,
                    enabled = !busy,
                    leading = RvPersonIcon,
                    capitalization = KeyboardCapitalization.Words,
                    error = failure,
                    onDone = save,
                )
            }
        },
        confirmButton = {
            Button(onClick = save, enabled = !busy, shapes = ButtonDefaults.shapes()) { Text(strings.editorSave) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) }
        },
    )
}

private fun machineOf(device: RvDevice): String? =
    listOf(device.platform, device.model).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }

private fun handheld(device: RvDevice): Boolean =
    device.platform.startsWith("Android") || device.platform.startsWith("iOS") || device.platform.startsWith("iPadOS")

private fun statusOf(device: RvDevice, now: Long, strings: RvStrings): String = when {
    device.self -> strings.devicesThis
    device.online -> strings.devicesOnline
    else -> relativeOf(device.syncedAt, now)
        ?.let { strings.devicesSynced(strings.relativeTime(it)) }
        ?: strings.devicesNever
}

@Composable
private fun DeviceBadge(device: RvDevice, tone: RvTone, size: Dp = 40.dp, marked: Boolean = true) {
    val dot = RenVault.accents.green.content
    val online = marked && device.online
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (online) {
                    val radius = minOf(this.size.minDimension * 0.14f, OnlineDot.toPx() / 2)
                    val reach = this.size.minDimension / 2 * 0.7071f
                    val center = Offset(this.size.width / 2 + reach, this.size.height / 2 + reach)
                    drawCircle(Color.Black, radius = radius + OnlineGap.toPx(), center = center, blendMode = BlendMode.Clear)
                    drawCircle(dot, radius = radius, center = center)
                }
            },
    ) {
        RvShapeBadge(
            icon = when {
                handheld(device) -> RvPhoneIcon
                device.platform.isNotEmpty() -> RvComputerIcon
                else -> RvDevicesIcon
            },
            tone = tone,
            polygon = MaterialShapes.Circle,
            size = size,
            iconSize = size * 0.55f,
        )
    }
}

@Composable
private fun DeviceStatus(device: RvDevice, text: String, style: TextStyle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (device.online) {
            Box(Modifier.size(8.dp).background(RenVault.accents.green.content, CircleShape))
        }
        Text(text, style = style, color = if (device.online) RenVault.accents.green.content else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceSheet(
    device: RvDevice,
    tone: RvTone,
    status: String,
    strings: RvStrings,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = buildList {
        if (device.platform.isNotBlank()) add(strings.devicesSystem to device.platform)
        if (device.model.isNotBlank()) add(strings.devicesModel to device.model)
        if (device.address.isNotBlank()) add(strings.devicesAddress to device.address)
        if (device.version.isNotBlank()) add(strings.devicesVersion to device.version)
        if (device.joined > 0) add(strings.devicesJoined to localizedDate(device.joined, RvDateStyle.Long))
        if (!device.self && device.syncedAt > 0) add(strings.devicesLastSync to localizedDate(device.syncedAt, RvDateStyle.Medium))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DeviceBadge(device, tone, size = 88.dp, marked = false)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                )
                DeviceStatus(device, status, MaterialTheme.typography.bodyMedium)
            }
            if (rows.isNotEmpty()) {
                RvSection(modifier = Modifier.fillMaxWidth()) {
                    rows.forEachIndexed { index, (label, value) ->
                        RvSegment(
                            index = index,
                            count = rows.size,
                            supporting = { Text(value) },
                        ) {
                            Text(label)
                        }
                    }
                }
            }
            if (!device.self) {
                FilledTonalButton(
                    onClick = onRemove,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(RvDeleteIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(strings.devicesRemove, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            }
        }
    }
}
