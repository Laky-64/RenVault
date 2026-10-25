package io.lauren.renvault.autofill

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.lauren.renvault.R
import io.lauren.renvault.RvUnlockGate
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.bridge.WebEntry
import io.lauren.renvault.ui.components.RvSearchIcon
import io.lauren.renvault.ui.components.RvSegment
import io.lauren.renvault.ui.components.RvTextField
import io.lauren.renvault.ui.theme.RenVaultTheme
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class AutofillActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlatformStorage.attach(applicationContext)

        val form = Form(
            username = intent.parcelable(ExtraUsername),
            password = intent.parcelable(ExtraPassword),
            webDomain = intent.getStringExtra(ExtraDomain),
            packageName = intent.getStringExtra(ExtraPackage).orEmpty(),
            inline = intent.parcelable<Parcelable>(ExtraInline),
        )
        val pick = intent.getBooleanExtra(ExtraPick, false)

        setContent {
            RenVaultTheme {
                var ready by remember { mutableStateOf(false) }
                var unlocked by remember { mutableStateOf(false) }
                var entries by remember { mutableStateOf<List<WebEntry>?>(null) }

                LaunchedEffect(Unit) {
                    runCatching { Vault.init() }
                    ready = true
                }
                LaunchedEffect(unlocked) {
                    if (!unlocked) return@LaunchedEffect
                    val all = runCatching { Vault.web() }.getOrDefault(emptyList()).filter { !it.isDeleted }
                    val found = matchEntries(all, form)
                    if (!pick && found.isNotEmpty()) {
                        finishWith(unlockedResponse(this@AutofillActivity, form, found))
                    } else {
                        entries = all.sortedBy { it.title.ifEmpty { it.domain }.lowercase() }
                    }
                }

                when {
                    !ready -> Box(Modifier.fillMaxSize())
                    !unlocked -> RvUnlockGate(onUnlocked = { unlocked = true })
                    else -> Picker(entries, onPick = { entry -> pickEntry(form, entry) })
                }
            }
        }
    }

    private var finishing = false

    private fun pickEntry(form: Form, entry: WebEntry) {
        if (finishing) return
        finishing = true
        lifecycleScope.launch {
            finishWith(datasetOf(this@AutofillActivity, form, entry))
        }
    }

    private fun finishWith(result: Parcelable) {
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result))
        finish()
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Picker(entries: List<WebEntry>?, onPick: (WebEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.autofill_search)) }) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        if (entries == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
            return@Scaffold
        }
        val shown = entries.filter { entry ->
            query.isBlank() || listOf(entry.title, entry.domain, entry.username).any { it.contains(query.trim(), ignoreCase = true) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                RvTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.autofill_search_hint),
                    leading = RvSearchIcon,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.autofill_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            itemsIndexed(shown, key = { _, entry -> entry.id }) { index, entry ->
                RvSegment(
                    index = index,
                    count = shown.size,
                    onClick = { onPick(entry) },
                    supporting = if (entry.username.isNotEmpty()) ({ Text(entry.username) }) else null,
                ) {
                    Text(entry.title.ifEmpty { entry.domain })
                }
            }
        }
    }
}
