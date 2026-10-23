package io.lauren.renvault.lib

internal expect object RvLan {
    fun addresses(): List<String>
    fun deviceName(): String
    fun start(node: String, port: Int, onSeen: (node: String, host: String, port: Int) -> Unit)
    fun stop()
    fun schedule(enabled: Boolean)
}

internal const val LanService = "_renvault._tcp"
internal const val LanNodeKey = "n"
