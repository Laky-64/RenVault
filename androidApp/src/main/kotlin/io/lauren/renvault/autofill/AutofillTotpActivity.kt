package io.lauren.renvault.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import io.lauren.renvault.R
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.ui.components.copyToClipboard
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class AutofillTotpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformStorage.attach(applicationContext)
        val form = Form(
            username = intent.parcelable(ExtraUsername),
            password = intent.parcelable(ExtraPassword),
            webDomain = intent.getStringExtra(ExtraDomain),
            packageName = intent.getStringExtra(ExtraPackage).orEmpty(),
        )
        val id = intent.getStringExtra(ExtraEntry).orEmpty()

        lifecycleScope.launch {
            val entry = runCatching { Vault.web().firstOrNull { it.id == id } }.getOrNull()
            if (entry == null) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@launch
            }
            val dataset = datasetOf(this@AutofillTotpActivity, form, entry, deferTotp = false)
            runCatching { Vault.totp(id) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { code ->
                copyToClipboard(this@AutofillTotpActivity, code, sensitive = true)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(this@AutofillTotpActivity, R.string.autofill_totp_copied, Toast.LENGTH_SHORT).show()
                }
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
            finish()
        }
    }
}
