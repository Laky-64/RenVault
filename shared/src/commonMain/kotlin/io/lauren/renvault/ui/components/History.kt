package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lauren.renvault.bridge.RvGeneratedPassword
import io.lauren.renvault.bridge.RvPasswordVersion
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.RvDateStyle
import io.lauren.renvault.lib.RvSecureScreen
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.formatDate
import io.lauren.renvault.lib.relativeOf
import io.lauren.renvault.ui.theme.RenVault
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val Masked = 12

@Composable
fun rememberPasswordHistory(itemId: String, revision: Long): List<RvPasswordVersion> {
    var versions by remember(itemId) { mutableStateOf<List<RvPasswordVersion>>(emptyList()) }
    LaunchedEffect(itemId, revision) {
        versions = runCatching { Vault.passwordHistory(itemId) }.getOrDefault(emptyList())
    }
    return versions
}

@Composable
fun RvPasswordHistorySheet(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    versions: List<RvPasswordVersion>,
    strings: RvStrings,
    onRestore: (String) -> Unit,
) {
    if (!open) return
    RvSecureScreen()

    val snackbar = remember { SnackbarHostState() }
    val copy = rememberSheetCopy(snackbar, strings)

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf<String?>(null) }
    val tone = RenVault.accents.orange

    ModalBottomSheet(
        onDismissRequest = { onOpenChange(false) },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = strings.historyTitle,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.fillMaxWidth(),
                )
                RvSection {
                    versions.forEachIndexed { index, version ->
                        SecretRow(
                            index = index,
                            count = versions.size,
                            password = version.password,
                            detail = strings.historyUntil(formatDate(version.until, RvDateStyle.Medium)),
                            leading = { RvShapeBadge(icon = RvHistoryIcon, tone = tone, polygon = MaterialShapes.Circle, size = 40.dp, iconSize = 22.dp) },
                            strings = strings,
                            onCopy = copy,
                            onClick = { restoring = version.password },
                        )
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            val chosen = restoring
            RvConfirmDialog(
                open = chosen != null,
                onOpenChange = { if (!it) restoring = null },
                title = strings.historyRestoreTitle,
                description = strings.historyRestoreDesc,
                confirm = strings.historyRestoreConfirm,
                cancel = strings.cancel,
                icon = RvRestoreIcon,
                onConfirm = {
                    chosen?.let { password ->
                        onRestore(password)
                        scope.launch { sheet.hide() }.invokeOnCompletion { onOpenChange(false) }
                    }
                },
            )
        }
    }
}

@Composable
fun RvGeneratedSheet(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    strings: RvStrings,
    onUse: ((String) -> Unit)? = null,
) {
    if (!open) return
    RvSecureScreen()

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copy = rememberSheetCopy(snackbar, strings)
    var items by remember { mutableStateOf<List<RvGeneratedPassword>?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    var gone by remember { mutableStateOf(emptySet<String>()) }
    val drop: (Set<String>) -> Unit = { passwords ->
        gone = gone + passwords
        scope.launch {
            delay(RemoveHold)
            items = items?.filter { it.password !in passwords }
            gone = gone - passwords
        }
    }
    LaunchedEffect(Unit) {
        items = runCatching { Vault.generatedPasswords() }.getOrDefault(emptyList())
    }

    if (items == null) return

    ModalBottomSheet(
        onDismissRequest = { onOpenChange(false) },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.generatedTitle,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                    if (items.orEmpty().any { it.password !in gone }) {
                        IconButton(
                            onClick = { clearing = true },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(RvDeleteIcon, contentDescription = strings.generatedClear)
                        }
                    }
                }
                val list = items
                when {
                    list == null -> Unit
                    list.isEmpty() -> Text(
                        text = strings.generatedEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                    else -> {
                        val shown = list.filter { it.password !in gone }
                        val hints = listOfNotNull(strings.generatedUseHint.takeIf { onUse != null }, strings.generatedNote)
                        RvSection(footer = hints.joinToString(" ")) {
                            list.forEach { entry ->
                                val index = shown.indexOf(entry)
                                AnimatedVisibility(
                                    visible = entry.password !in gone,
                                    enter = fadeIn(),
                                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                        shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                                ) {
                                val site = entry.domain.ifEmpty { null }
                                val state = if (entry.saved) strings.generatedSaved else strings.generatedUnsaved
                                val ago = relativeOf(entry.at)?.let { strings.relativeTime(it) }
                                SecretRow(
                                    index = index.coerceAtLeast(0),
                                    count = shown.size.coerceAtLeast(1),
                                    password = entry.password,
                                    detail = listOfNotNull(site, state, ago).joinToString(" · "),
                                    leading = {
                                        RvSiteIcon(
                                            fallback = entry.domain,
                                            glyph = if (entry.domain.isEmpty()) RvKeyIcon else null,
                                            size = 40.dp,
                                            shape = MaterialShapes.Cookie9Sided.toShape(),
                                        )
                                    },
                                    strings = strings,
                                    onCopy = copy,
                                    onClick = onUse?.let { use ->
                                        {
                                            use(entry.password)
                                            scope.launch { sheet.hide() }.invokeOnCompletion { onOpenChange(false) }
                                        }
                                    },
                                    actions = listOfNotNull(
                                        onUse?.let { use ->
                                            RowAction(strings.generatorUseShort, RvCheckIcon) {
                                                use(entry.password)
                                                scope.launch { sheet.hide() }.invokeOnCompletion { onOpenChange(false) }
                                            }
                                        },
                                        RowAction(strings.generatedRemoveConfirm, RvDeleteIcon, danger = true) { removing = entry.password },
                                    ),
                                )
                            }
                                }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            RvConfirmDialog(
                open = clearing,
                onOpenChange = { clearing = it },
                title = strings.generatedClearTitle,
                description = strings.generatedClearDesc,
                confirm = strings.generatedClear,
                cancel = strings.cancel,
                danger = true,
                icon = RvDeleteIcon,
                onConfirm = {
                    clearing = false
                    scope.launch { runCatching { Vault.clearGenerated() } }
                    drop(items.orEmpty().map { it.password }.toSet())
                },
            )

            val target = removing
            RvConfirmDialog(
                open = target != null,
                onOpenChange = { if (!it) removing = null },
                title = strings.generatedRemoveTitle,
                description = strings.generatedRemoveDesc,
                confirm = strings.generatedRemoveConfirm,
                cancel = strings.cancel,
                danger = true,
                icon = RvDeleteIcon,
                onConfirm = {
                    target?.let { password ->
                        scope.launch { runCatching { Vault.removeGenerated(password) } }
                        drop(setOf(password))
                    }
                },
            )
        }
    }
}

@Composable
private fun rememberSheetCopy(snackbar: SnackbarHostState, strings: RvStrings): (String) -> Unit {
    val copy = rememberClipboardCopy()
    val scope = rememberCoroutineScope()
    return { text ->
        copy(text, true)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(strings.fieldCopied)
        }
    }
}

private val RemoveHold = 320.milliseconds

private class RowAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val run: () -> Unit)

@Composable
private fun SecretRow(
    index: Int,
    count: Int,
    password: String,
    detail: String,
    leading: @Composable () -> Unit,
    strings: RvStrings,
    onCopy: (String) -> Unit,
    onClick: (() -> Unit)? = null,
    actions: List<RowAction> = emptyList(),
) {
    var shown by remember(password) { mutableStateOf(false) }
    val menuable = actions.isNotEmpty()
    var menu by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(IntOffset.Zero) }
    var pointerMenu by remember { mutableStateOf(false) }
    val all = listOf(RowAction(strings.generatorCopy, RvCopyIcon) { onCopy(password) }) + actions
    val dismiss = {
        menu = false
        pointerMenu = false
    }

    Box(
        modifier = if (!menuable) Modifier else Modifier.pointerInput(password) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        val at = event.changes.first().position
                        pointer = IntOffset(at.x.roundToInt(), at.y.roundToInt())
                        pointerMenu = true
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        },
    ) {
        RvSegment(
            index = index,
            count = count,
            onClick = onClick ?: { shown = !shown },
            onLongClick = if (menuable) {
                { menu = true }
            } else {
                null
            },
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            leading = leading,
            supporting = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { shown = !shown }, shapes = IconButtonDefaults.shapes()) {
                        Icon(if (shown) RvVisibilityOffIcon else RvVisibilityIcon, contentDescription = null)
                    }
                    if (menuable) {
                        Box {
                            IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(RvMoreIcon, contentDescription = strings.generatorMore)
                            }
                            ActionMenu(expanded = menu, actions = all, onDismiss = dismiss)
                        }
                    } else {
                        IconButton(onClick = { onCopy(password) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(RvCopyIcon, contentDescription = strings.generatorCopy)
                        }
                    }
                }
            },
        ) {
            Text(
                text = if (shown) password else "•".repeat(Masked),
                fontFamily = if (shown) FontFamily.Monospace else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (menuable) {
            Box(modifier = Modifier.offset { pointer }.size(0.dp)) {
                ActionMenu(expanded = pointerMenu, actions = all, onDismiss = dismiss)
            }
        }
    }
}

@Composable
private fun ActionMenu(expanded: Boolean, actions: List<RowAction>, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
    ) {
        actions.sortedBy { it.danger }.forEach { action ->
            val tint = if (action.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            DropdownMenuItem(
                text = { Text(action.label, color = tint) },
                leadingIcon = { Icon(action.icon, contentDescription = null, tint = tint) },
                onClick = {
                    onDismiss()
                    action.run()
                },
            )
        }
    }
}

