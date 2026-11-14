package app.tantal.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.tantal.clock.R

// Google Sans Flex with the roundness axis at its maximum. A variable font needs one entry per weight in use.
private val flex = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.google_sans_flex,
            weight,
            variationSettings = FontVariation.Settings(weight, FontStyle.Normal, FontVariation.Setting("ROND", 100f)),
        )
    },
)

private fun TextStyle.flex() = copy(fontFamily = flex)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.flex(),
        displayMedium = displayMedium.flex(),
        displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(),
        headlineMedium = headlineMedium.flex(),
        headlineSmall = headlineSmall.flex(),
        titleLarge = titleLarge.flex(),
        titleMedium = titleMedium.flex(),
        titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.flex(),
        bodyMedium = bodyMedium.flex(),
        bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(),
        labelMedium = labelMedium.flex(),
        labelSmall = labelSmall.flex(),
        displayLargeEmphasized = displayLargeEmphasized.flex(),
        displayMediumEmphasized = displayMediumEmphasized.flex(),
        displaySmallEmphasized = displaySmallEmphasized.flex(),
        headlineLargeEmphasized = headlineLargeEmphasized.flex(),
        headlineMediumEmphasized = headlineMediumEmphasized.flex(),
        headlineSmallEmphasized = headlineSmallEmphasized.flex(),
        titleLargeEmphasized = titleLargeEmphasized.flex(),
        titleMediumEmphasized = titleMediumEmphasized.flex(),
        titleSmallEmphasized = titleSmallEmphasized.flex(),
        bodyLargeEmphasized = bodyLargeEmphasized.flex(),
        bodyMediumEmphasized = bodyMediumEmphasized.flex(),
        bodySmallEmphasized = bodySmallEmphasized.flex(),
        labelLargeEmphasized = labelLargeEmphasized.flex(),
        labelMediumEmphasized = labelMediumEmphasized.flex(),
        labelSmallEmphasized = labelSmallEmphasized.flex(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TantalTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        },
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        content = content,
    )
}
