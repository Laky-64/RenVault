package io.lauren.renvault

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Tray
import com.jetbrains.JBR
import io.lauren.renvault.lib.currentLocaleTag
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.app_name
import org.jetbrains.compose.resources.stringResource
import io.lauren.renvault.ui.components.RvLockIcon
import io.lauren.renvault.ui.rememberStrings
import java.awt.SystemTray
import io.lauren.renvault.ui.components.RvTitleBar
import io.lauren.renvault.ui.theme.RenVaultTheme
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.MouseEvent
import java.net.URI
import kotlin.math.max
import kotlin.math.roundToInt
import java.awt.Window as AwtWindow

private val MinimumSize = DpSize(350.dp, 690.dp)

private val StartSize = DpSize(1000.dp, 670.dp)

private val EdgeGrip = 6.dp

fun main() {
    if (!RvInstance.claim()) return
    vaultApplication()
}

private fun vaultApplication() = application {
    val appName = stringResource(Res.string.app_name)
    val state = rememberWindowState(size = StartSize)
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
        undecorated = true,
    ) {
        LaunchedEffect(visible) {
            if (!visible) return@LaunchedEffect
            state.isMinimized = false
            window.toFront()
            window.requestFocus()
        }

        var maximised by remember { mutableStateOf(state.placement == WindowPlacement.Maximized) }
        maximised = state.placement == WindowPlacement.Maximized

        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(
                MinimumSize.width.value.roundToInt(),
                MinimumSize.height.value.roundToInt(),
            )
        }

        RenVaultTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                RvVaultApp(
                    title = appName,
                    titleBar = {
                        TitleDrag(
                            maximised = maximised,
                            onRestore = { state.placement = WindowPlacement.Floating },
                        ) {
                            RvTitleBar(
                                title = appName,
                                maximised = maximised,
                                onMinimise = { state.isMinimized = true },
                                onToggleMaximise = {
                                    state.placement = if (maximised) {
                                        WindowPlacement.Floating
                                    } else {
                                        WindowPlacement.Maximized
                                    }
                                },
                                onClose = close,
                            )
                        }
                    },
                    onOpenUrl = { target ->
                        runCatching { Desktop.getDesktop().browse(URI(target)) }
                    },
                )

                if (!maximised) ResizeFrame()
            }
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

@Composable
private fun FrameWindowScope.TitleDrag(
    maximised: Boolean,
    onRestore: () -> Unit,
    content: @Composable () -> Unit,
) {
    val full by rememberUpdatedState(maximised)
    val restore by rememberUpdatedState(onRestore)

    Box(
        modifier = Modifier.pointerInput(Unit) {
            var handled = false
            var ratio = 0f
            var wide = 0
            var grabX = -1
            var grabY = 0

            detectDragGestures(
                onDragStart = { start ->
                    handled = window.handOverMove()
                    if (handled) return@detectDragGestures

                    grabY = start.y.roundToInt()
                    if (full) {
                        ratio = (start.x / size.width).coerceIn(0f, 1f)
                        wide = window.width
                        grabX = -1
                        restore()
                    } else {
                        grabX = start.x.roundToInt()
                    }
                },
                onDrag = { change, _ ->
                    if (handled) return@detectDragGestures
                    change.consume()

                    val pointer = pointerLocation() ?: return@detectDragGestures

                    if (grabX < 0) {
                        if (window.width == wide) return@detectDragGestures
                        grabX = (window.width * ratio).roundToInt()
                    }

                    window.setLocation(pointer.x - grabX, pointer.y - grabY)
                },
            )
        },
    ) {
        content()
    }
}

private fun AwtWindow.handOverMove(): Boolean {
    if (!JBR.isAvailable() || !JBR.isWindowMoveSupported()) return false
    return runCatching {
        JBR.getWindowMove().startMovingTogetherWithMouse(this, MouseEvent.BUTTON1)
    }.isSuccess
}

private fun pointerLocation(): Point? =
    runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()

@Composable
private fun FrameWindowScope.ResizeFrame() {
    Box(modifier = Modifier.fillMaxSize()) {
        Edge(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(EdgeGrip),
            cursor = Cursor.N_RESIZE_CURSOR,
            top = true,
        )
        Edge(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(EdgeGrip),
            cursor = Cursor.S_RESIZE_CURSOR,
            bottom = true,
        )
        Edge(
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(EdgeGrip),
            cursor = Cursor.W_RESIZE_CURSOR,
            left = true,
        )
        Edge(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(EdgeGrip),
            cursor = Cursor.E_RESIZE_CURSOR,
            right = true,
        )

        Edge(
            modifier = Modifier.align(Alignment.TopStart).size(EdgeGrip * 2),
            cursor = Cursor.NW_RESIZE_CURSOR,
            top = true,
            left = true,
        )
        Edge(
            modifier = Modifier.align(Alignment.TopEnd).size(EdgeGrip * 2),
            cursor = Cursor.NE_RESIZE_CURSOR,
            top = true,
            right = true,
        )
        Edge(
            modifier = Modifier.align(Alignment.BottomStart).size(EdgeGrip * 2),
            cursor = Cursor.SW_RESIZE_CURSOR,
            bottom = true,
            left = true,
        )
        Edge(
            modifier = Modifier.align(Alignment.BottomEnd).size(EdgeGrip * 2),
            cursor = Cursor.SE_RESIZE_CURSOR,
            bottom = true,
            right = true,
        )
    }
}

@Composable
private fun FrameWindowScope.Edge(
    modifier: Modifier,
    cursor: Int,
    top: Boolean = false,
    bottom: Boolean = false,
    left: Boolean = false,
    right: Boolean = false,
) {
    Box(
        modifier = modifier
            .pointerHoverIcon(PointerIcon(Cursor(cursor)))
            .pointerInput(top, bottom, left, right) {
                detectDragGestures { change, drag ->
                    change.consume()

                    val dx = drag.x.roundToInt()
                    val dy = drag.y.roundToInt()

                    val floor = window.minimumSize
                    var x = window.x
                    var y = window.y
                    var width = window.width
                    var height = window.height

                    if (left) {
                        val next = max(floor.width, width - dx)
                        x += width - next
                        width = next
                    }
                    if (right) width = max(floor.width, width + dx)

                    if (top) {
                        val next = max(floor.height, height - dy)
                        y += height - next
                        height = next
                    }
                    if (bottom) height = max(floor.height, height + dy)

                    window.setBounds(x, y, width, height)
                }
            },
    )
}
