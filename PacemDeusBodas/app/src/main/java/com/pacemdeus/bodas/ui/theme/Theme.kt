package com.pacemdeus.bodas.ui.theme

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Tema Material 3 (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PacemColorScheme = lightColorScheme(
    primary = Gold,
    onPrimary = White,
    primaryContainer = Gold20,
    secondary = Brown,
    onSecondary = Cream,
    background = Cream,
    onBackground = Brown,
    surface = White,
    onSurface = Brown,
    surfaceVariant = NavBg,
    onSurfaceVariant = BrownLight,
    outline = Divider,
    error = Red,
    onError = White
)

@Composable
fun PacemDeusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PacemColorScheme,
        typography = PacemTypography,
        content = content
    )
}
