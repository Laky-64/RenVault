package io.lauren.renvault.lib

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlin.concurrent.thread

internal actual object RvLan {
    private val running = mutableListOf<JmDNS>()
    private var generation = 0

    actual fun addresses(): List<String> = interfaces().mapNotNull { it.hostAddress }

    actual fun deviceName(): String =
        System.getenv("COMPUTERNAME")
            ?: System.getenv("HOSTNAME")
            ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
            ?: "RenVault"

    @Synchronized
    actual fun start(node: String, port: Int, onSeen: (node: String, host: String, port: Int) -> Unit) {
        stop()
        val mine = generation
        val type = "$LanService.local."
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                event.dns.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceRemoved(event: ServiceEvent) = Unit

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info
                val seen = info.getPropertyString(LanNodeKey).orEmpty()
                val host = info.inet4Addresses.firstOrNull()?.hostAddress
                if (seen.isNotEmpty() && host != null) onSeen(seen, host, info.port)
            }
        }
        interfaces().forEach { address ->
            thread(isDaemon = true, name = "renvault-mdns") {
                val dns = runCatching { JmDNS.create(address) }.getOrNull() ?: return@thread
                val keep = synchronized(this) {
                    if (generation == mine) running += dns
                    generation == mine
                }
                if (!keep) {
                    runCatching { dns.close() }
                    return@thread
                }
                runCatching {
                    val info = ServiceInfo.create(type, "RenVault-" + node.take(8), port, 0, 0, mapOf(LanNodeKey to node))
                    dns.registerService(info)
                    dns.addServiceListener(type, listener)
                }
            }
        }
    }

    @Synchronized
    actual fun stop() {
        generation++
        val closing = running.toList()
        running.clear()
        if (closing.isEmpty()) return
        thread(isDaemon = true, name = "renvault-mdns-stop") {
            closing.forEach { dns ->
                runCatching { dns.unregisterAllServices() }
                runCatching { dns.close() }
            }
        }
    }

    actual fun schedule(enabled: Boolean) = Unit

    private fun interfaces(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && it.isSiteLocalAddress }
    }.getOrDefault(emptyList())
}
