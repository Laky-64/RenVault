package io.lauren.renvault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.googlesansflex
import org.jetbrains.compose.resources.Font

private val Weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

@Composable
private fun flexFamily(roundness: Float): FontFamily = FontFamily(
    Weights.map { weight ->
        Font(
            resource = Res.font.googlesansflex,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("ROND", roundness),
            ),
        )
    },
)

@Composable
internal fun rememberTypography(): Typography {
    val plain = flexFamily(0f)
    val rounded = flexFamily(100f)
    val base = Typography()

    fun TextStyle.plain() = copy(fontFamily = plain)
    fun TextStyle.rounded() = copy(fontFamily = rounded)

    return Typography(
        displayLarge = base.displayLarge.plain(),
        displayMedium = base.displayMedium.plain(),
        displaySmall = base.displaySmall.plain(),
        headlineLarge = base.headlineLarge.plain(),
        headlineMedium = base.headlineMedium.plain(),
        headlineSmall = base.headlineSmall.plain(),
        titleLarge = base.titleLarge.plain(),
        titleMedium = base.titleMedium.plain(),
        titleSmall = base.titleSmall.plain(),
        bodyLarge = base.bodyLarge.plain(),
        bodyMedium = base.bodyMedium.plain(),
        bodySmall = base.bodySmall.plain(),
        labelLarge = base.labelLarge.plain(),
        labelMedium = base.labelMedium.plain(),
        labelSmall = base.labelSmall.plain(),
        displayLargeEmphasized = base.displayLargeEmphasized.rounded(),
        displayMediumEmphasized = base.displayMediumEmphasized.rounded(),
        displaySmallEmphasized = base.displaySmallEmphasized.rounded(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.rounded(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.rounded(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.rounded(),
        titleLargeEmphasized = base.titleLargeEmphasized.rounded(),
        titleMediumEmphasized = base.titleMediumEmphasized.plain(),
        titleSmallEmphasized = base.titleSmallEmphasized.plain(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.plain(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.plain(),
        bodySmallEmphasized = base.bodySmallEmphasized.plain(),
        labelLargeEmphasized = base.labelLargeEmphasized.plain(),
        labelMediumEmphasized = base.labelMediumEmphasized.plain(),
        labelSmallEmphasized = base.labelSmallEmphasized.plain(),
    )
}
