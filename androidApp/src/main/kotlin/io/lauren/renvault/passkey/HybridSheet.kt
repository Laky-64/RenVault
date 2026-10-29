@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.lauren.renvault.passkey

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import io.lauren.renvault.R
import io.lauren.renvault.bridge.HybridPrompt
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.ui.components.RvCheckIcon
import io.lauren.renvault.ui.components.RvChevronRightIcon
import io.lauren.renvault.ui.components.RvComputerIcon
import io.lauren.renvault.ui.components.RvFavicon
import io.lauren.renvault.ui.components.RvPasskeyIcon
import io.lauren.renvault.ui.components.RvPhoneIcon
import io.lauren.renvault.ui.components.RvSegment
import io.lauren.renvault.ui.components.RvSiteIcon
import io.lauren.renvault.ui.components.RvWarningIcon
import io.lauren.renvault.ui.components.fitted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

private val HeroSize = 96.dp

private val Searching = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Pill,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Oval,
)

@Composable
internal fun HybridSheet(
    stage: Stage,
    onBluetooth: () -> Unit,
    onClose: () -> Unit,
    onAnswer: (HybridPrompt, Int) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val site = (stage as? Stage.Asking)?.prompt?.relyingParty
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MorphHero(stage, site?.let { rememberFavicon(it) }, site)
        AnimatedContent(
            targetState = stage,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false))
            },
            label = "hybrid",
        ) { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (current) {
                    Stage.Bluetooth -> {
                        Heading(stringResource(R.string.hybrid_bluetooth_title), stringResource(R.string.hybrid_bluetooth))
                        Button(onClick = onBluetooth, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(stringResource(R.string.hybrid_bluetooth_enable))
                        }
                        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hybrid_cancel)) }
                    }
                    Stage.Connecting -> {
                        Heading(stringResource(R.string.hybrid_connecting), stringResource(R.string.hybrid_connecting_hint))
                        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hybrid_cancel)) }
                    }
                    is Stage.Asking -> Ask(current.prompt, onAnswer)
                    Stage.Done -> Heading(stringResource(R.string.hybrid_done_title), stringResource(R.string.hybrid_done))
                    is Stage.Failed -> {
                        Heading(stringResource(current.message), current.hint?.let { stringResource(it) } ?: current.detail)
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(stringResource(R.string.hybrid_close))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

private const val Room = 1.3f

private val fits = HashMap<RoundedPolygon, Float>()

private fun RoundedPolygon.outline(): List<Pair<Float, Float>> = cubics.flatMap { c ->
    (0 until 8).map { i ->
        val t = i / 8f
        val u = 1f - t
        val x = u * u * u * c.anchor0X + 3 * u * u * t * c.control0X + 3 * u * t * t * c.control1X + t * t * t * c.anchor1X
        val y = u * u * u * c.anchor0Y + 3 * u * u * t * c.control0Y + 3 * u * t * t * c.control1Y + t * t * t * c.anchor1Y
        x to y
    }
}

private fun RoundedPolygon.area(): Float {
    val points = outline()
    var sum = 0f
    for (i in points.indices) {
        val (x0, y0) = points[i]
        val (x1, y1) = points[(i + 1) % points.size]
        sum += x0 * y1 - x1 * y0
    }
    return abs(sum) / 2f
}

private fun RoundedPolygon.fit(): Float = fits.getOrPut(this) {
    val reach = outline().maxOf { (x, y) -> hypot(x - 0.5f, y - 0.5f) }
    val even = sqrt(MaterialShapes.Cookie12Sided.area() / area())
    minOf(even, 0.5f * Room / reach) / Room
}

private class MorphShape(private val morph: Morph, private val progress: Float, private val fit: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        fun x(v: Float) = (0.5f + (v - 0.5f) * fit) * w
        fun y(v: Float) = (0.5f + (v - 0.5f) * fit) * h
        val cubics = morph.asCubics(progress)
        cubics.firstOrNull()?.let { path.moveTo(x(it.anchor0X), y(it.anchor0Y)) }
        for (c in cubics) {
            path.cubicTo(x(c.control0X), y(c.control0Y), x(c.control1X), y(c.control1Y), x(c.anchor1X), y(c.anchor1Y))
        }
        path.close()
        return Outline.Generic(path)
    }
}

private fun heroShape(stage: Stage): RoundedPolygon = when (stage) {
    is Stage.Asking -> MaterialShapes.Cookie12Sided
    Stage.Done -> MaterialShapes.Cookie12Sided
    is Stage.Failed -> MaterialShapes.Cookie4Sided
    else -> MaterialShapes.Cookie9Sided
}

@Composable
private fun MorphHero(stage: Stage, favicon: Painter?, site: String?) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    var from by remember { mutableStateOf(heroShape(stage)) }
    var to by remember { mutableStateOf(heroShape(stage)) }
    val progress = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(0.6f) }
    val morph = remember(from, to) { Morph(from, to) }

    suspend fun morphTo(target: RoundedPolygon, turn: Float) {
        if (target === to && progress.value >= 1f) {
            rotation.animateTo(rotation.value + turn, motion.defaultSpatialSpec())
            return
        }
        from = if (progress.value >= 0.5f) to else from
        to = target
        progress.snapTo(0f)
        coroutineScope {
            launch { progress.animateTo(1f, motion.defaultEffectsSpec()) }
            launch { rotation.animateTo(rotation.value + turn, motion.defaultSpatialSpec()) }
        }
    }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, motion.slowSpatialSpec())
    }
    LaunchedEffect(stage::class) {
        when (stage) {
            Stage.Connecting -> {
                var step = 0
                while (true) {
                    morphTo(Searching[step % Searching.size], 90f)
                    step++
                    delay(120)
                }
            }
            Stage.Done -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                launch {
                    scale.animateTo(1.06f, motion.fastSpatialSpec())
                    scale.animateTo(1f, motion.defaultSpatialSpec())
                }
                morphTo(heroShape(stage), 120f)
            }
            else -> morphTo(heroShape(stage), 60f)
        }
    }

    val container by animateColorAsState(
        when (stage) {
            Stage.Done -> colors.primary
            is Stage.Failed -> colors.errorContainer
            Stage.Bluetooth -> colors.secondaryContainer
            else -> colors.primaryContainer
        },
        motion.defaultEffectsSpec(),
        label = "container",
    )
    val content by animateColorAsState(
        when (stage) {
            Stage.Done -> colors.onPrimary
            is Stage.Failed -> colors.onErrorContainer
            Stage.Bluetooth -> colors.onSecondaryContainer
            else -> colors.onPrimaryContainer
        },
        motion.defaultEffectsSpec(),
        label = "content",
    )
    val t = progress.value.coerceIn(0f, 1f)
    val shape = MorphShape(morph, t, from.fit() + (to.fit() - from.fit()) * t)

    Box(
        modifier = Modifier
            .size(HeroSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(HeroSize * Room)
                .graphicsLayer { rotationZ = rotation.value }
                .clip(shape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            if (stage is Stage.Asking && site != null) {
                RvSiteIcon(
                    fallback = site,
                    favicon = favicon,
                    size = HeroSize,
                    shape = RectangleShape,
                    modifier = Modifier.graphicsLayer { rotationZ = -rotation.value },
                )
            }
        }
        AnimatedContent(
            targetState = heroIcon(stage),
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f))
            },
            label = "icon",
        ) { icon ->
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(40.dp))
        }
    }
}

private fun heroIcon(stage: Stage): ImageVector? = when (stage) {
    Stage.Bluetooth -> RvPhoneIcon
    Stage.Connecting -> RvComputerIcon
    Stage.Done -> RvCheckIcon
    is Stage.Failed -> RvWarningIcon
    else -> null
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (subtitle.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Ask(prompt: HybridPrompt, onAnswer: (HybridPrompt, Int) -> Unit) {
    Heading(
        stringResource(if (prompt.create) R.string.hybrid_create else R.string.hybrid_get, prompt.relyingParty),
        stringResource(if (prompt.create) R.string.hybrid_create_hint else R.string.hybrid_get_hint),
    )
    if (prompt.create) {
        RvSegment(
            index = 0,
            count = 1,
            leading = { RvSiteIcon(fallback = prompt.username.ifEmpty { prompt.relyingParty }) },
            supporting = { Text(prompt.relyingParty) },
        ) {
            Text(prompt.username.ifEmpty { prompt.relyingParty })
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onAnswer(prompt, 0) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(RvPasskeyIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.hybrid_save))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            prompt.accounts.forEachIndexed { index, account ->
                val name = account.username.ifEmpty { account.title }
                RvSegment(
                    index = index,
                    count = prompt.accounts.size,
                    onClick = { onAnswer(prompt, index) },
                    leading = { RvSiteIcon(fallback = name) },
                    trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
                    supporting = if (account.title.isNotEmpty() && account.title != name) ({ Text(account.title) }) else null,
                ) {
                    Text(name)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { onAnswer(prompt, -1) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hybrid_cancel)) }
}

@Composable
private fun rememberFavicon(domain: String): Painter? {
    val tile = with(LocalDensity.current) { HeroSize.roundToPx() }
    var favicon by remember(domain) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(domain) {
        favicon = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Vault.icon(domain)
                RvFavicon(BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap().fitted(tile))
            }.getOrNull()
        }
    }
    return favicon
}
