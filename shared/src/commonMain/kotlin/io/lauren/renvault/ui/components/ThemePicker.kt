package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.ui.LocalStrings
import io.lauren.renvault.ui.theme.LocalThemeController
import io.lauren.renvault.ui.theme.RvThemeMode

val RvThemeMode.icon: ImageVector
    get() = when (this) {
        RvThemeMode.System -> RvThemeAutoIcon
        RvThemeMode.Light -> RvThemeLightIcon
        RvThemeMode.Dark -> RvThemeDarkIcon
    }

fun RvThemeMode.label(strings: RvStrings): String = when (this) {
    RvThemeMode.System -> strings.themeSystem
    RvThemeMode.Light -> strings.themeLight
    RvThemeMode.Dark -> strings.themeDark
}

@Composable
fun RvThemeButtons(modifier: Modifier = Modifier) {
    val theme = LocalThemeController.current
    val strings = LocalStrings.current
    val modes = RvThemeMode.entries

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, mode ->
            var center by remember { mutableStateOf(Offset.Zero) }
            ToggleButton(
                checked = theme.mode == mode,
                onCheckedChange = { theme.choose(mode, center) },
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { center = it.boundsInRoot().center },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(mode.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(
                    text = mode.label(strings),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
}

@Composable
fun RvThemeMenuButton(modifier: Modifier = Modifier) {
    val theme = LocalThemeController.current
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier) {
        FilledTonalIconButton(
            onClick = { open = true },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.onGloballyPositioned { center = it.boundsInRoot().center },
        ) {
            Icon(theme.mode.icon, contentDescription = strings.themeChoose)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.large,
        ) {
            RvThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label(strings)) },
                    onClick = {
                        theme.choose(mode, center)
                        open = false
                    },
                    leadingIcon = { Icon(mode.icon, contentDescription = null) },
                    trailingIcon = if (theme.mode == mode) {
                        { Icon(RvCheckIcon, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
