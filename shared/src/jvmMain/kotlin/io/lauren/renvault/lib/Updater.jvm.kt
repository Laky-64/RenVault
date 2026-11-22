package io.lauren.renvault.lib

import io.lauren.renvault.RvAppName
import io.lauren.renvault.RvAppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private val os = System.getProperty("os.name").orEmpty().lowercase()

private val machineArm = System.getProperty("os.arch").orEmpty().lowercase().let { it == "aarch64" || it == "arm64" }

private val launcher: File? = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile }

private const val InstallMinutes = 30L

private const val TestVersion = "renvault.update.version"

private const val TestPlatform = "renvault.update.platform"

private enum class Format(val ext: String) { Msi("msi"), Dmg("dmg"), Rpm("rpm"), Deb("deb") }

private fun succeeds(vararg command: String, minutes: Long = 1): Boolean = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    process.inputStream.readAllBytes()
    process.waitFor(minutes, TimeUnit.MINUTES) && process.exitValue() == 0
}.getOrDefault(false)

private fun available(command: String): Boolean = succeeds("sh", "-c", "command -v $command")

private fun linuxFormat(path: File): Format? = when {
    available("rpm") && succeeds("rpm", "-qf", path.absolutePath) -> Format.Rpm
    available("dpkg") && succeeds("dpkg", "-S", path.absolutePath) -> Format.Deb
    else -> null
}

private fun quoteShell(text: String) = "'" + text.replace("'", "'\\''") + "'"

private fun quotePowerShell(text: String) = "'" + text.replace("'", "''") + "'"

private fun detach(vararg command: String) {
    ProcessBuilder(*command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
}

private class DesktopUpdater(private val app: File?, private val format: Format, private val arm: Boolean) : RvUpdater {
    override val current: String = System.getProperty(TestVersion)?.takeIf { it.isNotBlank() } ?: RvAppVersion

    override val platform: String = when (format) {
        Format.Msi -> if (arm) "Windows-ARM64" else "Windows-x64"
        Format.Dmg -> if (arm) "macOS-Apple-Silicon" else "macOS-Intel"
        Format.Rpm, Format.Deb -> if (arm) "Linux-ARM64" else "Linux-x64"
    } + "." + format.ext

    override val directory: String = when (format) {
        Format.Msi -> File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "$RvAppName\\updates")
        Format.Dmg -> File(System.getProperty("user.home"), "Library/Caches/$RvAppName/updates")
        else -> File(
            System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + "/.cache"),
            "$RvAppName/updates",
        )
    }.absolutePath

    private val pid = ProcessHandle.current().pid()

    override suspend fun install(path: String): Boolean = withContext(Dispatchers.IO) {
        when (format) {
            Format.Rpm, Format.Deb -> {
                val installed = if (format == Format.Rpm) {
                    if (available("dnf")) {
                        succeeds("pkexec", "dnf", "install", "-y", path, minutes = InstallMinutes)
                    } else {
                        succeeds("pkexec", "rpm", "-U", "--replacepkgs", path, minutes = InstallMinutes)
                    }
                } else {
                    succeeds("pkexec", "apt-get", "install", "-y", path, minutes = InstallMinutes)
                }
                if (!installed) return@withContext false
                if (app != null) detach("setsid", "sh", "-c", "while kill -0 $pid 2>/dev/null; do sleep 0.2; done; exec ${quoteShell(app.absolutePath)}")
            }
            Format.Msi -> {
                val relaunch = app?.let { "; Start-Process -FilePath ${quotePowerShell(it.absolutePath)}" }.orEmpty()
                val script = "Wait-Process -Id $pid -ErrorAction SilentlyContinue; " +
                    "Start-Process -FilePath msiexec.exe -ArgumentList @('/i', ${quotePowerShell("\"$path\"")}, '/passive', '/norestart') -Wait" +
                    relaunch
                detach("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script)
            }
            Format.Dmg -> {
                val bundle = app?.parentFile?.parentFile?.parentFile?.absolutePath ?: "/Applications/$RvAppName.app"
                val mount = File(System.getProperty("java.io.tmpdir"), "$RvAppName-update-$pid").absolutePath
                val script = listOf(
                    "while kill -0 $pid 2>/dev/null; do sleep 0.2; done",
                    "mount=${quoteShell(mount)}",
                    "target=${quoteShell(bundle)}",
                    "mkdir -p \"\$mount\"",
                    "hdiutil attach -nobrowse -readonly -mountpoint \"\$mount\" ${quoteShell(path)} || exit 1",
                    "source=\$(ls -d \"\$mount\"/*.app | head -1)",
                    "if [ -w \"\$(dirname \"\$target\")\" ] && [ -w \"\$target\" ]; then",
                    "  rm -rf \"\$target\" && ditto \"\$source\" \"\$target\"",
                    "else",
                    "  osascript -e \"do shell script \\\"rm -rf '\$target' && ditto '\$source' '\$target'\\\" with administrator privileges\"",
                    "fi",
                    "hdiutil detach \"\$mount\" -quiet",
                    "xattr -dr com.apple.quarantine \"\$target\" 2>/dev/null",
                    "open \"\$target\"",
                ).joinToString("\n")
                detach("/bin/sh", "-c", "nohup /bin/sh -c ${quoteShell(script)} >/dev/null 2>&1 &")
            }
        }
        exitProcess(0)
    }
}

actual fun platformUpdater(): RvUpdater? {
    val test = System.getProperty(TestPlatform)?.takeIf { it.isNotBlank() }
    if (test != null) {
        val (name, ext) = test.substringBeforeLast('.') to test.substringAfterLast('.')
        val format = Format.entries.firstOrNull { it.ext == ext } ?: return null
        return DesktopUpdater(launcher, format, name.endsWith("ARM64") || name.endsWith("Apple-Silicon"))
    }
    val app = launcher ?: return null
    val format = when {
        os.startsWith("windows") -> Format.Msi
        os.startsWith("mac") -> Format.Dmg
        else -> linuxFormat(app)
    } ?: return null
    return DesktopUpdater(app, format, machineArm)
}
