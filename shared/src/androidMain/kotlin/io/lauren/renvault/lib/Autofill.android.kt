package io.lauren.renvault.lib

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
actual fun rememberAutofillSetup(): RvAutofillSetup? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AutofillManager::class.java) } ?: return null
    if (!manager.isAutofillSupported) return null
    var enabled by remember { mutableStateOf(manager.hasEnabledAutofillServices()) }
    LifecycleResumeEffect(manager) {
        enabled = manager.hasEnabledAutofillServices()
        onPauseOrDispose {}
    }
    return RvAutofillSetup(enabled) { on ->
        if (on) {
            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData("package:${context.packageName}".toUri())
            RvLockGuard.expectLeave()
            runCatching { context.startActivity(intent) }
                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
        } else {
            runCatching { manager.disableAutofillServices() }
            enabled = false
        }
    }
}
