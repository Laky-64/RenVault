package io.lauren.renvault.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse

@Composable
actual fun RvAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    iconContentColor: Color,
) = AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = confirmButton,
    dismissButton = dismissButton,
    icon = icon,
    title = title,
    text = text,
    iconContentColor = iconContentColor.takeOrElse { AlertDialogDefaults.iconContentColor },
)
