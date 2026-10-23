package io.lauren.renvault.lib

import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import io.lauren.renvault.bridge.PlatformStorage
import java.net.Inet4Address
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal actual object RvLan {
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val resolver by lazy { Executors.newSingleThreadExecutor() }

    private val nsd: NsdManager?
        get() = PlatformStorage.context?.getSystemService(NsdManager::class.java)

    actual fun addresses(): List<String> {
        val manager = PlatformStorage.context?.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = manager.activeNetwork ?: return emptyList()
        val link = manager.getLinkProperties(network) ?: return emptyList()
        return link.linkAddresses
            .map { it.address }
            .filter { it is Inet4Address && !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
    }

    actual fun deviceName(): String {
        val resolver = PlatformStorage.context?.contentResolver
        val named = resolver?.let { Settings.Global.getString(it, DeviceNameKey) }
        return named?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }

    @Synchronized
    actual fun start(node: String, port: Int, onSeen: (node: String, host: String, port: Int) -> Unit) {
        stop()
        val manager = nsd ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "RenVault-" + node.take(8)
            serviceType = LanService
            this.port = port
            setAttribute(LanNodeKey, node)
        }
        val announced = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        val browsing = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) = resolve(manager, service, onSeen)
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, announced) }
            .onSuccess { registration = announced }
        runCatching { manager.discoverServices(LanService, NsdManager.PROTOCOL_DNS_SD, browsing) }
            .onSuccess { discovery = browsing }
    }

    @Synchronized
    actual fun stop() {
        val manager = nsd
        registration?.let { listener -> runCatching { manager?.unregisterService(listener) } }
        discovery?.let { listener -> runCatching { manager?.stopServiceDiscovery(listener) } }
        registration = null
        discovery = null
    }

    actual fun schedule(enabled: Boolean) {
        val context = PlatformStorage.context ?: return
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(SyncWorkName)
            return
        }
        val request = PeriodicWorkRequestBuilder<RvSyncWorker>(SyncEveryMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(SyncWorkName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun resolve(manager: NsdManager, service: NsdServiceInfo, onSeen: (String, String, Int) -> Unit) {
        val report: (NsdServiceInfo) -> Unit = { found ->
            val node = found.attributes[LanNodeKey]?.decodeToString().orEmpty()
            val host = hostOf(found)
            if (node.isNotEmpty() && host != null) onSeen(node, host, found.port)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(code: Int) = Unit
                override fun onServiceUpdated(found: NsdServiceInfo) {
                    report(found)
                    runCatching { manager.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            runCatching { manager.registerServiceInfoCallback(service, resolver, callback) }
            return
        }
        @Suppress("DEPRECATION")
        runCatching {
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                override fun onServiceResolved(info: NsdServiceInfo) = report(info)
            })
        }
    }

    private fun hostOf(found: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return found.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
        }
        @Suppress("DEPRECATION")
        return found.host?.hostAddress
    }
}

private const val DeviceNameKey = "device_name"

private const val SyncWorkName = "renvault-sync"

private const val SyncEveryMinutes = 15L
