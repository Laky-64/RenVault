package io.lauren.renvault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

private val Gap = 8.dp
private val SplitGap = 20.dp
private val MaxBox = 52.dp

@Composable
fun RvCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    group: Int = 3,
    enabled: Boolean = true,
    error: Int = 0,
    autofocus: Boolean = true,
    onComplete: (String) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val requester = remember { FocusRequester() }

    val full = value.length == length
    val active = minOf(value.length, length - 1)
    val splits = if (group in 1 until length) 1 else 0

    val shake = remember { Animatable(0f) }
    val shaking = shake.isRunning

    LaunchedEffect(error) { if (error > 0) shake.shake() }

    LaunchedEffect(autofocus, enabled) {
        if (autofocus && enabled) runCatching { requester.requestFocus() }
    }

    var delivered by remember { mutableStateOf("") }

    LaunchedEffect(value, enabled) {
        if (!full || !enabled) {
            if (!full) delivered = ""
            return@LaunchedEffect
        }
        if (delivered == value) return@LaunchedEffect
        delivered = value
        onComplete(value)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .shaken { shake.value },
    ) {
        val gaps = Gap * (length - 1 - splits) + SplitGap * splits
        val side = minOf(MaxBox, (maxWidth - gaps) / length)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(length) { index ->
                val char = value.getOrNull(index)
                val lifted = focused && enabled && index == active && !full

                val border by animateColorAsState(
                    targetValue = when {
                        shaking -> colors.error
                        lifted -> colors.primary
                        else -> Color.Transparent
                    },
                    animationSpec = motion.fastEffectsSpec(),
                    label = "codeBorder",
                )

                val fill by animateColorAsState(
                    targetValue = when {
                        shaking -> colors.errorContainer
                        char != null -> colors.primaryContainer
                        else -> colors.surfaceContainerHighest
                    },
                    animationSpec = motion.fastEffectsSpec(),
                    label = "codeFill",
                )

                val corner by animateDpAsState(
                    targetValue = if (lifted || char != null) side / 2 else 14.dp,
                    animationSpec = motion.fastSpatialSpec(),
                    label = "codeCorner",
                )

                val shape = RoundedCornerShape(corner)

                Box(
                    modifier = Modifier
                        .size(width = side, height = side * 1.18f)
                        .background(fill, shape)
                        .border(2.dp, border, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (char != null) {
                        val pop = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { pop.animateTo(1f, motion.fastSpatialSpec()) }

                        Text(
                            text = char.toString(),
                            style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.onPrimaryContainer,
                            modifier = Modifier.graphicsLayer {
                                alpha = pop.value.coerceIn(0f, 1f)
                                val scale = 0.4f + 0.6f * pop.value
                                scaleX = scale
                                scaleY = scale
                            },
                        )
                    }
                }

                if (index == length - 1) return@repeat
                Spacer(Modifier.width(if (index == group - 1 && splits == 1) SplitGap else Gap))
            }
        }

        BasicTextField(
            value = TextFieldValue(text = value, selection = TextRange(value.length)),
            onValueChange = { field -> onValueChange(field.text.filter { it.isDigit() }.take(length)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(Color.Transparent),
            interactionSource = interactionSource,
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .focusRequester(requester),
        )
    }
}
