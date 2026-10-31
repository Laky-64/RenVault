package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.lauren.renvault.failureText
import io.lauren.renvault.lib.MinPassword
import io.lauren.renvault.lib.RvStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RvMasterPasswordDialog(
    strings: RvStrings,
    onDismiss: () -> Unit,
    onChange: suspend (current: String, next: String) -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val short = next.isNotEmpty() && next.length < MinPassword
    val mismatch = again.isNotEmpty() && again != next
    val ready = current.isNotEmpty() && next.length >= MinPassword && again == next && !busy

    val submit: () -> Unit = {
        if (ready) {
            scope.launch {
                busy = true
                val result = runCatching { onChange(current, next) }
                busy = false
                result.onSuccess { onChanged() }.onFailure { failure = strings.failureText(it) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(RvKeyIcon, contentDescription = null) },
        title = { Text(strings.masterChangeTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(strings.masterChangeDesc)
                RvTextField(
                    value = current,
                    onValueChange = {
                        current = it
                        failure = null
                    },
                    label = strings.masterCurrent,
                    enabled = !busy,
                    secret = true,
                    shown = shown,
                    onShownChange = { shown = it },
                    leading = RvLockIcon,
                    error = failure,
                    imeAction = ImeAction.Next,
                )
                RvTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = strings.masterNew,
                    enabled = !busy,
                    secret = true,
                    shown = shown,
                    onShownChange = { shown = it },
                    leading = RvKeyIcon,
                    error = if (short) strings.authMasterTooShort(MinPassword) else null,
                    imeAction = ImeAction.Next,
                )
                RvTextField(
                    value = again,
                    onValueChange = { again = it },
                    label = strings.masterRepeat,
                    enabled = !busy,
                    secret = true,
                    shown = shown,
                    onShownChange = { shown = it },
                    leading = RvKeyIcon,
                    error = if (mismatch) strings.authMasterMismatch else null,
                    imeAction = ImeAction.Done,
                    onDone = submit,
                )
            }
        },
        confirmButton = {
            Button(onClick = submit, enabled = ready, shapes = ButtonDefaults.shapes()) { Text(strings.masterChange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) }
        },
    )
}
