package io.lauren.renvault.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class RvTone(val container: Color, val content: Color)

@Immutable
data class RvAccents(
    val blue: RvTone,
    val green: RvTone,
    val yellow: RvTone,
    val teal: RvTone,
    val red: RvTone,
    val orange: RvTone,
    val fair: Color,
    val strong: Color,
) {
    fun forIndex(index: Int): RvTone = order[((index % order.size) + order.size) % order.size]

    private val order: List<RvTone>
        get() = listOf(blue, green, yellow, teal, red, orange)
}

internal val lightAccents = RvAccents(
    blue = RvTone(Color(0xFFD8E2FF), Color(0xFF0B3E8F)),
    green = RvTone(Color(0xFFC4EFC0), Color(0xFF0F5223)),
    yellow = RvTone(Color(0xFFFFE08A), Color(0xFF574500)),
    teal = RvTone(Color(0xFFBDEBF5), Color(0xFF004E5B)),
    red = RvTone(Color(0xFFFFDAD5), Color(0xFF8C1D18)),
    orange = RvTone(Color(0xFFFFDCC2), Color(0xFF6E3900)),
    fair = Color(0xFF8F5A00),
    strong = Color(0xFF146C2E),
)

internal val darkAccents = RvAccents(
    blue = RvTone(Color(0xFF214A9B), Color(0xFFD8E2FF)),
    green = RvTone(Color(0xFF1E5E2E), Color(0xFFC4EFC0)),
    yellow = RvTone(Color(0xFF5C4A00), Color(0xFFFFE08A)),
    teal = RvTone(Color(0xFF005766), Color(0xFFBDEBF5)),
    red = RvTone(Color(0xFF8C1D18), Color(0xFFFFDAD5)),
    orange = RvTone(Color(0xFF713B00), Color(0xFFFFDCC2)),
    fair = Color(0xFFFFB95C),
    strong = Color(0xFF7FDA8C),
)

internal val lightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3F5BA9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF254290),
    inversePrimary = Color(0xFFB4C5FF),
    secondary = Color(0xFF585E72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF404659),
    tertiary = Color(0xFF725572),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDD7FA),
    onTertiaryContainer = Color(0xFF593D59),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF3F5BA9),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF757680),
    outlineVariant = Color(0xFFC5C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
)

internal val darkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF052C78),
    primaryContainer = Color(0xFF254290),
    onPrimaryContainer = Color(0xFFDBE1FF),
    inversePrimary = Color(0xFF3F5BA9),
    secondary = Color(0xFFC0C6DD),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFE0BBDD),
    onTertiary = Color(0xFF412742),
    tertiaryContainer = Color(0xFF593D59),
    onTertiaryContainer = Color(0xFFFDD7FA),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB4C5FF),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF2F3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
)
