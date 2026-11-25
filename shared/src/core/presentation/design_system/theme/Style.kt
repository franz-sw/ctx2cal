package de.franzsw.ctx2cal.core.presentation.design_system.theme

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.franzsw.ctx2cal.core.presentation.design_system.theme.color.Colors

object Sizes {
    val maxContentWidth = 1000.dp
    val maxDialogWidth = 800.dp
}

val MaterialTheme.sizes: Sizes
    get() = Sizes

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

expect val DarkColorScheme: ColorScheme
expect val LightColorScheme: ColorScheme

fun defaultLightColorScheme() = lightColorScheme(
    primary = Color(Colors.md_theme_light_primary),
    onPrimary = Color(Colors.md_theme_light_onPrimary),
    primaryContainer = Color(Colors.md_theme_light_primaryContainer),
    onPrimaryContainer = Color(Colors.md_theme_light_onPrimaryContainer),
    secondary = Color(Colors.md_theme_light_secondary),
    onSecondary = Color(Colors.md_theme_light_onSecondary),
    secondaryContainer = Color(Colors.md_theme_light_secondaryContainer),
    onSecondaryContainer = Color(Colors.md_theme_light_onSecondaryContainer),
    tertiary = Color(Colors.md_theme_light_tertiary),
    onTertiary = Color(Colors.md_theme_light_onTertiary),
    tertiaryContainer = Color(Colors.md_theme_light_tertiaryContainer),
    onTertiaryContainer = Color(Colors.md_theme_light_onTertiaryContainer),
    error = Color(Colors.md_theme_light_error),
    errorContainer = Color(Colors.md_theme_light_errorContainer),
    onError = Color(Colors.md_theme_light_onError),
    onErrorContainer = Color(Colors.md_theme_light_onErrorContainer),
    background = Color(Colors.md_theme_light_background),
    onBackground = Color(Colors.md_theme_light_onBackground),
    surface = Color(Colors.md_theme_light_surface),
    onSurface = Color(Colors.md_theme_light_onSurface),
    surfaceVariant = Color(Colors.md_theme_light_surfaceVariant),
    onSurfaceVariant = Color(Colors.md_theme_light_onSurfaceVariant),
    outline = Color(Colors.md_theme_light_outline),
    inverseOnSurface = Color(Colors.md_theme_light_inverseOnSurface),
    inverseSurface = Color(Colors.md_theme_light_inverseSurface),
    inversePrimary = Color(Colors.md_theme_light_inversePrimary),
    surfaceTint = Color(Colors.md_theme_light_surfaceTint),
    outlineVariant = Color(Colors.md_theme_light_outlineVariant),
    scrim = Color(Colors.md_theme_light_scrim),
)


fun defaultDarkColorScheme() = darkColorScheme(
    background = Color(Colors.md_theme_dark_background),
    onBackground = Color(Colors.md_theme_dark_onBackground),
    surface = Color(Colors.md_theme_dark_surface),
    onSurface = Color(Colors.md_theme_dark_onSurface),
    primary = Color(Colors.md_theme_dark_primary),
    onPrimary = Color(Colors.md_theme_dark_onPrimary),
    primaryContainer = Color(Colors.md_theme_dark_primaryContainer),
    onPrimaryContainer = Color(Colors.md_theme_dark_onPrimaryContainer),
    secondary = Color(Colors.md_theme_dark_secondary),
    onSecondary = Color(Colors.md_theme_dark_onSecondary),
    secondaryContainer = Color(Colors.md_theme_dark_secondaryContainer),
    onSecondaryContainer = Color(Colors.md_theme_dark_onSecondaryContainer),
    tertiary = Color(Colors.md_theme_dark_tertiary),
    onTertiary = Color(Colors.md_theme_dark_onTertiary),
    tertiaryContainer = Color(Colors.md_theme_dark_tertiaryContainer),
    onTertiaryContainer = Color(Colors.md_theme_dark_onTertiaryContainer),
    error = Color(Colors.md_theme_dark_error),
    errorContainer = Color(Colors.md_theme_dark_errorContainer),
    onError = Color(Colors.md_theme_dark_onError),
    onErrorContainer = Color(Colors.md_theme_dark_onErrorContainer),
    surfaceVariant = Color(Colors.md_theme_dark_surfaceVariant),
    onSurfaceVariant = Color(Colors.md_theme_dark_onSurfaceVariant),
    outline = Color(Colors.md_theme_dark_outline),
    inverseOnSurface = Color(Colors.md_theme_dark_inverseOnSurface),
    inverseSurface = Color(Colors.md_theme_dark_inverseSurface),
    inversePrimary = Color(Colors.md_theme_dark_inversePrimary),
    surfaceTint = Color(Colors.md_theme_dark_surfaceTint),
    outlineVariant = Color(Colors.md_theme_dark_outlineVariant),
    scrim = Color(Colors.md_theme_dark_scrim),
)
