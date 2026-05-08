package com.pacemdeus.bodas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.WeddingStatus

// Componentes reutilizables. Centralizan el estilo de la app para no
// repetirlo en cada pantalla. Todos respetan los colores de marca (Gold,
// Brown, Cream) sin redefinirlos en cada lugar.

// ─── PALETA DE LA APP ──────────────────────────────────────

val Gold       = Color(0xFFB8995E)
val GoldDark   = Color(0xFF8C7344)
val GoldSoft   = Color(0xFFEFE5D0)
val Brown      = Color(0xFF3E2F1C)
val BrownLight = Color(0xFF6B5A41)
val Cream      = Color(0xFFFAF6EE)
val Sand       = Color(0xFF9B8A6E)
val Divider    = Color(0xFFE5DDC9)
val NavBg      = Color(0xFFFFFFFF)

// ─── BARRA SUPERIOR ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacemTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Text(
                title,
                color = Brown,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atras",
                        tint = Brown
                    )
                }
            }
        },
        actions = {
            if (onLogout != null) {
                IconButton(onClick = onLogout) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "Cerrar sesion",
                        tint = Brown
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
    )
}

// ─── BOTONES PRINCIPALES ───────────────────────────────────

@Composable
fun GoldButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color.White,
            disabledContainerColor = Gold.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OutlineGoldButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
    ) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}

// ─── TARJETAS ──────────────────────────────────────────────

@Composable
fun PacemCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

// ─── ETIQUETA DE SECCION ───────────────────────────────────

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Gold,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ─── BADGE DE ESTADO DE BODA ───────────────────────────────

@Composable
fun StatusBadge(status: WeddingStatus) {
    val (bg, fg) = when (status) {
        WeddingStatus.DRAFT                  -> Color(0xFFF5E6C8) to Color(0xFF8C6A1A)
        WeddingStatus.SUBMITTED              -> Color(0xFFD9E8F5) to Color(0xFF1F4E79)
        WeddingStatus.APPROVED               -> Color(0xFFD7EAD2) to Color(0xFF2E5E1A)
        WeddingStatus.CONTRACTED             -> Color(0xFFE5D7F2) to Color(0xFF5B2E92)
        WeddingStatus.CANCELLATION_REQUESTED -> Color(0xFFF5D6D6) to Color(0xFF8B1A1A)
        WeddingStatus.COMPLETED              -> Color(0xFFCFCFCF) to Color(0xFF333333)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            status.displayName(),
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ─── TARJETA DE METRICA ────────────────────────────────────

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                label.uppercase(),
                color = Sand,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ─── ESTADOS DE CARGA Y VACIO ──────────────────────────────

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize().background(Cream),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Gold)
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = Sand,
            fontSize = 14.sp
        )
    }
}

// ─── FONDO PANTALLA COMPLETA ───────────────────────────────

@Composable
fun ScreenBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Cream)) {
        content()
    }
}
