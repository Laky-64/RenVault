package io.lauren.renvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val ExpandedMin = 840.dp

@Composable
fun windowWidth(): Dp {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { size.width.toDp() }
}

@Composable
fun isCompact(): Boolean = windowWidth() < ExpandedMin

object RvLayout {
    val gutter = 16.dp
    val zonesPane = 300.dp
    val listPane = 380.dp
    val zonesPaneMin = 240.dp
    val listPaneMin = 300.dp
    val detailPaneMin = 360.dp
    val readable = 640.dp
}

private val PaneChrome = 64.dp

@Composable
fun isHomeCompact(): Boolean =
    windowWidth() < RvLayout.zonesPaneMin + RvLayout.listPaneMin + RvLayout.detailPaneMin + PaneChrome

@Composable
fun homePaneWidths(): Pair<Dp, Dp> {
    val room = windowWidth() - PaneChrome
    val zones = (room * 0.24f).coerceIn(RvLayout.zonesPaneMin, RvLayout.zonesPane)
    val list = (room * 0.32f).coerceIn(RvLayout.listPaneMin, RvLayout.listPane)
    return zones to list
}
