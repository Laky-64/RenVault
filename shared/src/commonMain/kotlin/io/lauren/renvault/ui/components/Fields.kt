package io.lauren.renvault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.RvPasswordStrength
import io.lauren.renvault.ui.theme.RenVault
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow

private val FieldHeight = 68.dp

@Composable
fun RvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secret: Boolean = false,
    shown: Boolean? = null,
    onShownChange: ((Boolean) -> Unit)? = null,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    error: String? = null,
    supporting: String? = null,
    lines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
) {
    var ownShown by remember { mutableStateOf(false) }
    val visible = shown ?: ownShown
    val reveal: (Boolean) -> Unit = onShownChange ?: { ownShown = it }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (lines == 1) FieldHeight else 0.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = leading?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingIcon = when {
            trailing != null -> trailing
            secret -> {
                {
                    IconButton(
                        onClick = { reveal(!visible) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = if (visible) RvVisibilityOffIcon else RvVisibilityIcon,
                            contentDescription = null,
                        )
                    }
                }
            }
            else -> null
        },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { text -> { Text(text) } },
        visualTransformation = if (secret && !visible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = !secret && keyboard == KeyboardType.Text && lines > 1,
            keyboardType = if (secret) KeyboardType.Password else keyboard,
            imeAction = if (onDone != null) ImeAction.Done else imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        singleLine = lines == 1,
        minLines = lines,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
fun RvStrengthMeter(
    strength: RvPasswordStrength,
    label: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accents = RenVault.accents
    val motion = MaterialTheme.motionScheme

    val target = when (strength) {
        RvPasswordStrength.None -> 0.08f
        RvPasswordStrength.Weak -> 0.33f
        RvPasswordStrength.Fair -> 0.66f
        RvPasswordStrength.Strong -> 1f
    }
    val tint = when (strength) {
        RvPasswordStrength.None, RvPasswordStrength.Weak -> colors.error
        RvPasswordStrength.Fair -> accents.fair
        RvPasswordStrength.Strong -> accents.strong
    }

    val progress by animateFloatAsState(target, motion.slowSpatialSpec(), label = "strength")
    val color by animateColorAsState(tint, motion.defaultEffectsSpec(), label = "strengthTint")

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f),
            color = color,
            stopSize = 0.dp,
            amplitude = { 1f - it * 0.7f },
        )
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
fun RvSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun segmentShapes(index: Int, count: Int): ListItemShapes {
    if (count != 1) return ListItemDefaults.segmentedShapes(index = index, count = count)
    val top = ListItemDefaults.segmentedShapes(index = 0, count = 2)
    return remember(top) {
        val outer = top.shape as? CornerBasedShape ?: return@remember top
        top.copy(shape = outer.copy(bottomStart = outer.topStart, bottomEnd = outer.topEnd))
    }
}

@Composable
fun rvSegmentColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
fun RvSegment(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean? = null,
    checked: Boolean? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    overline: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    colors: ListItemColors = rvSegmentColors(),
    content: @Composable () -> Unit,
) {
    val shapes = segmentShapes(index, count)

    if (checked != null && onClick != null) {
        SegmentedListItem(
            checked = checked,
            onCheckedChange = { onClick() },
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            overlineContent = overline,
            supportingContent = supporting,
            colors = colors,
            content = content,
        )
        return
    }

    if (selected != null && onClick != null) {
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            overlineContent = overline,
            supportingContent = supporting,
            colors = colors,
            content = content,
        )
        return
    }

    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            overlineContent = overline,
            supportingContent = supporting,
            colors = colors,
            content = content,
        )
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shapes.shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ListItem(
            headlineContent = content,
            leadingContent = leading,
            trailingContent = trailing,
            overlineContent = overline,
            supportingContent = supporting,
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

@Composable
fun RvSearchInput(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(color, CircleShape)
            .padding(start = 20.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(RvSearchIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                IconButton(onClick = { onQuery("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(RvCloseIcon, contentDescription = null)
                }
            }
        }
    }
}
