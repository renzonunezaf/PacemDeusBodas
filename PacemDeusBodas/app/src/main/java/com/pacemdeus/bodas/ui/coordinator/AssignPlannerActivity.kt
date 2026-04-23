package com.pacemdeus.bodas.ui.coordinator

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Asignar Wedding Planner (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Lista de wedding planners disponibles. El admin selecciona uno
// y confirma la asignación al evento recibido por intent.
// ═══════════════════════════════════════════════════════════════

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.AssignPlannerRequest
import com.pacemdeus.bodas.data.api.models.WeddingPlannerItem
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class AssignPlannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weddingId = intent.getStringExtra("weddingId") ?: ""
        setContent {
            PacemDeusTheme {
                AssignPlannerScreen(
                    weddingId = weddingId,
                    onBack = { finish() },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignPlannerScreen(
    weddingId: String,
    onBack: () -> Unit,
    showToast: (String) -> Unit
) {
    var planners by remember { mutableStateOf(listOf<WeddingPlannerItem>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = ApiClient.service.getWeddingPlanners()
                if (res.isSuccessful) planners = res.body() ?: emptyList()
            } catch (_: Exception) {
                showToast("Error al cargar planners")
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar("Asignar planner", onBack = onBack) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when {
                loading -> LoadingIndicator()
                planners.isEmpty() -> EmptyState("No hay wedding planners registrados")
                else -> LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(planners) { p ->
                        PacemCard(onClick = { selected = p.id }) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected == p.id) Gold else Brown
                            )
                            Text(
                                "${p.company ?: "Freelance"} · ${p.weddingCount} evento(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Sand
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        GoldButton(
                            text = if (saving) "Asignando..." else "Confirmar asignación",
                            enabled = selected != null && !saving,
                            onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        val res = ApiClient.service.assignPlanner(
                                            weddingId,
                                            AssignPlannerRequest(selected!!)
                                        )
                                        if (res.isSuccessful) {
                                            showToast("Planner asignado")
                                            onBack()
                                        } else {
                                            showToast("No se pudo asignar")
                                        }
                                    } catch (_: Exception) {
                                        showToast("Error")
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
