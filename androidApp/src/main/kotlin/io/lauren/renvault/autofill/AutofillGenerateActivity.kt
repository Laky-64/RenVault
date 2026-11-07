package io.lauren.renvault.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import kotlinx.coroutines.launch

private const val GeneratedLength = 20
private const val GeneratedWords = 4

@RequiresApi(Build.VERSION_CODES.O)
class AutofillGenerateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformStorage.attach(applicationContext)
        val form = Form(
            username = null,
            password = null,
            webDomain = intent.getStringExtra(ExtraDomain),
            packageName = intent.getStringExtra(ExtraPackage).orEmpty(),
            fresh = intent.parcelables(ExtraFresh),
        )
        val rules = intent.getStringExtra(ExtraRules).orEmpty()
        val domain = form.webDomain?.removePrefix("www.").orEmpty()

        lifecycleScope.launch {
            runCatching { Vault.init() }
            val password = runCatching {
                Vault.generatePassword(domain, "apple", GeneratedLength, true, true, GeneratedWords, rules).password
            }.getOrNull()
            if (password.isNullOrEmpty() || form.fresh.isEmpty()) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@launch
            }
            runCatching { Vault.recordGenerated(password, domain) }
            val dataset = generatedDataset(this@AutofillGenerateActivity, form, password)
            setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
            finish()
        }
    }
}
