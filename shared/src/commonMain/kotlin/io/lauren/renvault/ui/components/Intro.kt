package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.rememberReducedMotion
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.app_logo
import io.lauren.renvault.ui.theme.RenVault
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

private enum class IntroScene { Logo, Devices, Autofill, Passkey, Alerts }

private class IntroSlide(
    val scene: IntroScene,
    val icon: ImageVector,
    val polygon: RoundedPolygon,
    val fill: Color,
    val glyph: Color,
    val title: String,
    val body: String,
)

@Composable
fun RvIntro(strings: RvStrings, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    val accents = RenVault.accents
    val slides = remember(strings, colors, accents) {
        listOf(
            IntroSlide(IntroScene.Logo, RvLockIcon, MaterialShapes.Cookie12Sided, if (light) colors.surfaceContainerHighest else Color.White, colors.primary, strings.introWelcomeTitle, strings.introWelcomeBody),
            IntroSlide(IntroScene.Devices, RvDevicesIcon, MaterialShapes.Cookie9Sided, accents.blue.container, accents.blue.content, strings.introEverywhereTitle, strings.introEverywhereBody),
            IntroSlide(IntroScene.Autofill, RvFingerprintIcon, MaterialShapes.Gem, accents.teal.container, accents.teal.content, strings.introSignInTitle, strings.introSignInBody),
            IntroSlide(IntroScene.Passkey, RvPasskeyIcon, MaterialShapes.Sunny, accents.yellow.container, accents.yellow.content, strings.introPasskeyTitle, strings.introPasskeyBody),
            IntroSlide(IntroScene.Alerts, RvShieldIcon, MaterialShapes.Cookie4Sided, colors.tertiaryContainer, colors.onTertiaryContainer, strings.introPrivateTitle, strings.introPrivateBody),
        )
    }
    val morphs = remember(slides) {
        slides.zipWithNext { from, to -> Morph(from.polygon, to.polygon) }
    }
    val pager = rememberPagerState { slides.size }
    val position = remember {
        derivedStateOf { (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (slides.size - 1).toFloat()) }
    }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    val focus = remember { FocusRequester() }
    val previous: () -> Unit = {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    val next: () -> Unit = {
        if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }

    RvBackHandler(enabled = pager.currentPage > 0, onBack = previous)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val copy: @Composable (IntroSlide, Modifier) -> Unit = { slide, modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = slide.icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = slide.title,
                style = MaterialTheme.typography.displaySmall,
                color = colors.onSurface,
            )
            Text(
                text = slide.body,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }
    val header: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier.padding(horizontal = Gutter)) {
            slides.forEach { slide ->
                copy(slide, Modifier.fillMaxWidth().graphicsLayer { alpha = 0f }.clearAndSetSemantics {})
            }
            HorizontalPager(state = pager, modifier = Modifier.matchParentSize()) { index ->
                copy(
                    slides[index],
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
                            translationX = offset * size.width * 0.3f
                            alpha = 1f - abs(offset).coerceIn(0f, 1f)
                        },
                )
            }
        }
    }
    val illustration: @Composable (Modifier, Float) -> Unit = { modifier, largest ->
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            if (side >= HeroMin) {
                val scale = (side / HeroSide).coerceAtMost(largest)
                IntroHero(position = position, slides = slides, morphs = morphs, scale = scale)
            }
        }
    }
    val buttons: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TextButton(
                    onClick = onDone,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.graphicsLayer { alpha = if (last) 0f else 1f },
                    enabled = !last,
                ) {
                    Text(strings.introSkip)
                }
            }
            IntroDots(current = pager.currentPage, count = slides.size)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Button(onClick = next, shapes = ButtonDefaults.shapes()) {
                    val appear = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val vanish = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = last,
                        transitionSpec = { fadeIn(appear) togetherWith fadeOut(vanish) },
                        label = "introAction",
                    ) { done ->
                        Text(if (done) strings.introStart else strings.introNext)
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        next()
                        true
                    }
                    Key.DirectionLeft -> {
                        previous()
                        true
                    }
                    Key.Escape -> {
                        onDone()
                        true
                    }
                    else -> false
                }
            },
    ) {
        val wide = maxWidth >= WideMin && maxWidth > maxHeight
        val roomy = maxWidth >= WideWidth + CardMargin * 2 && maxHeight >= WideHeight + CardMargin * 2
        val split: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 8.dp)) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    header(Modifier.weight(1f).fillMaxHeight())
                    illustration(Modifier.weight(1f).fillMaxHeight(), 1.2f)
                }
                buttons()
            }
        }
        if (wide && roomy) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = WideWidth)
                    .heightIn(max = WideHeight)
                    .fillMaxSize(),
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.surfaceContainer,
            ) {
                split(Modifier.fillMaxSize())
            }
        } else if (wide) {
            split(Modifier.fillMaxSize())
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                header(Modifier.fillMaxWidth().padding(top = 32.dp))
                illustration(Modifier.weight(1f).fillMaxWidth(), 1f)
                buttons()
            }
        }
    }
}

private val HeroSide = 300.dp

private val HeroMin = 96.dp

private val WideMin = 640.dp

private val WideWidth = 1040.dp

private val WideHeight = 600.dp

private val CardMargin = 32.dp


private val Gutter = 24.dp

private val MainSide = 220.dp

@Composable
private fun IntroDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 20.dp else 6.dp,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "introDot",
            )
            Spacer(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

private class ScenePainters(
    val fingerprint: Painter,
    val check: Painter,
    val warning: Painter,
    val passkey: Painter,
    val status: Morph,
)

@Composable
private fun IntroHero(position: State<Float>, slides: List<IntroSlide>, morphs: List<Morph>, scale: Float) {
    val still = rememberReducedMotion()
    val spin = rememberInfiniteTransition(label = "introSpin")
    val turn = if (still) remember { mutableFloatStateOf(0f) } else spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "introTurn",
    )
    val breath = if (still) remember { mutableFloatStateOf(0.5f) } else spin.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000), RepeatMode.Reverse),
        label = "introBreath",
    )
    val devices = if (still) remember { mutableFloatStateOf(0.1f) } else spin.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_800, easing = LinearEasing)),
        label = "introDevices",
    )
    val loop = if (still) remember { mutableFloatStateOf(0.7f) } else spin.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing)),
        label = "introLoop",
    )
    val colors = MaterialTheme.colorScheme
    val painters = ScenePainters(
        fingerprint = rememberVectorPainter(RvFingerprintIcon),
        check = rememberVectorPainter(RvCheckIcon),
        warning = rememberVectorPainter(RvWarningIcon),
        passkey = rememberVectorPainter(RvPasskeyIcon),
        status = remember { Morph(MaterialShapes.Circle, MaterialShapes.Sunny) },
    )
    val path = remember { Path() }
    val unit = remember { Matrix() }

    Box(modifier = Modifier.size(HeroSide * scale), contentAlignment = Alignment.Center) {
        Spacer(
            modifier = Modifier
                .size(MainSide * scale)
                .drawBehind {
                    val at = position.value
                    val base = floor(at).toInt().coerceAtMost(morphs.size - 1)
                    val progress = (at - base).coerceIn(0f, 1f)
                    val fill = lerp(slides[base].fill, slides[base + 1].fill, progress)
                    path.rewind()
                    morphs[base].toPath(progress, path)
                    unit.reset()
                    unit.scale(size.width, size.height)
                    path.transform(unit)
                    val grow = 0.98f + 0.04f * breath.value
                    withTransform({
                        rotate(turn.value + at * 45f)
                        scale(grow, grow)
                    }) {
                        drawPath(path, fill)
                    }
                },
        )

        slides.forEachIndexed { index, slide ->
            if (slide.scene != IntroScene.Logo) return@forEachIndexed
            val shown by remember(index) { derivedStateOf { abs(position.value - index) < 1f } }
            if (shown) {
                Box(
                    modifier = Modifier
                        .size(120.dp * scale)
                        .graphicsLayer {
                            val offset = position.value - index
                            val visible = 1f - abs(offset).coerceIn(0f, 1f)
                            alpha = visible
                            val settle = 0.9f + 0.1f * FastOutSlowInEasing.transform(visible)
                            scaleX = settle
                            scaleY = settle
                            translationX = -offset * 46.dp.toPx() * scale
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp * scale),
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier
                .size(HeroSide * scale)
                .drawBehind {
                    val at = position.value
                    val unitSize = size.minDimension / HeroSide.value
                    slides.forEachIndexed { index, slide ->
                        val visible = 1f - abs(at - index).coerceIn(0f, 1f)
                        if (visible <= 0f) return@forEachIndexed
                        val offset = at - index
                        val settle = 0.9f + 0.1f * FastOutSlowInEasing.transform(visible)
                        withTransform({
                            translate(left = -offset * 46f * unitSize)
                            scale(settle, settle, center)
                        }) {
                            when (slide.scene) {
                                IntroScene.Devices -> devicesScene(unitSize, visible, devices.value, slide.glyph)
                                IntroScene.Autofill -> autofillScene(unitSize, visible, loop.value, slide.glyph, slide.fill, painters)
                                IntroScene.Passkey -> passkeyScene(unitSize, visible, loop.value, slide.glyph, slide.fill, painters)
                                IntroScene.Alerts -> alertsScene(unitSize, visible, loop.value, slide.glyph, slide.fill, colors, painters)
                                else -> Unit
                            }
                        }
                    }
                },
        )
    }
}

private fun phase(t: Float, start: Float, length: Float): Float =
    FastOutSlowInEasing.transform(((t - start) / length).coerceIn(0f, 1f))

private fun DrawScope.icon(painter: Painter, center: Offset, side: Float, tint: Color, alpha: Float) {
    if (side < 1f || alpha <= 0f) return
    translate(center.x - side / 2f, center.y - side / 2f) {
        with(painter) { draw(Size(side, side), alpha = alpha, colorFilter = ColorFilter.tint(tint)) }
    }
}

private fun DrawScope.pill(center: Offset, width: Float, height: Float, color: Color, alpha: Float, radius: Float = height / 2f) {
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius),
        alpha = alpha,
    )
}

private class Device(
    val width: Float,
    val height: Float,
    val corner: Float,
    val base: Float,
)

private val Devices = listOf(
    Device(60f, 104f, 16f, 0f),
    Device(118f, 82f, 16f, 0f),
    Device(112f, 74f, 10f, 1f),
)

private val Snappy = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun snappy(x: Float): Float = Snappy.transform(x.coerceIn(0f, 1f))

private fun DrawScope.devicesScene(u: Float, visible: Float, t: Float, ink: Color) {
    val c = center
    val span = 1f / Devices.size
    val slot = (t / span).toInt().coerceIn(0, Devices.lastIndex)
    val local = (t - slot * span) / span
    val a = Devices[slot]
    val b = Devices[(slot + 1) % Devices.size]
    val leaving = b.base < a.base
    val move = if (leaving) snappy((local - 0.64f) / 0.34f) else snappy((local - 0.56f) / 0.42f)
    val wide = move
    val tall = move
    val settle = move
    fun mix(x: Float, y: Float, k: Float) = x + (y - x) * k
    val w = mix(a.width, b.width, wide) * u
    val h = mix(a.height, b.height, tall) * u
    val corner = mix(a.corner, b.corner, settle) * u
    val base = if (leaving) snappy(1f - (local - 0.4f) / 0.26f) else mix(a.base, b.base, move)
    val lift = 8f * u * base.coerceAtMost(1f)
    val bodyTop = Offset(c.x - w / 2f, c.y - h / 2f - lift)

    if (base > 0.01f) {
        val shown = base.coerceAtMost(1f)
        val baseWidth = (Devices.maxOf { it.width } + 24f) * u * shown
        val baseHeight = minOf(9f * u, baseWidth)
        drawRoundRect(
            color = ink,
            topLeft = Offset(c.x - baseWidth / 2f, bodyTop.y + h + 4f * u * shown),
            size = Size(baseWidth, baseHeight),
            cornerRadius = CornerRadius(baseHeight / 2f),
            alpha = visible * 0.2f,
        )
    }
    drawRoundRect(ink, bodyTop, Size(w, h), CornerRadius(corner), alpha = visible * 0.2f)
    val middle = bodyTop.y + h / 2f
    listOf(-12f, 12f).forEach { dy ->
        val y = middle + dy * u
        drawCircle(ink, radius = 7f * u, center = Offset(bodyTop.x + 15f * u, y), alpha = visible * 0.5f)
        val start = bodyTop.x + 28f * u
        val end = bodyTop.x + w - 13f * u
        drawRoundRect(
            color = ink,
            topLeft = Offset(start, y - 3.5f * u),
            size = Size((end - start).coerceAtLeast(7f * u), 7f * u),
            cornerRadius = CornerRadius(3.5f * u),
            alpha = visible,
        )
    }
}


private fun DrawScope.badgeSwap(at: Offset, radius: Float, morph: Float, spin: Float, shape: Morph, ink: Color, alpha: Float, before: DrawScope.(Float) -> Unit, after: DrawScope.(Float) -> Unit, turnBefore: Boolean = true) {
    val outline = shape.toPath(morph, Path())
    outline.transform(Matrix().apply {
        translate(at.x - radius, at.y - radius)
        scale(radius * 2f, radius * 2f)
    })
    withTransform({ rotate(morph * 90f + spin * 90f, at) }) {
        drawPath(outline, ink, alpha = alpha)
    }
    val out = 1f - FastOutSlowInEasing.transform((morph / 0.5f).coerceIn(0f, 1f))
    val grow = FastOutSlowInEasing.transform(((morph - 0.5f) / 0.5f).coerceIn(0f, 1f))
    if (out > 0f) {
        if (turnBefore) {
            withTransform({
                rotate(-(1f - out) * 90f, at)
                scale(out, out, at)
            }) { before(out) }
        } else {
            before(out)
        }
    }
    if (grow > 0f) {
        withTransform({
            rotate((1f - grow) * 90f, at)
            scale(grow, grow, at)
        }) { after(grow) }
    }
}

private fun DrawScope.passkeyScene(u: Float, visible: Float, t: Float, ink: Color, fill: Color, painters: ScenePainters) {
    val c = center
    val morph = phase(t, 0.26f, 0.22f) * (1f - phase(t, 0.74f, 0.2f))
    val leave = ((t - 0.16f) / 0.26f).coerceIn(0f, 1f)
    val enter = ((t - 0.74f) / 0.22f).coerceIn(0f, 1f)
    val appearing = enter > 0f
    val field = if (appearing) snappy(enter / 0.5f) else snappy(1f - (leave - 0.45f) / 0.55f)
    fun dot(index: Int): Float =
        if (appearing) snappy((enter - 0.4f - index * 0.06f) / 0.3f) else snappy(1f - (leave - (5 - index) * 0.05f) / 0.25f)

    val secretWidth = 112f * u * field
    val secretHeight = minOf(24f * u, secretWidth)
    val shift = 15f * u * (1f - field)
    val badgeAt = Offset(c.x, c.y - 34f * u + shift)
    badgeSwap(
        at = badgeAt,
        radius = 26f * u,
        morph = morph,
        spin = t,
        shape = painters.status,
        ink = ink,
        alpha = visible,
        before = {
            repeat(3) { index ->
                val pop = snappy((it - index * 0.15f) / 0.7f)
                if (pop > 0f) {
                    drawCircle(fill, radius = 4f * u * pop, center = Offset(badgeAt.x + (index - 1) * 10f * u, badgeAt.y), alpha = visible)
                }
            }
        },
        after = { icon(painters.passkey, badgeAt, 30f * u, fill, visible) },
        turnBefore = false,
    )

    val name = Offset(c.x, c.y + 18f * u + shift)
    pill(name, 112f * u, 24f * u, ink, visible * 0.14f)
    drawRoundRect(
        color = ink,
        topLeft = Offset(name.x - 44f * u, name.y - 4f * u),
        size = Size(66f * u, 8f * u),
        cornerRadius = CornerRadius(4f * u),
        alpha = visible,
    )

    val secret = Offset(c.x, name.y + 12f * u + 6f * u * field + secretHeight / 2f)
    if (field > 0f) {
        pill(secret, secretWidth, secretHeight, ink, visible * 0.14f)
        repeat(6) { index ->
            val grow = dot(index)
            if (grow > 0f) {
                drawCircle(ink, radius = 3.5f * u * grow, center = Offset(secret.x - 40f * u + index * 11f * u, secret.y), alpha = visible)
            }
        }
    }
}

private fun DrawScope.autofillScene(u: Float, visible: Float, t: Float, ink: Color, fill: Color, painters: ScenePainters) {
    val c = center
    val reset = 1f - phase(t, 0.86f, 0.12f)
    val morph = phase(t, 0.1f, 0.22f) * reset
    val print = Offset(c.x, c.y - 38f * u)
    badgeSwap(
        at = print,
        radius = 28f * u,
        morph = morph,
        spin = t,
        shape = painters.status,
        ink = ink,
        alpha = visible,
        before = { icon(painters.fingerprint, print, 38f * u, fill, visible) },
        after = { icon(painters.check, print, 30f * u, fill, visible) },
    )

    val filled = phase(t, 0.3f, 0.3f) * reset
    listOf(24f, 58f).forEachIndexed { row, dy ->
        val field = Offset(c.x, c.y + dy * u)
        pill(field, 132f * u, 26f * u, ink, visible * 0.14f)
        val left = field.x - 66f * u + 14f * u
        if (row == 0) {
            if (filled > 0f) {
                val height = 8f * u * (filled / 0.15f).coerceAtMost(1f)
                val width = height + 72f * u * ((filled - 0.15f) / 0.85f).coerceIn(0f, 1f)
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(left, field.y - height / 2f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height / 2f),
                    alpha = visible,
                )
            }
        } else {
            repeat(6) { dot ->
                val grow = ((filled * 6f - dot) / 1f).coerceIn(0f, 1f)
                if (grow > 0f) {
                    drawCircle(ink, radius = 4f * u * FastOutSlowInEasing.transform(grow), center = Offset(left + 4f * u + dot * 13f * u, field.y), alpha = visible)
                }
            }
        }
    }
}

private fun DrawScope.alertsScene(u: Float, visible: Float, t: Float, ink: Color, fill: Color, colors: ColorScheme, painters: ScenePainters) {
    val c = center
    val alarm = phase(t, 0.3f, 0.14f) * (1f - phase(t, 0.88f, 0.1f))
    repeat(3) { row ->
        val y = c.y - 40f * u + row * 40f * u
        drawCircle(ink, radius = 12f * u, center = Offset(c.x - 50f * u, y), alpha = visible * 0.35f)
        drawRoundRect(
            color = ink,
            topLeft = Offset(c.x - 30f * u, y - 4.5f * u),
            size = Size(56f * u, 9f * u),
            cornerRadius = CornerRadius(4.5f * u),
            alpha = visible * 0.85f,
        )
        val status = Offset(c.x + 50f * u, y)
        val danger = if (row == 1) alarm else 0f
        val badge = (13f + 1f * danger) * u
        val outline = painters.status.toPath(danger, Path())
        outline.transform(Matrix().apply {
            translate(status.x - badge, status.y - badge)
            scale(badge * 2f, badge * 2f)
        })
        withTransform({ rotate(danger * 90f, status) }) {
            drawPath(outline, lerp(ink, colors.error, danger), alpha = visible)
        }
        val calm = 1f - danger
        if (calm > 0f) {
            withTransform({
                rotate(-danger * 90f, status)
                scale(calm, calm, status)
            }) {
                icon(painters.check, status, 17f * u, fill, visible * calm)
            }
        }
        if (danger > 0f) {
            withTransform({
                rotate(calm * 90f, status)
                scale(danger, danger, status)
            }) {
                icon(painters.warning, status, 16f * u, colors.onError, visible * danger)
            }
        }
        if (row == 1 && danger > 0f) {
            val pulse = phase(t, 0.44f, 0.36f)
            if (pulse < 1f) {
                drawCircle(colors.error, radius = (14f + 8f * pulse) * u, center = status, alpha = visible * danger * sin(PI.toFloat() * pulse) * 0.7f, style = Stroke(width = 2f * u))
            }
        }
    }
}
