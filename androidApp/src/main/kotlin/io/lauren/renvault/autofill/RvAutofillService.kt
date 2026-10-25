package io.lauren.renvault.autofill

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.service.autofill.InlinePresentation
import androidx.annotation.RequiresApi
import io.lauren.renvault.lib.autofillInline
import io.lauren.renvault.R
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class RvAutofillService : AutofillService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onFillRequest(request: FillRequest, cancellation: CancellationSignal, callback: FillCallback) {
        val parsed = parseForm(request.fillContexts.last().structure)
        val inline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && autofillInline()) request.inlineSuggestionsRequest else null
        val form = Form(parsed.username, parsed.password, parsed.webDomain, parsed.packageName, inline)
        if (form.packageName == packageName || form.password == null && form.username == null) {
            callback.onSuccess(null)
            return
        }
        val job = scope.launch {
            callback.onSuccess(runCatching { respond(form) }.getOrNull())
        }
        cancellation.setOnCancelListener { job.cancel() }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun respond(form: Form): FillResponse? {
        PlatformStorage.attach(applicationContext)
        runCatching { Vault.init() }
        val state = Vault.state()
        if (!state.configured) return null
        if (!state.unlocked) {
            val title = getString(R.string.autofill_unlock)
            val chip = inlineOf(this, form, 0, title)
            val builder = FillResponse.Builder()
            if (chip != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAuthentication(
                    form.ids.toTypedArray(),
                    authSender(this, form, pick = false),
                    presentation(this, title),
                    chip as InlinePresentation,
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setAuthentication(form.ids.toTypedArray(), authSender(this, form, pick = false), presentation(this, title))
            }
            return builder.build()
        }
        return unlockedResponse(this, form, matchEntries(Vault.web(), form))
    }
}
