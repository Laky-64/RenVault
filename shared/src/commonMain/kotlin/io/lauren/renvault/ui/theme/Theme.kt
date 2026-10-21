package io.lauren.renvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
fun RenVaultTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val locale = rememberLocaleController()
    val scheme = dynamicScheme(dark) ?: if (dark) darkScheme else lightScheme

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = rememberTypography(),
    ) {
        CompositionLocalProvider(
            LocalRvAccents provides if (dark) darkAccents else lightAccents,
            LocalLocale provides locale,
            LocalStrings provides rememberStrings(locale.current),
            content = content,
        )
    }
}
