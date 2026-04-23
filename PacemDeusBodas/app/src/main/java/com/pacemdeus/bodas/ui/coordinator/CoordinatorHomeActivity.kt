package com.pacemdeus.bodas.ui.coordinator

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Home del Coordinador (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Punto de entrada para el ADMIN. Muestra la lista de eventos y
// ofrece tres tabs en el bottom navigation:
//   - Eventos: esta misma lista
//   - Mapa:    pantalla con los marcadores (placeholder)
//   - Aprobar: eventos pendientes de aprobación
// Cada card es tappable y lleva al detalle del evento.
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.Wedding
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.auth.LoginActivity
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class CoordinatorHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                CoordinatorHomeScreen(
                    onNavigate = { dest, extra ->
                        val intent = when (dest) {
                            "map" -> Intent(this, MapScreenActivity::class.java)
                            "approve" -> Intent(this, ApproveActivity::class.java)
                            "detail" -> Intent(this, WeddingDetailActivity::class.java).apply {
                                putExtra("weddingId", extra)
                            }
                            else -> null
                        }
                        intent?.let { startActivity(it) }
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
fun CoordinatorHomeScreen(
    onNavigate: (String, String?) -> Unit,
    onLogout: () -> Unit,
    showToast: (String) -> Unit
) {
    var weddings by remember { mutableStateOf(listOf<Wedding>()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = ApiClient.service.getWeddings()
                if (res.isSuccessful) weddings = res.body() ?: emptyList()
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar("Coordinador General", onLogout = onLogout) },
        bottomBar = { CoordinatorBottomNav(current = "events", onNavigate = onNavigate) }
    ) { padding ->
        when {
            loading -> LoadingIndicator()
            weddings.isEmpty() -> EmptyState("No hay eventos registrados")
            else -> {
                Column(Modifier.padding(padding)) {
                    // ─── Resumen arriba ────────────────
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            label = "Total",
                            value = weddings.size.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Pendientes",
                            value = weddings.count {
                                it.status in listOf("DRAFT", "SUBMITTED")
                            }.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ─── Lista de eventos ──────────────
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(weddings) { w ->
                            CoordinatorWeddingCard(w) {
                                onNavigate("detail", w.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Card tappable con resumen del evento */
@Composable
fun CoordinatorWeddingCard(wedding: Wedding, onClick: () -> Unit) {
    PacemCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${wedding.groomName} & ${wedding.brideName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${wedding.weddingDate ?: ""} · ${wedding.weddingTime ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sand
                )
                Text(
                    wedding.venueName ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrownLight
                )
                wedding.plannerName?.let {
                    Text(
                        "Planner: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = Sand
                    )
                }
            }
            StatusBadge(wedding.status)
        }
    }
}

/** Bottom navigation compartido por las 3 pantallas del coordinador */
@Composable
fun CoordinatorBottomNav(current: String, onNavigate: (String, String?) -> Unit) {
    NavigationBar(containerColor = NavBg) {
        NavigationBarItem(
            selected = current == "events",
            onClick = { if (current != "events") onNavigate("events", null) },
            icon = { Icon(Icons.Default.EventNote, "Eventos") },
            label = { Text("Eventos") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = Gold20
            )
        )
        NavigationBarItem(
            selected = current == "map",
            onClick = { if (current != "map") onNavigate("map", null) },
            icon = { Icon(Icons.Default.Map, "Mapa") },
            label = { Text("Mapa") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = Gold20
            )
        )
        NavigationBarItem(
            selected = current == "approve",
            onClick = { if (current != "approve") onNavigate("approve", null) },
            icon = { Icon(Icons.Default.CheckCircle, "Aprobar") },
            label = { Text("Aprobar") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = Gold20
            )
        )
    }
}
