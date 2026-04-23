package com.pacemdeus.bodas.ui.coordinator

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Aprobación de Eventos (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Lista filtrada de eventos en DRAFT o SUBMITTED. El coordinador
// puede aprobar o devolver, con confirmación antes de cada acción.
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.ApproveRequest
import com.pacemdeus.bodas.data.api.models.Wedding
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.auth.LoginActivity
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class ApproveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                ApproveScreen(
                    onBack = { finish() },
                    onNavigate = { dest, _ ->
                        val intent = when (dest) {
                            "events" -> Intent(this, CoordinatorHomeActivity::class.java)
                            "map" -> Intent(this, MapScreenActivity::class.java)
                            else -> null
                        }
                        intent?.let {
                            startActivity(it)
                            finish()
                        }
                    },
                    onLogout = {
                        SessionManager.logout()
                        startActivity(Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproveScreen(
    onBack: () -> Unit,
    onNavigate: (String, String?) -> Unit,
    onLogout: () -> Unit,
    showToast: (String) -> Unit
) {
    var weddings by remember { mutableStateOf(listOf<Wedding>()) }
    var loading by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<Pair<Wedding, String>?>(null) }  // (boda, "approve"|"reject")
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                val res = ApiClient.service.getWeddings()
                if (res.isSuccessful) {
                    weddings = (res.body() ?: emptyList())
                        .filter { it.status in listOf("DRAFT", "SUBMITTED") }
                }
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = { PacemTopBar("Aprobar eventos", onBack = onBack, onLogout = onLogout) },
        bottomBar = { CoordinatorBottomNav(current = "approve", onNavigate = onNavigate) }
    ) { padding ->
        when {
            loading -> LoadingIndicator()
            weddings.isEmpty() -> EmptyState("No hay eventos pendientes de aprobación")
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(weddings) { w ->
                    PacemCard {
                        Text(
                            "${w.groomName} & ${w.brideName}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${w.weddingDate ?: ""} · ${w.venueName ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sand
                        )
                        Spacer(Modifier.height(8.dp))
                        StatusBadge(w.status)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pending = w to "approve" },
                                colors = ButtonDefaults.buttonColors(containerColor = Green),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Aprobar") }
                            OutlinedButton(
                                onClick = { pending = w to "reject" },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                            ) { Text("Devolver") }
                        }
                    }
                }
            }
        }
    }

    // ─── Diálogo de confirmación ────────────────────────────
    pending?.let { (w, action) ->
        val isApprove = action == "approve"
        ConfirmDialog(
            title = if (isApprove) "Aprobar ensamble" else "Devolver al couple",
            message = if (isApprove)
                "Vas a aprobar el ensamble de ${w.groomName} & ${w.brideName}. " +
                        "Los novios recibirán una notificación."
            else
                "El ensamble volverá a borrador para que los novios lo ajusten.",
            confirmLabel = if (isApprove) "Aprobar" else "Devolver",
            destructive = !isApprove,
            onConfirm = {
                pending = null
                scope.launch {
                    try {
                        val res = ApiClient.service.approveWedding(w.id, ApproveRequest(action))
                        if (res.isSuccessful) {
                            showToast(if (isApprove) "Aprobado" else "Devuelto")
                            load()
                        } else {
                            showToast("No se pudo procesar")
                        }
                    } catch (_: Exception) {
                        showToast("Error")
                    }
                }
            },
            onDismiss = { pending = null }
        )
    }
}
