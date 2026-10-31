package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.lauren.renvault.bridge.HybridPrompt
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.RvStrings
import kotlinx.coroutines.launch

private const val LocalKeySession = "localkey"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RvLocalKeyPrompt(strings: RvStrings, onChange: (HybridPrompt?) -> Unit = {}) {
    var prompt by remember { mutableStateOf<HybridPrompt?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Vault.events.collect { event ->
            Vault.hybridPrompt(event)?.takeIf { it.session == LocalKeySession }?.let {
                prompt = it
                onChange(it)
            }
            Vault.hybridProgress(event)?.takeIf { it.session == LocalKeySession && it.stage == "resolved" }?.let { done ->
                if (prompt?.id == done.error) {
                    prompt = null
                    onChange(null)
                }
            }
        }
    }

    val current = prompt ?: return
    val answer: (Int) -> Unit = { account ->
        prompt = null
        onChange(null)
        scope.launch { runCatching { Vault.hybridDecide(current.id, account >= 0, account) } }
    }

    AlertDialog(
        onDismissRequest = { answer(-1) },
        icon = {
            RvSiteIcon(
                fallback = current.relyingParty,
                size = 56.dp,
                shape = MaterialShapes.Cookie12Sided.toShape(),
            )
        },
        title = {
            Text(if (current.create) strings.localKeyCreate(current.relyingParty) else strings.localKeyGet(current.relyingParty))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (current.create) strings.localKeyCreateHint else strings.localKeyGetHint)
                if (current.create) {
                    RvSegment(
                        index = 0,
                        count = 1,
                        leading = { RvSiteIcon(fallback = current.username.ifEmpty { current.relyingParty }) },
                        supporting = { Text(current.relyingParty) },
                    ) {
                        Text(current.username.ifEmpty { current.relyingParty })
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        current.accounts.forEachIndexed { index, account ->
                            val name = account.username.ifEmpty { account.title }
                            RvSegment(
                                index = index,
                                count = current.accounts.size,
                                onClick = { answer(index) },
                                leading = { RvSiteIcon(fallback = name) },
                                trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                                supporting = if (account.title.isNotEmpty() && account.title != name) ({ Text(account.title) }) else null,
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (current.create) {
                Button(onClick = { answer(0) }, shapes = ButtonDefaults.shapes()) {
                    Text(strings.localKeySave)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { answer(-1) }, shapes = ButtonDefaults.shapes()) {
                Text(strings.cancel)
            }
        },
    )
}
