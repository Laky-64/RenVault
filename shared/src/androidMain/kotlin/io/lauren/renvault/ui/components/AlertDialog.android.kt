package io.lauren.renvault.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun RvAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    iconContentColor: Color,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.sizeIn(minWidth = MinWidth, maxWidth = MaxWidth), propagateMinConstraints = true) {
                Surface(
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures {} },
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(modifier = Modifier.padding(DialogPadding)) {
                        icon?.let {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor.takeOrElse { AlertDialogDefaults.iconContentColor }) {
                                Box(Modifier.padding(bottom = HeaderGap).align(Alignment.CenterHorizontally)) { it() }
                            }
                        }
                        title?.let {
                            Styled(AlertDialogDefaults.titleContentColor, MaterialTheme.typography.headlineSmall) {
                                Box(
                                    Modifier
                                        .padding(bottom = HeaderGap)
                                        .align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally),
                                ) { it() }
                            }
                        }
                        text?.let {
                            Styled(AlertDialogDefaults.textContentColor, MaterialTheme.typography.bodyMedium) {
                                Box(
                                    Modifier
                                        .weight(1f, fill = false)
                                        .padding(bottom = TextGap)
                                        .align(Alignment.Start),
                                ) { it() }
                            }
                        }
                        Box(modifier = Modifier.align(Alignment.End)) {
                            Styled(MaterialTheme.colorScheme.primary, MaterialTheme.typography.labelLarge) {
                                Buttons(confirmButton, dismissButton)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Buttons(confirm: @Composable () -> Unit, dismiss: @Composable (() -> Unit)?) {
    val touch = LocalMinimumInteractiveComponentSize.current.takeOrElse { 0.dp } - ButtonDefaults.MinHeight
    val direction = LocalLayoutDirection.current
    val flipped = if (direction == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides flipped) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
            verticalArrangement = Arrangement.spacedBy((ButtonGap - touch).coerceIn(0.dp, ButtonGap)),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                confirm()
                dismiss?.invoke()
            }
        }
    }
}

@Composable
private fun Styled(color: Color, style: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides color,
        LocalTextStyle provides LocalTextStyle.current.merge(style),
        content = content,
    )
}

private val MinWidth = 280.dp
private val MaxWidth = 560.dp
private val DialogPadding = 24.dp
private val HeaderGap = 16.dp
private val TextGap = 24.dp
private val ButtonGap = 8.dp
