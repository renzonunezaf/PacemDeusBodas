package com.pacemdeus.bodas.ui.couple

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Crear / Editar Evento (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Formulario reutilizado para dos casos:
//   - Crear evento (extra "weddingId" ausente): POST /weddings
//   - Editar evento (extra "weddingId" presente): PATCH /weddings/{id}
// Campos: fecha, hora, nombre del lugar, dirección.
// ═══════════════════════════════════════════════════════════════

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.WeddingUpsertRequest
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class CreateEditWeddingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weddingId = intent.getStringExtra("weddingId")  // null → crear; presente → editar
        setContent {
            PacemDeusTheme {
                CreateEditWeddingScreen(
                    weddingId = weddingId,
                    onBack = { finish() },
                    onDone = { finish() },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditWeddingScreen(
    weddingId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    showToast: (String) -> Unit
) {
    val isEdit = weddingId != null
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var venueName by remember { mutableStateOf("") }
    var venueAddress by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Si es edición, precargar los datos actuales
    LaunchedEffect(weddingId) {
        if (weddingId == null) return@LaunchedEffect
        try {
            val res = ApiClient.service.getWedding(weddingId)
            if (res.isSuccessful) {
                res.body()?.let {
                    date = it.weddingDate ?: ""
                    time = it.weddingTime ?: ""
                    venueName = it.venueName ?: ""
                    venueAddress = it.venueAddress ?: ""
                }
            }
        } catch (_: Exception) {
            showToast("Error al cargar datos")
        }
    }

    fun save() {
        if (date.isBlank() || time.isBlank() || venueName.isBlank() || venueAddress.isBlank()) {
            showToast("Completa todos los campos")
            return
        }
        saving = true
        scope.launch {
            try {
                val req = WeddingUpsertRequest(date, time, venueName, venueAddress)
                val res = if (isEdit) {
                    ApiClient.service.updateWedding(weddingId!!, req)
                } else {
                    ApiClient.service.createWedding(req)
                }
                if (res.isSuccessful) {
                    showToast(if (isEdit) "Evento actualizado" else "Evento creado")
                    onDone()
                } else {
                    showToast("No se pudo guardar (${res.code()})")
                }
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            PacemTopBar(
                if (isEdit) "Editar evento" else "Nuevo evento",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionLabel("Datos de tu ceremonia")

            Text(
                "Indica cuándo y dónde será tu boda. Puedes editar estos datos mientras tu evento esté en borrador.",
                style = MaterialTheme.typography.bodyMedium,
                color = Sand,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))

            PacemTextField(
                value = date,
                onValueChange = { date = it },
                label = "Fecha (YYYY-MM-DD)",
                keyboardType = KeyboardType.Number
            )
            Spacer(Modifier.height(12.dp))

            PacemTextField(
                value = time,
                onValueChange = { time = it },
                label = "Hora (HH:mm, formato 24h)",
                keyboardType = KeyboardType.Number
            )
            Spacer(Modifier.height(12.dp))

            PacemTextField(
                value = venueName,
                onValueChange = { venueName = it },
                label = "Nombre del lugar"
            )
            Spacer(Modifier.height(12.dp))

            PacemTextField(
                value = venueAddress,
                onValueChange = { venueAddress = it },
                label = "Dirección completa",
                singleLine = false
            )
            Spacer(Modifier.height(24.dp))

            GoldButton(
                text = if (saving) "Guardando..." else if (isEdit) "Guardar cambios" else "Crear evento",
                enabled = !saving,
                onClick = { save() }
            )
            Spacer(Modifier.height(12.dp))
            OutlineGoldButton("Cancelar", onClick = onBack)
        }
    }
}
