package io.lauren.renvault

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

internal object RvInstance {
    private val folder = File(System.getProperty("java.io.tmpdir"), "renvault-" + System.getProperty("user.name"))
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    @Volatile
    private var onShow: (token: String) -> Unit = {}

    fun claim(): Boolean {
        folder.mkdirs()
        val channel = FileChannel.open(
            File(folder, "instance.lock").toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        val held = runCatching { channel.tryLock() }.getOrNull()
        if (held == null) {
            channel.close()
            wakeRunning()
            return false
        }
        lock = held
        val socket = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        server = socket
        File(folder, "instance.port").writeText(socket.localPort.toString())
        thread(isDaemon = true, name = "renvault-instance") {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use {
                    val line = it.getInputStream().bufferedReader().readLine().orEmpty()
                    if (line == ShowCommand || line.startsWith("$ShowCommand ")) onShow(line.removePrefix(ShowCommand).trim())
                }
            }
        }
        return true
    }

    fun listen(action: (token: String) -> Unit) {
        onShow = action
    }

    private fun wakeRunning() {
        val port = runCatching { File(folder, "instance.port").readText().trim().toInt() }.getOrNull() ?: return
        runCatching {
            val token = System.getenv("XDG_ACTIVATION_TOKEN") ?: System.getenv("DESKTOP_STARTUP_ID") ?: ""
            Socket(InetAddress.getLoopbackAddress(), port).use { it.getOutputStream().write("$ShowCommand $token\n".toByteArray()) }
        }
    }
}

private const val ShowCommand = "show"
