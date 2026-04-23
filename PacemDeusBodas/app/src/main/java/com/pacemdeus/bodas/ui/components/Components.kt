package com.pacemdeus.bodas.ui.components

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Componentes Reutilizables (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.ui.theme.*

/**
 * Barra superior con título estilizado.
 * - onBack: si se provee muestra flecha atrás a la izquierda
 * - onLogout: si se provee muestra menú ⋮ con "Cerrar sesión" a la derecha
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacemTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                title,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                color = Brown
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Brown)
                }
            }
        },
        actions = {
            if (onLogout != null) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Menú", tint = Brown)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Cerrar sesión") },
                        onClick = { showMenu = false; onLogout() }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
    )
}

/** Badge de estado con color semántico según status de la boda */
@Composable
fun StatusBadge(status: String) {
    val (text, bg, fg) = when (status) {
        "DRAFT" -> Triple("Borrador", StatusDraftBg, StatusDraft)
        "SUBMITTED" -> Triple("Enviado", StatusSubmittedBg, StatusSubmitted)
        "APPROVED" -> Triple("Aprobado", StatusApprovedBg, StatusApproved)
        "CONTRACTED" -> Triple("Contratado", StatusContractedBg, StatusContracted)
        "CANCELLATION_REQUESTED" -> Triple("Cancelación pedida", StatusDraftBg, Red)
        "COMPLETED" -> Triple("Completado", StatusApprovedBg, StatusApproved)
        else -> Triple(status, StatusDraftBg, StatusDraft)
    }
    Text(
        text,
        fontSize = 12.sp,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** Campo de texto estándar con estilo Pacem Deus */
@Composable
fun PacemTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = singleLine,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = Divider,
            focusedLabelColor = Gold,
            cursorColor = Gold
        )
    )
}

/** Botón dorado principal — onClick al final para soportar trailing lambda */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = White,
            disabledContainerColor = Gold.copy(alpha = 0.4f)
        )
    ) {
        Text(text, fontSize = 16.sp)
    }
}

/** Botón outline secundario — onClick al final para soportar trailing lambda */
@Composable
fun OutlineGoldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
    ) {
        Text(text, fontSize = 16.sp)
    }
}

/** Botón de acción destructiva (cancelar, rechazar, etc.) */
@Composable
fun DangerOutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
    ) {
        Text(text, fontSize = 16.sp)
    }
}

/** Tarjeta base reutilizable con borde redondeado y sombra suave */
@Composable
fun PacemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Etiqueta tipo overline dorada usada como encabezado de sección */
@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Gold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** Indicador de carga centrado en pantalla completa */
@Composable
fun LoadingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Gold)
    }
}

/** Mensaje de estado vacío centrado */
@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Sand,
            textAlign = TextAlign.Center
        )
    }
}

/** Tarjeta resumen con label + número grande (usada en dashboards) */
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Sand,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Brown)
        }
    }
}

/**
 * Diálogo de confirmación estándar.
 * Usar antes de acciones destructivas o irreversibles (aprobar, rechazar, cancelar).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirmar",
    cancelLabel: String = "Cancelar",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Brown) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Red else Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel, color = Sand) }
        }
    )
}
