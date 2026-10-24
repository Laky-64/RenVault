package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.lauren.renvault.bridge.FileFilter
import io.lauren.renvault.lib.RvStrings
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class RvImported(val added: Int, val updated: Int)

interface RvBackupActions {
    suspend fun export(password: String): ByteArray
    suspend fun restore(data: ByteArray, password: String): RvImported
    suspend fun importCsv(data: ByteArray): RvImported
    fun describe(failure: Throwable): String
}

internal class RvBackupFlow(
    val export: (() -> Unit)?,
    val restore: (() -> Unit)?,
    val importCsv: (() -> Unit)?,
    val busy: Boolean,
)

private const val MinBackupPassword = 8

@Composable
internal fun rememberBackupFlow(
    actions: RvBackupActions,
    strings: RvStrings,
    onMessage: (String) -> Unit,
    onImported: (RvImported) -> Unit = {},
): RvBackupFlow {
    val scope = rememberCoroutineScope()
    val message by rememberUpdatedState(onMessage)
    val imported by rememberUpdatedState(onImported)
    var exporting by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }

    val backupFilter = remember(strings) {
        FileFilter(strings.backupFileType, listOf("renvault"), listOf("application/octet-stream"))
    }
    val csvFilter = remember {
        FileFilter("CSV", listOf("csv"), listOf("text/csv", "text/comma-separated-values", "text/plain"))
    }

    val report: (RvImported) -> Unit = { result ->
        imported(result)
        message(
            if (result.added + result.updated == 0) {
                strings.backupNothing
            } else {
                strings.backupResult(result.added.toString(), result.updated.toString())
            },
        )
    }

    val saver = rememberFileSaver(strings.backupExport, backupFilter) { saved ->
        if (saved) message(strings.backupSaved)
    }
    val openBackup = rememberFileOpener(strings.backupRestore, backupFilter) { restoring = it }
    val openCsv = rememberFileOpener(strings.backupImportCsv, csvFilter) { data ->
        scope.launch {
            busy = true
            runCatching { actions.importCsv(data) }
                .onSuccess(report)
                .onFailure { message(actions.describe(it)) }
            busy = false
        }
    }

    if (exporting && saver != null) {
        BackupPasswordDialog(
            icon = RvDownloadIcon,
            title = strings.backupExport,
            hint = strings.backupExportHint,
            action = strings.backupExport,
            confirm = true,
            strings = strings,
            onDismiss = { exporting = false },
        ) { password ->
            runCatching { actions.export(password) }.fold(
                onSuccess = { data ->
                    exporting = false
                    saver(backupName(), data)
                    null
                },
                onFailure = { actions.describe(it) },
            )
        }
    }

    restoring?.let { data ->
        BackupPasswordDialog(
            icon = RvRestoreIcon,
            title = strings.backupRestore,
            hint = strings.backupRestoreHint,
            action = strings.backupRestore,
            confirm = false,
            strings = strings,
            onDismiss = { restoring = null },
        ) { password ->
            runCatching { actions.restore(data, password) }.fold(
                onSuccess = { result ->
                    restoring = null
                    report(result)
                    null
                },
                onFailure = { actions.describe(it) },
            )
        }
    }

    return RvBackupFlow(
        export = saver?.let { { exporting = true } },
        restore = openBackup,
        importCsv = openCsv,
        busy = busy,
    )
}

@OptIn(ExperimentalTime::class)
private fun backupName(): String = "RenVault-${Clock.System.now().toString().substringBefore('T')}.renvault"

@Composable
private fun BackupPasswordDialog(
    icon: ImageVector,
    title: String,
    hint: String,
    action: String,
    confirm: Boolean,
    strings: RvStrings,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> String?,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val short = confirm && password.isNotEmpty() && password.length < MinBackupPassword
    val mismatch = confirm && again.isNotEmpty() && again != password
    val ready = password.isNotEmpty() && !short && (!confirm || again == password) && !busy

    val submit: () -> Unit = {
        if (ready) {
            scope.launch {
                busy = true
                failure = onSubmit(password)
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(hint)
                RvTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        failure = null
                    },
                    label = strings.backupPassword,
                    enabled = !busy,
                    secret = true,
                    shown = shown,
                    onShownChange = { shown = it },
                    leading = RvKeyIcon,
                    error = failure ?: if (short) strings.backupPasswordShort else null,
                    imeAction = if (confirm) ImeAction.Next else ImeAction.Done,
                    onDone = if (confirm) null else submit,
                )
                if (confirm) {
                    RvTextField(
                        value = again,
                        onValueChange = { again = it },
                        label = strings.backupPasswordConfirm,
                        enabled = !busy,
                        secret = true,
                        shown = shown,
                        onShownChange = { shown = it },
                        leading = RvKeyIcon,
                        error = if (mismatch) strings.backupPasswordMismatch else null,
                        imeAction = ImeAction.Done,
                        onDone = submit,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = submit, enabled = ready, shapes = ButtonDefaults.shapes()) { Text(action) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) }
        },
    )
}
