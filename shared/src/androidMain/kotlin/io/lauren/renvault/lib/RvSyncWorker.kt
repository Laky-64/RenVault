package io.lauren.renvault.lib

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

internal class RvSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PlatformStorage.attach(applicationContext)
        withTimeoutOrNull(WorkTimeout) {
            runCatching {
                Vault.init()
                Vault.setAddresses(RvLan.addresses())
                Vault.syncPeers()
            }
        }
        return Result.success()
    }
}

private val WorkTimeout = 30.seconds
