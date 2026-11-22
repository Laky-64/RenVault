package io.lauren.renvault.lib

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import io.lauren.renvault.RvAppVersion
import io.lauren.renvault.bridge.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private const val InstallAction = "io.lauren.renvault.UPDATE_INSTALLED"

private class AndroidUpdater(private val context: Context) : RvUpdater {
    override val current: String = RvAppVersion

    override val platform: String = "Android.apk"

    override val directory: String = File(context.cacheDir, "updates").absolutePath

    override suspend fun install(path: String): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = withContext(Dispatchers.IO) {
            val session = installer.createSession(params)
            installer.openSession(session).use { open ->
                File(path).inputStream().use { input ->
                    open.openWrite("base.apk", 0, File(path).length()).use { output ->
                        input.copyTo(output)
                        open.fsync(output)
                    }
                }
            }
            session
        }
        return suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(received: Context, intent: Intent) {
                    when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            RvLockGuard.expectLeave()
                            runCatching { context.startActivity(confirm) }
                        }
                        PackageInstaller.STATUS_SUCCESS -> finish(true)
                        else -> finish(false)
                    }
                }

                fun finish(ok: Boolean) {
                    runCatching { context.unregisterReceiver(this) }
                    if (continuation.isActive) continuation.resume(ok)
                }
            }
            val filter = IntentFilter(InstallAction)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, id, Intent(InstallAction).setPackage(context.packageName), flags)
            installer.openSession(id).use { it.commit(pending.intentSender) }
        }
    }
}

actual fun platformUpdater(): RvUpdater? = PlatformStorage.context?.let(::AndroidUpdater)
