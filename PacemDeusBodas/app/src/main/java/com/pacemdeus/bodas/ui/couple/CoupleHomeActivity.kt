package com.pacemdeus.bodas.ui.couple

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Home del Couple (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Pantalla principal para los novios. Tiene tres estados:
//   1. Sin evento → CTA "Crear mi evento"
//   2. En DRAFT/SUBMITTED → editar, elegir instrumentos, enviar al coro
//   3. APPROVED/CONTRACTED → ver setlist y contrato
// El bottom navigation conecta con Assembly y Setlist.
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.BuildConfig
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.Wedding
import com.pacemdeus.bodas.data.api.models.CancelRequest
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.auth.LoginActivity
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class CoupleHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                CoupleHomeScreen(
                    onNavigate = { route, extra ->
                        val intent = when (route) {
                            "assembly" -> Intent(this, AssemblyActivity::class.java)
                            "setlist" -> Intent(this, SetlistActivity::class.java)
                            "create" -> Intent(this, CreateEditWeddingActivity::class.java)
                            "edit" -> Intent(this, CreateEditWeddingActivity::class.java).apply {
                                putExtra("weddingId", extra)
                            }
                            "contract" -> Intent(this, ContractActivity::class.java).apply {
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

/**
 * Pantalla principal del novio/a. Recibe tres callbacks:
 * - onNavigate(route, extra?): navegar a otra pantalla
 * - onLogout: cerrar sesión
 * - showToast: mostrar mensajes cortos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleHomeScreen(
    onNavigate: (String, String?) -> Unit,
    onLogout: () -> Unit,
    showToast: (String) -> Unit
) {
    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var setlistCount by remember { mutableStateOf(0) }
    var momentsFilled by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Recarga datos cada vez que la pantalla vuelve al foreground
    LaunchedEffect(Unit) {
        loadData(
            onLoading = { loading = it },
            onWedding = { wedding = it },
            onSetlist = { count, moments -> setlistCount = count; momentsFilled = moments },
            onError = showToast
        )
    }

    val hasWedding = wedding != null
    val status = wedding?.status
    val isEditable = status in listOf("DRAFT", "SUBMITTED")
    val canSubmit = status == "DRAFT" && setlistCount > 0
    val canViewContract = status in listOf("APPROVED", "CONTRACTED", "COMPLETED")
    val canCancel = status in listOf("DRAFT", "SUBMITTED", "APPROVED")

    Scaffold(
        topBar = { PacemTopBar("Pacem Deus Bodas", onLogout = onLogout) },
        bottomBar = { if (hasWedding) CoupleBottomNav("home", onNavigate) }
    ) { padding ->
        when {
            loading -> LoadingIndicator()
            !hasWedding -> EmptyWeddingView(Modifier.padding(padding), onCreate = { onNavigate("create", null) })
            else -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // ─── Saludo ──────────────────────────────
                    SectionLabel("Tu ceremonia")
                    Text(
                        SessionManager.getDisplayName() ?: "",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(20.dp))

                    // ─── Card: detalle del evento ────────────
                    PacemCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Detalle del evento")
                            Spacer(Modifier.weight(1f))
                            wedding?.status?.let { StatusBadge(it) }
                        }
                        Text(
                            "${wedding?.weddingDate ?: "Por definir"} · ${wedding?.weddingTime ?: ""}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            wedding?.venueName ?: "Lugar por definir",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            wedding?.venueAddress ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sand
                        )
                        if (isEditable) {
                            Spacer(Modifier.height(12.dp))
                            OutlineGoldButton(
                                "Editar datos del evento",
                                onClick = { onNavigate("edit", wedding?.id) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ─── Card: progreso del ensamble ────────
                    PacemCard {
                        SectionLabel("Ensamble musical")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (setlistCount == 0) "Aún no has seleccionado canciones"
                                else "$setlistCount canciones en $momentsFilled momentos",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text("$momentsFilled / 14", color = Gold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { momentsFilled / 14f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Gold,
                            trackColor = Divider
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // ─── Botones de acción según estado ─────
                    if (isEditable) {
                        GoldButton("Editar ensamble musical") { onNavigate("assembly", null) }
                        Spacer(Modifier.height(10.dp))
                        OutlineGoldButton("Ver mi setlist") { onNavigate("setlist", null) }

                        if (canSubmit) {
                            Spacer(Modifier.height(10.dp))
                            GoldButton(
                                text = if (submitting) "Enviando..." else "Enviar al coro",
                                enabled = !submitting
                            ) {
                                showSubmitConfirm = true
                            }
                        } else if (status == "SUBMITTED") {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Tu ensamble fue enviado al coro. " +
                                "Recibirás una notificación cuando sea revisado.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Sand,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    } else {
                        OutlineGoldButton("Ver mi setlist") { onNavigate("setlist", null) }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "El ensamble fue aprobado por el coordinador.\nSolo puedes visualizar tu setlist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sand,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }

                    if (canViewContract) {
                        Spacer(Modifier.height(10.dp))
                        OutlineGoldButton("Ver contrato") { onNavigate("contract", wedding?.id) }
                    }

                    if (canCancel) {
                        Spacer(Modifier.height(10.dp))
                        DangerOutlineButton("Solicitar cancelación") { showCancelConfirm = true }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        fontSize = 11.sp,
                        color = Sand,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // ─── Diálogos de confirmación ───────────────────────────
    if (showSubmitConfirm) {
        ConfirmDialog(
            title = "Enviar al coro",
            message = "Una vez enviado, el coro revisará tu ensamble. " +
                    "Podrás seguir editando hasta que sea aprobado.",
            confirmLabel = "Enviar",
            onConfirm = {
                showSubmitConfirm = false
                submitting = true
                scope.launch {
                    try {
                        val id = wedding?.id ?: return@launch
                        val res = ApiClient.service.submitWedding(id)
                        if (res.isSuccessful) {
                            showToast("Ensamble enviado al coro")
                            loadData(
                                onLoading = { loading = it },
                                onWedding = { wedding = it },
                                onSetlist = { c, m -> setlistCount = c; momentsFilled = m },
                                onError = showToast
                            )
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

    if (showCancelConfirm) {
        ConfirmDialog(
            title = "Solicitar cancelación",
            message = "¿Estás seguro de cancelar tu evento? " +
                    "El coordinador lo revisará y se comunicará contigo.",
            confirmLabel = "Sí, cancelar",
            destructive = true,
            onConfirm = {
                showCancelConfirm = false
                scope.launch {
                    try {
                        val id = wedding?.id ?: return@launch
                        val res = ApiClient.service.cancelWedding(id, CancelRequest(null))
                        if (res.isSuccessful) {
                            showToast("Solicitud registrada")
                            loadData(
                                onLoading = { loading = it },
                                onWedding = { wedding = it },
                                onSetlist = { c, m -> setlistCount = c; momentsFilled = m },
                                onError = showToast
                            )
                        } else {
                            showToast("No se pudo cancelar")
                        }
                    } catch (_: Exception) {
                        showToast("Error de conexión")
                    }
                }
            },
            onDismiss = { showCancelConfirm = false }
        )
    }
}

/** Estado vacío: el couple no tiene evento, se le invita a crearlo */
@Composable
private fun EmptyWeddingView(modifier: Modifier, onCreate: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("♡", fontSize = 48.sp, color = Gold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Bienvenidos al Coro Pacem Deus",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Para comenzar, crea el evento de tu ceremonia. " +
            "Podrás elegir los momentos musicales e instrumentos.",
            style = MaterialTheme.typography.bodyMedium,
            color = Sand,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(32.dp))
        GoldButton("Crear mi evento", onClick = onCreate)
    }
}

/**
 * Carga en paralelo el evento del couple y su setlist.
 * Extraída para poder reutilizar tras cada acción.
 */
private suspend fun loadData(
    onLoading: (Boolean) -> Unit,
    onWedding: (Wedding?) -> Unit,
    onSetlist: (Int, Int) -> Unit,
    onError: (String) -> Unit
) {
    onLoading(true)
    try {
        val res = ApiClient.service.getWeddings()
        val w = if (res.isSuccessful) res.body()?.firstOrNull() else null
        onWedding(w)
        if (w != null) {
            val sRes = ApiClient.service.getSetlist(w.id)
            if (sRes.isSuccessful) {
                val items = sRes.body().orEmpty()
                onSetlist(items.size, items.map { it.momentSlug }.distinct().size)
            }
        } else {
            onSetlist(0, 0)
        }
    } catch (_: Exception) {
        onError("Error de conexión")
    } finally {
        onLoading(false)
    }
}

/** Bottom navigation compartido por las 3 pantallas del couple */
@Composable
fun CoupleBottomNav(current: String, onNavigate: (String, String?) -> Unit) {
    NavigationBar(containerColor = NavBg) {
        NavigationBarItem(
            selected = current == "home",
            onClick = { if (current != "home") onNavigate("home", null) },
            icon = { Icon(Icons.Default.Home, "Inicio") },
            label = { Text("Inicio") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = Gold20
            )
        )
        NavigationBarItem(
            selected = current == "assembly",
            onClick = { if (current != "assembly") onNavigate("assembly", null) },
            icon = { Icon(Icons.Default.MusicNote, "Ensamble") },
            label = { Text("Ensamble") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = Gold20
            )
        )
        NavigationBarItem(
            selected = current == "setlist",
            onClick = { if (current != "setlist") onNavigate("setlist", null) },
            icon = { Icon(Icons.Default.LibraryMusic, "Setlist") },
            label = { Text("Setlist") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = Gold20
            )
        )
    }
}
