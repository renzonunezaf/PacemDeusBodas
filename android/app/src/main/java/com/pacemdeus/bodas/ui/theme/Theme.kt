package com.pacemdeus.bodas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Theme oficial Pacem Deus. Cumple el patron del profesor (MaterialTheme
// directo sin theme custom complejo) pero ya con la paleta de marca
// aplicada al colorScheme. No usamos dynamicColor (Android 12+) porque
// rompe el branding.

private val PacemColorScheme = lightColorScheme(
    primary = Gold,
    onPrimary = Cream,
    primaryContainer = GoldSoft,
    onPrimaryContainer = Brown,

    secondary = Brown,
    onSecondary = Cream,
    secondaryContainer = BrownLight,
    onSecondaryContainer = Cream,

    tertiary = GoldDark,
    onTertiary = Cream,

    background = Cream,
    onBackground = Brown,

    surface = NavBg,
    onSurface = Brown,
    surfaceVariant = GoldSoft,
    onSurfaceVariant = BrownLight,

    error = Danger,
    onError = Cream,
    outline = Divider,
    outlineVariant = Sand
)

@Composable
fun PacemDeusBodasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PacemColorScheme,
        typography = PacemTypography,
        content = content
    )
}
