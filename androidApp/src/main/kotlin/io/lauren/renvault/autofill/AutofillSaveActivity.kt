package io.lauren.renvault.autofill

import io.lauren.renvault.RvAppId
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.lauren.renvault.R
import io.lauren.renvault.RvUnlockGate
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.appLabel
import io.lauren.renvault.ui.theme.RenVaultTheme
import kotlinx.coroutines.launch

internal const val ExtraSaveUsername = "$RvAppId.autofill.SAVE_USERNAME"
internal const val ExtraSavePassword = "$RvAppId.autofill.SAVE_PASSWORD"

@RequiresApi(Build.VERSION_CODES.O)
class AutofillSaveActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlatformStorage.attach(applicationContext)
        val login = SavedLogin(
            username = intent.getStringExtra(ExtraSaveUsername).orEmpty(),
            password = intent.getStringExtra(ExtraSavePassword).orEmpty(),
            webDomain = intent.getStringExtra(ExtraDomain),
            packageName = intent.getStringExtra(ExtraPackage).orEmpty(),
        )
        if (login.password.isEmpty()) {
            finish()
            return
        }

        setContent {
            RenVaultTheme {
                var ready by remember { mutableStateOf(false) }
                var unlocked by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    runCatching { Vault.init() }
                    val state = runCatching { Vault.state() }.getOrNull()
                    if (state == null || !state.configured) {
                        finish()
                        return@LaunchedEffect
                    }
                    unlocked = state.unlocked
                    ready = true
                }
                LaunchedEffect(unlocked) {
                    if (unlocked) save(login)
                }
                when {
                    !ready || unlocked -> Box(Modifier.fillMaxSize())
                    else -> RvUnlockGate(onUnlocked = { unlocked = true })
                }
            }
        }
    }

    private var saving = false

    private fun save(login: SavedLogin) {
        if (saving) return
        saving = true
        lifecycleScope.launch {
            val app = if (login.webDomain == null) login.packageName else ""
            val website = login.webDomain ?: verifiedSite(this@AutofillSaveActivity, login.packageName).orEmpty()
            val title = if (app.isNotEmpty()) appLabel(app) ?: app else website
            val result = runCatching { Vault.saveLogin(title, website, app, login.username, login.password) }.getOrNull()
            val message = when {
                result == null -> R.string.autofill_save_failed
                result.updated -> R.string.autofill_updated
                result.added -> R.string.autofill_saved
                else -> null
            }
            message?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show() }
            finish()
        }
    }
}
