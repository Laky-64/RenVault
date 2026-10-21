package io.lauren.renvault.ui.components

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private const val DoubleTapMs = 300L

@Composable
fun RvTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    maximised: Boolean = true,
    onMinimise: () -> Unit = {},
    onToggleMaximise: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(onToggleMaximise) {
                    awaitPointerEventScope {
                        var last = 0L
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val stamp = down.uptimeMillis
                            if (stamp - last <= DoubleTapMs) {
                                last = 0L
                                onToggleMaximise()
                            } else {
                                last = stamp
                            }
                        }
                    }
                }
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RvShapeBadge(
                icon = RvLockIcon,
                tone = io.lauren.renvault.ui.theme.RvTone(colors.primaryContainer, colors.onPrimaryContainer),
                polygon = MaterialShapes.Cookie9Sided,
                size = 24.dp,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }

        Row(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowButton(RvMinimiseIcon, onMinimise)
            WindowButton(if (maximised) RvWindowRestoreIcon else RvMaximiseIcon, onToggleMaximise)
            WindowButton(RvCloseIcon, onClose, danger = true)
        }
    }
}

@Composable
private fun WindowButton(icon: ImageVector, onClick: () -> Unit, danger: Boolean = false) {
    val colors = MaterialTheme.colorScheme

    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(36.dp),
        colors = if (danger) {
            IconButtonDefaults.iconButtonColors(contentColor = colors.error)
        } else {
            IconButtonDefaults.iconButtonColors()
        },
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
