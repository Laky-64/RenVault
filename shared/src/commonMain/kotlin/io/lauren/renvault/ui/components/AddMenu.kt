package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.lauren.renvault.lib.RvStrings

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RvAddMenu(
    strings: RvStrings,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    extended: Boolean,
    onAdd: () -> Unit,
) {
    val hybrid = rememberHybridLauncher()
    if (hybrid == null) {
        ExtendedFloatingActionButton(
            text = { Text(strings.listAdd) },
            icon = { Icon(RvAddIcon, contentDescription = null) },
            onClick = onAdd,
            expanded = extended,
        )
        return
    }

    RvBackHandler(enabled = open) { onOpenChange(false) }

    FloatingActionButtonMenu(
        expanded = open,
        button = {
            ToggleFloatingActionButton(
                checked = open,
                onCheckedChange = onOpenChange,
                containerSize = ToggleFloatingActionButtonDefaults.containerSizeMedium(),
                containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadiusMedium(),
            ) {
                Icon(
                    if (checkedProgress > 0.5f) RvCloseIcon else RvAddIcon,
                    contentDescription = strings.listAdd,
                    modifier = Modifier.animateIcon(
                        checkedProgress = { checkedProgress },
                        size = ToggleFloatingActionButtonDefaults.iconSizeMedium(),
                    ),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                onOpenChange(false)
                hybrid()
            },
            icon = { Icon(RvQrScanIcon, contentDescription = null) },
            text = { Text(strings.passkeyComputer) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onOpenChange(false)
                onAdd()
            },
            icon = { Icon(RvKeyIcon, contentDescription = null) },
            text = { Text(strings.editorNewPassword) },
        )
    }
}

@Composable
fun RvScrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
}
