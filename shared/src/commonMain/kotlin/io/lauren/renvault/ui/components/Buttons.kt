package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class RvButtonKind { Filled, Tonal, DangerTonal }

@Composable
fun RvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: RvButtonKind = RvButtonKind.Filled,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    wide: Boolean = true,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val shapes = ButtonDefaults.shapesFor(height)
    val padding = ButtonDefaults.contentPaddingFor(height)
    val sized = modifier
        .then(if (wide) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = height)

    val label: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
        }
        Text(
            text = text,
            style = ButtonDefaults.textStyleFor(height),
            modifier = if (icon != null) Modifier.padding(start = ButtonDefaults.IconSpacing) else Modifier,
        )
    }

    when (kind) {
        RvButtonKind.Filled -> Button(
            onClick = onClick,
            shapes = shapes,
            modifier = sized,
            enabled = enabled,
            contentPadding = padding,
        ) { label() }

        RvButtonKind.Tonal, RvButtonKind.DangerTonal -> FilledTonalButton(
            onClick = onClick,
            shapes = shapes,
            modifier = sized,
            enabled = enabled,
            colors = if (kind == RvButtonKind.DangerTonal) dangerTonal() else ButtonDefaults.filledTonalButtonColors(),
            contentPadding = padding,
        ) { label() }
    }
}

@Composable
private fun dangerTonal(): ButtonColors {
    val colors = MaterialTheme.colorScheme
    return ButtonDefaults.filledTonalButtonColors(
        containerColor = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    )
}
