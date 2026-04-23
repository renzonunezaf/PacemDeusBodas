package com.pacemdeus.bodas.ui.coordinator

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Detalle del Evento (Coordinador)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Vista completa de un evento para el admin, con todas las acciones
// disponibles en un solo lugar:
//   - Tomar foto del venue (CameraActivity)
//   - Asignar wedding planner (AssignPlannerActivity)
//   - Aprobar o devolver
//   - Ver contrato
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.ApproveRequest
import com.pacemdeus.bodas.data.api.models.Wedding
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.couple.ContractActivity
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class WeddingDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weddingId = intent.getStringExtra("weddingId") ?: ""
        setContent {
            PacemDeusTheme {
                WeddingDetailScreen(
                    weddingId = weddingId,
                    onBack = { finish() },
                    onCamera = {
                        startActivity(
                            Intent(this, CameraActivity::class.java)
                                .putExtra("weddingId", weddingId)
                        )
                    },
                    onAssignPlanner = {
                        startActivity(
                            Intent(this, AssignPlannerActivity::class.java)
                                .putExtra("weddingId", weddingId)
                        )
                    },
                    onContract = {
                        startActivity(
                            Intent(this, ContractActivity::class.java)
                                .putExtra("weddingId", weddingId)
                        )
                    },
                    onOpenMaps = { lat, lng, name ->
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
                            )
                        )
                    },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeddingDetailScreen(
    weddingId: String,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onAssignPlanner: () -> Unit,
    onContract: () -> Unit,
    onOpenMaps: (Double, Double, String) -> Unit,
    showToast: (String) -> Unit
) {
    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var loading by remember { mutableStateOf(true) }
    var processing by remember { mutableStateOf(false) }
    var showApproveConfirm by remember { mutableStateOf(false) }
    var showRejectConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                val res = ApiClient.service.getWedding(weddingId)
                if (res.isSuccessful) wedding = res.body()
                else showToast("No se pudo cargar el evento")
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(weddingId) { load() }

    fun approve(action: String) {
        processing = true
        scope.launch {
            try {
                val res = ApiClient.service.approveWedding(weddingId, ApproveRequest(action))
                if (res.isSuccessful) {
                    showToast(if (action == "approve") "Evento aprobado" else "Devuelto al couple")
                    load()
                } else {
                    showToast("No se pudo procesar")
                }
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                processing = false
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar("Detalle del evento", onBack = onBack) }
    ) { padding ->
        when {
            loading -> LoadingIndicator()
            wedding == null -> EmptyState("Evento no encontrado")
            else -> {
                val w = wedding!!
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // Encabezado
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${w.groomName} & ${w.brideName}",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${w.weddingDate ?: ""} · ${w.weddingTime ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Sand
                            )
                        }
                        StatusBadge(w.status)
                    }
                    Spacer(Modifier.height(20.dp))

                    // ─── Foto del venue ──────────────────────
                    VenuePhotoBox(
                        photoUrl = w.venuePhotoUrl,
                        onTakePhoto = onCamera
                    )
                    Spacer(Modifier.height(16.dp))

                    // ─── Información del lugar ───────────────
                    PacemCard {
                        SectionLabel("Lugar de la ceremonia")
                        Text(
                            w.venueName ?: "Por definir",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            w.venueAddress ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sand
                        )
                        if (w.venueLat != null && w.venueLng != null) {
                            Spacer(Modifier.height(10.dp))
                            OutlineGoldButton("Abrir en Maps") {
                                onOpenMaps(w.venueLat, w.venueLng, w.venueName ?: "Lugar")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // ─── Wedding planner ─────────────────────
                    PacemCard {
                        SectionLabel("Wedding planner")
                        Text(
                            w.plannerName ?: "Sin planner asignado",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlineGoldButton(
                            if (w.plannerName != null) "Cambiar planner" else "Asignar planner",
                            onClick = onAssignPlanner
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // ─── Precio ──────────────────────────────
                    PacemCard {
                        SectionLabel("Inversión")
                        Text(
                            "S/. ${"%.2f".format(w.totalPrice ?: 0.0)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Gold
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    // ─── Acciones ────────────────────────────
                    SectionLabel("Acciones")

                    if (w.status in listOf("DRAFT", "SUBMITTED")) {
                        GoldButton(
                            text = if (processing) "Procesando..." else "Aprobar ensamble",
                            enabled = !processing,
                            onClick = { showApproveConfirm = true }
                        )
                        Spacer(Modifier.height(10.dp))
                        DangerOutlineButton(
                            "Devolver al couple",
                            onClick = { showRejectConfirm = true }
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    OutlineGoldButton("Ver contrato", onClick = onContract)
                }
            }
        }
    }

    if (showApproveConfirm) {
        ConfirmDialog(
            title = "Aprobar ensamble",
            message = "El evento pasará a estado APROBADO y los novios recibirán una notificación.",
            confirmLabel = "Aprobar",
            onConfirm = {
                showApproveConfirm = false
                approve("approve")
            },
            onDismiss = { showApproveConfirm = false }
        )
    }
    if (showRejectConfirm) {
        ConfirmDialog(
            title = "Devolver al couple",
            message = "El evento volverá a borrador para que los novios lo ajusten.",
            confirmLabel = "Devolver",
            destructive = true,
            onConfirm = {
                showRejectConfirm = false
                approve("reject")
            },
            onDismiss = { showRejectConfirm = false }
        )
    }
}

/**
 * Placeholder visual de la foto del venue.
 * Si hay foto subida muestra un placeholder gráfico marcando que existe;
 * si no hay foto, invita al admin a tomar una con el botón de cámara.
 */
@Composable
private fun VenuePhotoBox(photoUrl: String?, onTakePhoto: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (photoUrl != null) StatusApprovedBg else NavBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                null,
                tint = if (photoUrl != null) Green else Sand,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (photoUrl != null) "Foto del venue registrada"
                else "Aún no se ha tomado foto del lugar",
                style = MaterialTheme.typography.bodyMedium,
                color = if (photoUrl != null) Green else Sand,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onTakePhoto,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
            ) {
                Text(if (photoUrl != null) "Volver a tomar" else "Tomar foto")
            }
        }
    }
}
