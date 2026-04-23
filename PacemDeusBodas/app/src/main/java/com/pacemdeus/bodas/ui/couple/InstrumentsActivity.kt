package com.pacemdeus.bodas.ui.couple

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Elegir Instrumentos (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Lista de instrumentos disponibles con checkboxes.
// El precio total se recalcula en vivo al marcar/desmarcar.
// Al guardar: POST /weddings/{id}/instruments y vuelve atrás.
// ═══════════════════════════════════════════════════════════════

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.Instrument
import com.pacemdeus.bodas.data.api.models.InstrumentsRequest
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class InstrumentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                InstrumentsScreen(
                    onBack = { finish() },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentsScreen(onBack: () -> Unit, showToast: (String) -> Unit) {
    var instruments by remember { mutableStateOf(listOf<Instrument>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val weddingId = SessionManager.getWeddingId()

    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.service.getInstruments()
            if (res.isSuccessful) instruments = res.body() ?: emptyList()
            // Precarga de selección actual: pendiente enriquecer el modelo Wedding
            // para traer la lista de instruments ya contratados. Por ahora arranca
            // en vacío y el couple reemplaza su selección al guardar.
        } catch (_: Exception) {
            showToast("Error al cargar instrumentos")
        } finally {
            loading = false
        }
    }

    val totalExtra = instruments.filter { it.id in selected }.sumOf { it.priceLima }

    fun save() {
        if (weddingId == null) {
            showToast("No tienes un evento activo")
            return
        }
        saving = true
        scope.launch {
            try {
                val res = ApiClient.service.setInstruments(
                    weddingId,
                    InstrumentsRequest(selected.toList())
                )
                if (res.isSuccessful) {
                    showToast("Instrumentos actualizados")
                    onBack()
                } else {
                    showToast("No se pudo guardar")
                }
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar("Elegir instrumentos", onBack = onBack) },
        bottomBar = {
            Surface(color = NavBg, shadowElevation = 6.dp) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "INSTRUMENTOS EXTRA",
                                style = MaterialTheme.typography.labelSmall,
                                color = Sand,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "S/. ${"%.2f".format(totalExtra)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Brown
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GoldButton(
                        text = if (saving) "Guardando..." else "Guardar selección",
                        enabled = !saving,
                        onClick = { save() }
                    )
                }
            }
        }
    ) { padding ->
        if (loading) {
            LoadingIndicator()
        } else if (instruments.isEmpty()) {
            EmptyState("No hay instrumentos registrados")
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Los novios pueden personalizar el ensamble con instrumentos adicionales. " +
                        "El precio se actualiza automáticamente abajo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sand,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(instruments) { inst ->
                    val isSelected = inst.id in selected
                    PacemCard(
                        onClick = {
                            selected = if (isSelected) selected - inst.id else selected + inst.id
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selected = if (isSelected) selected - inst.id else selected + inst.id
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Gold)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${inst.icon ?: "🎵"}  ${inst.name}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "S/. ${"%.2f".format(inst.priceLima)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
