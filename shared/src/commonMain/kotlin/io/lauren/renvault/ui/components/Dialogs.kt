package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

private const val RowMs = 220L

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
                                                delay(RowMs)
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
    var value by remember { mutableStateOf(current) }
    var refused by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val apply: (String) -> Unit = { target ->
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(strings.anisetteDesc)
                RvTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        refused = false
                    },
                    label = strings.anisettePlaceholder,
                    enabled = !busy,
                    keyboard = KeyboardType.Uri,
                    error = if (refused) strings.anisetteInvalid else null,
                    supporting = if (value.isBlank()) strings.anisetteDefault else strings.anisetteNote,
                    onDone = { apply(value) },
                )
                if (current.isNotEmpty()) {
                    TextButton(onClick = { apply("") }, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                        Text(strings.anisetteReset)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { apply(value) }, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                Text(strings.editorSave)
            }
        },
        dismissButton = {
            TextButton(onClick = { onOpenChange(false) }, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) }
        },
    )
}
