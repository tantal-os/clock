package app.tantal.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.tantal.clock.R

internal val ClockNumerals = FontFamily(
    Font(
        R.font.google_sans_flex,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontWeight.Medium,
            FontStyle.Normal,
            FontVariation.Setting("ROND", 100f),
        ),
    ),
)

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
        content = content,
    )
}
