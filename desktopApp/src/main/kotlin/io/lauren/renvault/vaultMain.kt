package io.lauren.renvault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Tray
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.lib.currentLocaleTag
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.app_name
import org.jetbrains.compose.resources.stringResource
import io.lauren.renvault.ui.components.RvLockIcon
import io.lauren.renvault.ui.rememberStrings
import java.awt.SystemTray
import io.lauren.renvault.ui.theme.RenVaultTheme
import java.awt.Desktop
import java.awt.Dimension
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
    val tray = remember { runCatching { SystemTray.isSupported() }.getOrDefault(false) }
    var visible by remember { mutableStateOf(true) }
    val strings = rememberStrings(currentLocaleTag())
    val close: () -> Unit = { if (tray) visible = false else exitApplication() }

    LaunchedEffect(Unit) {
        RvInstance.listen { visible = true }
    }

    if (tray) {
        Tray(
            icon = rememberTrayIcon(),
            tooltip = appName,
            onAction = { visible = true },
            menu = {
                Item(strings.trayOpen, onClick = { visible = true })
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
        LaunchedEffect(visible) {
            if (!visible) return@LaunchedEffect
            state.isMinimized = false
            window.toFront()
            window.requestFocus()
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
    val glyph = rememberVectorPainter(RvLockIcon)
    return remember(glyph) {
        object : Painter() {
            override val intrinsicSize = Size(TrayIconSize, TrayIconSize)

            override fun DrawScope.onDraw() {
                drawCircle(TrayBackground)
                val inset = size.minDimension * 0.22f
                translate(inset, inset) {
                    with(glyph) {
                        draw(
                            size = Size(size.width - inset * 2, size.height - inset * 2),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
            }
        }
    }
}

private const val TrayIconSize = 64f

private val TrayBackground = Color(0xFF3F5BA9)
