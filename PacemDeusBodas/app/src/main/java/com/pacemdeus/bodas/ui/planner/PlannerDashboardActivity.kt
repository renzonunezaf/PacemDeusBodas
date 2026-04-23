package com.pacemdeus.bodas.ui.planner

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Dashboard Wedding Planner (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

class PlannerDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PacemDeusTheme { PlannerDashboardScreen(
            onWeddingClick = { w ->
                startActivity(Intent(this, PlannerDetailActivity::class.java).apply {
                    putExtra("weddingId", w.id)
                    putExtra("coupleName", "${w.groomName} & ${w.brideName}")
                    putExtra("date", w.weddingDate ?: ""); putExtra("venue", w.venueName ?: "")
                    putExtra("address", w.venueAddress ?: ""); putExtra("price", w.totalPrice ?: 0.0)
                    putExtra("status", w.status); putExtra("lat", w.venueLat ?: 0.0); putExtra("lng", w.venueLng ?: 0.0)
                })
            },
            onLogout = { SessionManager.logout(); startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }); finish() },
            showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        )}}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDashboardScreen(onWeddingClick: (Wedding) -> Unit, onLogout: () -> Unit, showToast: (String) -> Unit) {
    var weddings by remember { mutableStateOf(listOf<Wedding>()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { scope.launch {
        try { val res = ApiClient.service.getPlannerWeddings(); if (res.isSuccessful) weddings = res.body() ?: emptyList() }
        catch (_: Exception) { showToast("Error de conexión") }; loading = false
    }}

    Scaffold(topBar = { PacemTopBar(SessionManager.getDisplayName() ?: "Wedding Planner", onLogout = onLogout) }) { padding ->
        Column(Modifier.padding(padding)) {
            // Contadores
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total", "${weddings.size}", Modifier.weight(1f))
                StatCard("Activos", "${weddings.count { it.status in listOf("DRAFT","SUBMITTED","APPROVED") }}", Modifier.weight(1f))
            }

            if (loading) LoadingIndicator()
            else if (weddings.isEmpty()) EmptyState("No tienes eventos asignados")
            else LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(weddings) { w ->
                    PacemCard(onClick = { onWeddingClick(w) }) {
                        Text("${w.groomName} & ${w.brideName}", style = MaterialTheme.typography.titleMedium)
                        Text("${w.weddingDate ?: ""} · ${w.venueName ?: ""}", style = MaterialTheme.typography.bodyMedium, color = Sand)
                        Spacer(Modifier.height(8.dp))
                        StatusBadge(w.status)
                    }
                }
            }
        }
    }
}
