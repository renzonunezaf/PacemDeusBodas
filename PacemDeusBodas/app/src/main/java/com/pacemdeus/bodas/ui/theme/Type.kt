package com.pacemdeus.bodas.ui.theme

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Tipografía (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PacemTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        color = Brown
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        color = Brown
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = Brown
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = Brown
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        color = Brown
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        color = BrownLight
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = Gold
    )
)
