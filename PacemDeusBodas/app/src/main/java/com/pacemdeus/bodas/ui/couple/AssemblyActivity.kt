package com.pacemdeus.bodas.ui.couple

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Ensamble Musical Timeline (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Timeline de los 14 momentos litúrgicos. El couple selecciona
// una canción para cada momento. Abajo un panel muestra el precio
// actualizado y dos acciones: elegir instrumentos / enviar al coro.
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.*
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.auth.LoginActivity
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class AssemblyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                AssemblyScreen(
                    onBack = { finish() },
                    onNavigate = { dest, _ ->
                        when (dest) {
                            "home" -> {
                                startActivity(Intent(this, CoupleHomeActivity::class.java))
                                finish()
                            }
                            "setlist" -> startActivity(Intent(this, SetlistActivity::class.java))
                            "instruments" -> startActivity(Intent(this, InstrumentsActivity::class.java))
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

/** Datos de un momento con su canción asignada (si la tiene) */
data class MomentRow(
    val moment: LiturgicalMoment,
    val songTitle: String? = null,
    val songAuthor: String? = null,
    val setlistItemId: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssemblyScreen(
    onBack: () -> Unit,
    onNavigate: (String, String?) -> Unit,
    onLogout: () -> Unit,
    showToast: (String) -> Unit
) {
    var moments by remember { mutableStateOf(listOf<LiturgicalMoment>()) }
    var setlistMap by remember { mutableStateOf(mapOf<String, SetlistItem>()) }
    var totalPrice by remember { mutableStateOf(0.0) }
    var wedStatus by remember { mutableStateOf("DRAFT") }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var pickerMoment by remember { mutableStateOf<LiturgicalMoment?>(null) }
    var pickerSongs by remember { mutableStateOf(listOf<Song>()) }
    var changeMoment by remember { mutableStateOf<MomentRow?>(null) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val weddingId = SessionManager.getWeddingId()
    val isEditable = wedStatus in listOf("DRAFT", "SUBMITTED")

    fun loadData() {
        scope.launch {
            try {
                val mRes = ApiClient.service.getMoments()
                if (mRes.isSuccessful) moments = mRes.body() ?: emptyList()
                if (weddingId != null) {
                    val sRes = ApiClient.service.getSetlist(weddingId)
                    if (sRes.isSuccessful && sRes.body() != null) {
                        setlistMap = sRes.body()!!.associateBy { it.momentId ?: "" }
                    }
                    // Traer precio + estado actualizados
                    val wRes = ApiClient.service.getWeddings()
                    if (wRes.isSuccessful) {
                        wRes.body()?.firstOrNull()?.let {
                            totalPrice = it.totalPrice ?: 0.0
                            wedStatus = it.status
                        }
                    }
                }
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    val rows = moments.map { m ->
        val item = setlistMap[m.id]
        MomentRow(m, item?.songTitle, item?.songAuthor, item?.id)
    }
    val filled = setlistMap.size
    val canSubmit = wedStatus == "DRAFT" && filled > 0

    Scaffold(
        topBar = {
            PacemTopBar(
                SessionManager.getDisplayName() ?: "Ensamble",
                onBack = onBack,
                onLogout = onLogout
            )
        },
        bottomBar = { CoupleBottomNav("assembly", onNavigate) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // ─── Progreso ──────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MOMENTOS DE LA CEREMONIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$filled / ${moments.size}",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            LinearProgressIndicator(
                progress = { if (moments.isNotEmpty()) filled.toFloat() / moments.size else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 20.dp),
                color = Gold,
                trackColor = Divider
            )
            Spacer(Modifier.height(8.dp))

            if (loading) {
                LoadingIndicator()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(rows) { index, row ->
                        MomentTimelineItem(
                            index = index + 1,
                            row = row,
                            editable = isEditable,
                            onClick = {
                                if (row.songTitle != null) {
                                    changeMoment = row
                                } else {
                                    scope.launch {
                                        try {
                                            val res = ApiClient.service.getSongs(row.moment.id)
                                            if (res.isSuccessful && !res.body().isNullOrEmpty()) {
                                                pickerSongs = res.body()!!
                                                pickerMoment = row.moment
                                            } else {
                                                showToast("No hay canciones para ${row.moment.name}")
                                            }
                                        } catch (_: Exception) {
                                            showToast("Error de conexión")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // ─── Panel de precio + acciones ─────────
                PricePanel(
                    totalPrice = totalPrice,
                    editable = isEditable,
                    canSubmit = canSubmit,
                    submitting = submitting,
                    onEditInstruments = { onNavigate("instruments", null) },
                    onSubmit = { showSubmitConfirm = true }
                )
            }
        }
    }

    // ─── Diálogo: seleccionar canción ───────────────────────
    if (pickerMoment != null) {
        AlertDialog(
            onDismissRequest = { pickerMoment = null },
            title = {
                Text(
                    pickerMoment!!.name,
                    style = MaterialTheme.typography.headlineMedium
                )
            },
            text = {
                Column {
                    Text(
                        "Selecciona una canción",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sand
                    )
                    Spacer(Modifier.height(12.dp))
                    pickerSongs.forEach { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        try {
                                            val res = ApiClient.service.addToSetlist(
                                                weddingId!!,
                                                AddToSetlistRequest(song.id, pickerMoment!!.id)
                                            )
                                            if (res.isSuccessful) {
                                                showToast("✓ ${song.title}")
                                                pickerMoment = null
                                                loading = true
                                                loadData()
                                            } else {
                                                showToast("No se pudo agregar")
                                            }
                                        } catch (_: Exception) {
                                            showToast("Error")
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    song.author ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand
                                )
                            }
                        }
                        HorizontalDivider(color = Divider)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerMoment = null }) {
                    Text("Cancelar", color = Gold)
                }
            }
        )
    }

    // ─── Diálogo: cambiar/quitar canción ────────────────────
    if (changeMoment != null) {
        AlertDialog(
            onDismissRequest = { changeMoment = null },
            title = { Text(changeMoment!!.moment.name) },
            text = { Text("Canción actual: ${changeMoment!!.songTitle}") },
            confirmButton = {
                TextButton(onClick = {
                    val cm = changeMoment!!
                    changeMoment = null
                    scope.launch {
                        try {
                            ApiClient.service.removeFromSetlist(weddingId!!, cm.setlistItemId!!)
                            val res = ApiClient.service.getSongs(cm.moment.id)
                            if (res.isSuccessful && !res.body().isNullOrEmpty()) {
                                pickerSongs = res.body()!!
                                pickerMoment = cm.moment
                            }
                        } catch (_: Exception) {
                            showToast("Error")
                        }
                    }
                }) { Text("Cambiar", color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val cm = changeMoment!!
                    changeMoment = null
                    scope.launch {
                        try {
                            ApiClient.service.removeFromSetlist(weddingId!!, cm.setlistItemId!!)
                            showToast("Canción quitada")
                            loading = true
                            loadData()
                        } catch (_: Exception) {
                            showToast("Error")
                        }
                    }
                }) { Text("Quitar", color = Red) }
            }
        )
    }

    // ─── Diálogo: confirmar envío al coro ───────────────────
    if (showSubmitConfirm) {
        ConfirmDialog(
            title = "Enviar al coro",
            message = "Tu ensamble será revisado por el coordinador. " +
                    "Podrás seguir editando hasta que sea aprobado.",
            confirmLabel = "Enviar",
            onConfirm = {
                showSubmitConfirm = false
                submitting = true
                scope.launch {
                    try {
                        val res = ApiClient.service.submitWedding(weddingId!!)
                        if (res.isSuccessful) {
                            showToast("Ensamble enviado al coro")
                            loadData()
                        } else {
                            showToast("No se pudo enviar")
                        }
                    } catch (_: Exception) {
                        showToast("Error de conexión")
                    } finally {
                        submitting = false
                    }
                }
            },
            onDismiss = { showSubmitConfirm = false }
        )
    }
}

/**
 * Panel sticky abajo del timeline: muestra el precio total actualizado
 * y dos acciones: elegir instrumentos y enviar al coro.
 */
@Composable
private fun PricePanel(
    totalPrice: Double,
    editable: Boolean,
    canSubmit: Boolean,
    submitting: Boolean,
    onEditInstruments: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NavBg,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "INVERSIÓN TOTAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Sand,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "S/. ${"%.2f".format(totalPrice)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Brown
                    )
                }
                if (editable) {
                    TextButton(onClick = onEditInstruments) {
                        Text("Elegir instrumentos", color = Gold)
                    }
                }
            }
            if (editable && canSubmit) {
                Spacer(Modifier.height(8.dp))
                GoldButton(
                    text = if (submitting) "Enviando..." else "Enviar al coro",
                    enabled = !submitting,
                    onClick = onSubmit
                )
            }
        }
    }
}

/** Item individual del timeline */
@Composable
fun MomentTimelineItem(
    index: Int,
    row: MomentRow,
    editable: Boolean,
    onClick: () -> Unit
) {
    val hasSong = row.songTitle != null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = editable) { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (hasSong) StatusApprovedBg else NavBg,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$index",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasSong) Green else Sand
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.moment.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (hasSong) "${row.songTitle} — ${row.songAuthor ?: ""}"
                    else if (editable) "Tocar para seleccionar" else "Sin canción",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasSong) BrownLight else Sand
                )
            }
            Text(
                if (hasSong) "✓" else if (editable) "＋" else "—",
                fontSize = 18.sp,
                color = if (hasSong) Green else if (editable) Gold else Sand
            )
        }
    }
}
