package io.lauren.renvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import io.lauren.renvault.ui.LocalLocale
import io.lauren.renvault.ui.LocalStrings
import io.lauren.renvault.ui.rememberLocaleController
import io.lauren.renvault.ui.rememberStrings

val LocalRvAccents: ProvidableCompositionLocal<RvAccents> = staticCompositionLocalOf { lightAccents }

object RenVault {
    val accents: RvAccents
        @Composable @ReadOnlyComposable get() = LocalRvAccents.current
}

@Composable
internal expect fun dynamicScheme(dark: Boolean): ColorScheme?

@Composable
internal expect fun SystemBars(dark: Boolean)

@Composable
fun RenVaultTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val locale = rememberLocaleController()
    val theme = rememberThemeController()
    val system = isSystemInDarkTheme()
    val night = dark ?: theme.mode.isDark(system)
    SystemBars(night)
    val scheme = dynamicScheme(night) ?: if (night) darkScheme else lightScheme

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = rememberTypography(),
    ) {
        CompositionLocalProvider(
            LocalRvAccents provides if (night) darkAccents else lightAccents,
            LocalLocale provides locale,
            LocalThemeController provides theme,
            LocalStrings provides rememberStrings(locale.current),
        ) {
            ThemeReveal(theme = theme, system = system, content = content)
        }
    }
}

@Composable
private fun ThemeReveal(
    theme: RvThemeController,
    system: Boolean,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val radius = remember { Animatable(0f) }
    val currentSystem by rememberUpdatedState(system)
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(theme) {
        snapshotFlow { theme.reveal }.filterNotNull().collect { request ->
            val changes = request.mode.isDark(currentSystem) != theme.mode.isDark(currentSystem)
            val before = if (changes && bounds != IntSize.Zero) {
                runCatching { layer.toImageBitmap() }.getOrNull()
            } else {
                null
            }
            theme.apply(request.mode)
            if (before == null) return@collect

            origin = request.origin
            frozen = before
            radius.snapTo(0f)
            radius.animateTo(farthestCorner(request.origin, bounds), tween(RevealMs, easing = RevealEasing))
            frozen = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { bounds = it }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val image = frozen ?: return@drawWithContent
                val hole = Path().apply { addOval(Rect(origin, radius.value)) }
                clipPath(hole, ClipOp.Difference) { drawImage(image) }
            },
    ) {
        content()
    }
}

private fun RvThemeMode.isDark(system: Boolean): Boolean = when (this) {
    RvThemeMode.System -> system
    RvThemeMode.Light -> false
    RvThemeMode.Dark -> true
}

private fun farthestCorner(origin: Offset, size: IntSize): Float {
    val dx = max(origin.x, size.width - origin.x)
    val dy = max(origin.y, size.height - origin.y)
    return sqrt(dx * dx + dy * dy)
}

private const val RevealMs = 650
private val RevealEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
