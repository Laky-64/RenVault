package io.lauren.renvault

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Tray
import io.lauren.renvault.bridge.EventKind
import io.lauren.renvault.bridge.TrayItem
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.currentLocaleTag
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.app_name
import org.jetbrains.compose.resources.stringResource
import io.lauren.renvault.ui.rememberStrings
import java.awt.SystemTray
import io.lauren.renvault.ui.theme.RenVaultTheme
import java.awt.Desktop
import java.awt.Dimension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URI
import kotlin.math.roundToInt

private val MinimumSize = DpSize(350.dp, 690.dp)

private val StartSize = DpSize(1000.dp, 670.dp)

fun main() {
    if (!RvInstance.claim()) return
    vaultApplication()
}

private fun vaultApplication() = application {
    val appName = stringResource(Res.string.app_name)
    val state = rememberWindowState(size = StartSize)
    val mac = remember { System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true) }
    val awtTray = remember { runCatching { SystemTray.isSupported() }.getOrDefault(false) }
    var nativeTray by remember { mutableStateOf<Boolean?>(if (mac) false else null) }
    val tray = nativeTray == true || awtTray
    var visible by remember { mutableStateOf(true) }
    var raises by remember { mutableIntStateOf(0) }
    var activation by remember { mutableStateOf("") }
    val open: (token: String) -> Unit = { token ->
        visible = true
        activation = token
        raises++
    }
    val strings = rememberStrings(currentLocaleTag())
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { if (tray) visible = false else exitApplication() }
    val quit: () -> Unit = {
        scope.launch {
            if (nativeTray == true) runCatching { Vault.hideTray() }
            exitApplication()
        }
    }
    val trayIcon = rememberTrayIcon()

    LaunchedEffect(Unit) {
        RvInstance.listen(open)
    }

    if (!mac) {
        LaunchedEffect(appName, strings.trayOpen, strings.trayQuit) {
            val items = listOf(TrayItem(TrayOpen, strings.trayOpen), TrayItem(TrayQuit, strings.trayQuit))
            val shown = try {
                Vault.showTray(appName, trayIcons("light"), trayIcons("dark"), items)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            if (nativeTray != true) nativeTray = shown
        }
        LaunchedEffect(Unit) {
            Vault.events.collect { event ->
                if (event.kind != EventKind.TRAY) return@collect
                when (Vault.trayItem(event)) {
                    TrayQuit -> quit()
                    else -> open("")
                }
            }
        }
    }

    if (awtTray && nativeTray == false) {
        Tray(
            icon = trayIcon,
            tooltip = appName,
            onAction = { open("") },
            menu = {
                Item(strings.trayOpen, onClick = { open("") })
                Item(strings.trayQuit, onClick = ::exitApplication)
            },
        )
    }

    Window(
        onCloseRequest = close,
        state = state,
        visible = visible,
        title = appName,
    ) {
        LaunchedEffect(visible, raises) {
            if (!visible) return@LaunchedEffect
            state.isMinimized = false
            window.toFront()
            window.requestFocus()
            val token = activation
            activation = ""
            runCatching { Vault.raiseWindow(token) }
        }

        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(
                MinimumSize.width.value.roundToInt(),
                MinimumSize.height.value.roundToInt(),
            )
        }

        RenVaultTheme {
            val surface = MaterialTheme.colorScheme.surface
            if (!mac) {
                val onSurface = MaterialTheme.colorScheme.onSurface
                LaunchedEffect(surface, onSurface) {
                    window.background = java.awt.Color(surface.toArgb())
                    runCatching {
                        Vault.setTitleBar(
                            dark = surface.luminance() < 0.5f,
                            caption = surface.toArgb() and 0xFFFFFF,
                            text = onSurface.toArgb() and 0xFFFFFF,
                        )
                    }
                }
            } else {
                SideEffect {
                    window.rootPane.apply {
                        putClientProperty("apple.awt.transparentTitleBar", true)
                        putClientProperty("apple.awt.windowTitleVisible", false)
                        putClientProperty(
                            "apple.awt.windowAppearance",
                            if (surface.luminance() < 0.5f) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
                        )
                    }
                    window.background = java.awt.Color(surface.toArgb())
                }
            }

            RvVaultApp(
                title = appName,
                onOpenUrl = { target ->
                    runCatching { Desktop.getDesktop().browse(URI(target)) }
                },
            )
        }
    }
}

@Composable
private fun rememberTrayIcon(): Painter {
    val variant = if (isSystemInDarkTheme()) "light" else "dark"
    return remember(variant) {
        BitmapPainter(Image.makeFromEncoded(trayIcons(variant).last()).toComposeImageBitmap())
    }
}

private fun trayIcons(variant: String): List<ByteArray> = TraySizes.map { size ->
    val stream = RvInstance::class.java.getResourceAsStream("/tray/vault-$variant-$size.png") ?: error("tray icon $variant $size missing")
    stream.use { it.readBytes() }
}

private const val TrayOpen = "open"

private const val TrayQuit = "quit"

private val TraySizes = listOf(16, 20, 24, 32, 48, 64)
