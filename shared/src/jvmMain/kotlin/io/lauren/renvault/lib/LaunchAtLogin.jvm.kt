package io.lauren.renvault.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.lauren.renvault.RvAppId
import io.lauren.renvault.RvAppName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

const val RvBackgroundArg = "--background"

private val os = System.getProperty("os.name").orEmpty().lowercase()

private val launcher: String? = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }

private const val RunKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

private val home = File(System.getProperty("user.home"))

private val agent = File(home, "Library/LaunchAgents/$RvAppId.plist")

private val entry = File(
    System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config"),
    "autostart/$RvAppId.desktop",
)

private fun reg(vararg args: String): Boolean {
    val process = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).start()
    process.inputStream.readAllBytes()
    return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
}

private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun registered(): Boolean = when {
    os.startsWith("windows") -> reg("query", RunKey, "/v", RvAppName)
    os.startsWith("mac") -> agent.isFile
    else -> entry.isFile
}

private fun register(path: String, on: Boolean) {
    when {
        os.startsWith("windows") -> {
            if (on) {
                reg("add", RunKey, "/v", RvAppName, "/t", "REG_SZ", "/d", "\"\\\"$path\\\" $RvBackgroundArg\"", "/f")
            } else {
                reg("delete", RunKey, "/v", RvAppName, "/f")
            }
        }
        os.startsWith("mac") -> {
            if (!on) {
                agent.delete()
                return
            }
            agent.parentFile.mkdirs()
            agent.writeText(
                """
                |<?xml version="1.0" encoding="UTF-8"?>
                |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                |<plist version="1.0">
                |<dict>
                |    <key>Label</key>
                |    <string>$RvAppId</string>
                |    <key>ProgramArguments</key>
                |    <array>
                |        <string>${xml(path)}</string>
                |        <string>$RvBackgroundArg</string>
                |    </array>
                |    <key>RunAtLoad</key>
                |    <true/>
                |    <key>ProcessType</key>
                |    <string>Interactive</string>
                |</dict>
                |</plist>
                |
                """.trimMargin(),
            )
        }
        else -> {
            if (!on) {
                entry.delete()
                return
            }
            entry.parentFile.mkdirs()
            entry.writeText(
                """
                |[Desktop Entry]
                |Type=Application
                |Name=$RvAppName
                |Exec="$path" $RvBackgroundArg
                |Icon=$RvAppId
                |Terminal=false
                |X-GNOME-Autostart-enabled=true
                |
                """.trimMargin(),
            )
        }
    }
}

@Composable
actual fun rememberLaunchAtLogin(): RvLaunchAtLogin? {
    val path = launcher ?: return null
    var enabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(path) {
        enabled = withContext(Dispatchers.IO) {
            runCatching { registered().also { on -> if (on) register(path, true) } }.getOrDefault(false)
        }
    }
    return RvLaunchAtLogin(enabled) { on ->
        enabled = on
        scope.launch {
            enabled = withContext(Dispatchers.IO) {
                runCatching { register(path, on) }
                runCatching { registered() }.getOrDefault(false)
            }
        }
    }
}
