package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.tidyWebsites
import io.lauren.renvault.lib.websitesOk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun RvConfirmDialog(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    title: String,
    description: String,
    confirm: String,
    cancel: String,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onConfirm: () -> Unit = {},
) {
    if (!open) return

    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = { onOpenChange(false) },
        icon = icon?.let { glyph -> { Icon(glyph, contentDescription = null) } },
        iconContentColor = if (danger) colors.error else colors.secondary,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            Button(
                onClick = {
                    onOpenChange(false)
                    onConfirm()
                },
                shapes = ButtonDefaults.shapes(),
                colors = if (danger) {
                    ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = { onOpenChange(false) }, shapes = ButtonDefaults.shapes()) {
                Text(cancel)
            }
        },
    )
}

@Composable
fun RvLoadingDialog(shown: Boolean) {
    if (!shown) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
    }
}

@Composable
fun <T> RvChoiceDialog(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    title: String,
    choices: List<T>,
    selected: T?,
    label: (T) -> String,
    cancel: String,
    onSelect: (T) -> Unit,
) {
    if (!open) return

    AlertDialog(
        onDismissRequest = { onOpenChange(false) },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                choices.forEach { choice ->
                    val picked = choice == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(
                                selected = picked,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(choice)
                                    onOpenChange(false)
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(selected = picked, onClick = null)
                        Text(label(choice), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpenChange(false) }, shapes = ButtonDefaults.shapes()) {
                Text(cancel)
            }
        },
    )
}

private val OtpAuth = Regex("^otpauth://", RegexOption.IGNORE_CASE)
private val OtpSecret = Regex("[?&]secret=[a-z2-7]+", RegexOption.IGNORE_CASE)
private val Base32 = Regex("^[a-z2-7]+$", RegexOption.IGNORE_CASE)
private val Padding = Regex("=+$")
private val Loose = Regex("[\\s-]")

private fun usable(raw: String): Boolean {
    val value = raw.trim()
    if (value.isEmpty()) return false
    if (OtpAuth.containsMatchIn(value)) return OtpSecret.containsMatchIn(value)
    return Base32.matches(value.replace(Loose, "").replace(Padding, ""))
}

@Composable
fun RvTotpDialog(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    title: String,
    description: String,
    placeholder: String,
    confirm: String,
    cancel: String,
    onKey: (String) -> Unit = {},
) {
    if (!open) return

    var key by remember { mutableStateOf("") }
    val ready = usable(key)
    val submit = {
        if (ready) {
            onKey(key.trim())
            onOpenChange(false)
        }
    }

    AlertDialog(
        onDismissRequest = { onOpenChange(false) },
        icon = { Icon(RvCodesIcon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(description)
                RvTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = placeholder,
                    capitalization = KeyboardCapitalization.Characters,
                    keyboard = KeyboardType.Ascii,
                    onDone = submit,
                )
            }
        },
        confirmButton = {
            Button(onClick = submit, enabled = ready, shapes = ButtonDefaults.shapes()) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = { onOpenChange(false) }, shapes = ButtonDefaults.shapes()) { Text(cancel) }
        },
    )
}

private val RowDelay = 220.milliseconds

private class WebRow(val id: Int, initial: String, fresh: Boolean) {
    var value by mutableStateOf(initial)
    val visible = MutableTransitionState(!fresh)
}

@Composable
fun RvWebsitesDialog(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    title: String,
    websites: List<String>,
    onWebsites: (List<String>) -> Unit,
    confirm: String,
    cancel: String,
    heading: String,
    description: String,
    placeholder: String,
    addLabel: String,
) {
    if (!open) return

    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<WebRow>() }
    var nextId by remember { mutableIntStateOf(0) }
    var focusId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        rows.clear()
        websites.forEach { rows += WebRow(nextId++, it, fresh = false) }
    }

    val values = rows.map { it.value }
    val canApply = websitesOk(values)

    AlertDialog(
        onDismissRequest = { onOpenChange(false) },
        icon = { Icon(RvGlobeIcon, contentDescription = null) },
        title = { Text(heading) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(description, modifier = Modifier.padding(bottom = 8.dp))

                rows.forEach { row ->
                    key(row.id) {
                        LaunchedEffect(row) { row.visible.targetState = true }
                        val requester = remember { FocusRequester() }
                        LaunchedEffect(focusId) {
                            if (focusId == row.id) runCatching { requester.requestFocus() }
                        }

                        AnimatedVisibility(
                            visibleState = row.visible,
                            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        ) {
                            RvTextField(
                                value = row.value,
                                onValueChange = { row.value = it },
                                label = placeholder,
                                keyboard = KeyboardType.Uri,
                                focusRequester = requester,
                                trailing = {
                                    IconButton(
                                        onClick = {
                                            row.visible.targetState = false
                                            scope.launch {
                                                delay(RowDelay)
                                                rows.remove(row)
                                            }
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(
                                            imageVector = RvRemoveCircleIcon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            val row = WebRow(nextId++, "", fresh = true)
                            rows += row
                            focusId = row.id
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(RvAddIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(addLabel, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onWebsites(tidyWebsites(values))
                    onOpenChange(false)
                },
                enabled = canApply,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = { onOpenChange(false) }, shapes = ButtonDefaults.shapes()) { Text(cancel) }
        },
    )
}

data class RvAnisetteChoice(
    val name: String,
    val address: String,
    val latencyMs: Long,
    val reachable: Boolean,
)

val LocalAnisetteServers = compositionLocalOf<suspend () -> List<RvAnisetteChoice>> { { emptyList() } }

private const val CustomServer = "\u0000custom"

@Composable
fun RvAnisetteDialog(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    current: String,
    strings: RvStrings,
    onSave: suspend (String) -> Boolean,
) {
    if (!open) return

    val scope = rememberCoroutineScope()
    val loadServers = LocalAnisetteServers.current
    var servers by remember { mutableStateOf<List<RvAnisetteChoice>?>(null) }
    var picked by remember { mutableStateOf(current) }
    var custom by remember { mutableStateOf(current) }
    var refused by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = runCatching { loadServers() }.getOrDefault(emptyList())
        servers = loaded
        if (current.isNotEmpty() && loaded.none { it.address == current }) picked = CustomServer
    }

    val target = if (picked == CustomServer) custom else picked
    val apply: () -> Unit = {
        if (!busy) {
            scope.launch {
                busy = true
                val taken = onSave(target)
                busy = false
                if (taken) onOpenChange(false) else refused = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = { onOpenChange(false) },
        icon = { Icon(RvServerIcon, contentDescription = null) },
        title = { Text(strings.anisetteTitle) },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp)) {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(strings.anisetteDesc)
                    Column(modifier = Modifier.selectableGroup()) {
                        AnisetteRow(
                            title = strings.anisetteDefault,
                            detail = strings.anisetteDefaultDesc,
                            selected = picked.isEmpty(),
                            enabled = !busy,
                            onClick = { picked = "" },
                        )
                        val loaded = servers
                        if (loaded == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
                                Text(strings.anisetteChecking, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            loaded.forEach { server ->
                                AnisetteRow(
                                    title = server.name,
                                    detail = server.address.substringAfter("://"),
                                    selected = picked == server.address,
                                    enabled = !busy && server.reachable,
                                    status = when {
                                        !server.reachable -> strings.anisetteUnreachable
                                        server.address.startsWith("http://") -> strings.anisetteInsecure
                                        else -> strings.anisetteLatency(server.latencyMs.toString())
                                    },
                                    good = server.reachable && server.address.startsWith("https://"),
                                    onClick = { picked = server.address },
                                )
                            }
                        }
                        AnisetteRow(
                            title = strings.anisetteCustom,
                            detail = null,
                            selected = picked == CustomServer,
                            enabled = !busy,
                            onClick = { picked = CustomServer },
                        )
                    }
                    AnimatedVisibility(visible = picked == CustomServer) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RvTextField(
                                value = custom,
                                onValueChange = {
                                    custom = it
                                    refused = false
                                },
                                label = strings.anisettePlaceholder,
                                enabled = !busy,
                                keyboard = KeyboardType.Uri,
                                error = if (refused) strings.anisetteInvalid else null,
                                supporting = strings.anisetteNote,
                                onDone = apply,
                            )
                        }
                    }
                }
                InsecureWarning(
                    text = strings.anisetteInsecureWarning,
                    visible = target.trim().startsWith("http://", ignoreCase = true),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = apply,
                enabled = !busy && (picked != CustomServer || custom.isNotBlank()),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(strings.editorSave)
            }
        },
        dismissButton = {
            TextButton(onClick = { onOpenChange(false) }, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) }
        },
    )
}

@Composable
private fun AnisetteRow(
    title: String,
    detail: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    status: String? = null,
    good: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !enabled -> colors.error
                    good -> colors.primary
                    else -> colors.tertiary
                },
            )
        }
    }
}

@Composable
private fun InsecureWarning(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(motion.defaultSpatialSpec()) +
            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f) +
            fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) +
            scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f) +
            fadeOut(motion.fastEffectsSpec()),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = RvWarningIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
