package com.pacemdeus.bodas.ui.couple

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Setlist (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.SetlistItem
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.auth.LoginActivity
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class SetlistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                SetlistScreen(
                    onBack = { finish() },
                    onNavigate = { dest, _ ->
                        when (dest) {
                            "home" -> { startActivity(Intent(this, CoupleHomeActivity::class.java)); finish() }
                            "assembly" -> { startActivity(Intent(this, AssemblyActivity::class.java)); finish() }
                        }
                    },
                    onLogout = {
                        SessionManager.logout()
                        startActivity(Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }); finish()
                    },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistScreen(
    onBack: () -> Unit,
    onNavigate: (String, String?) -> Unit,
    onLogout: () -> Unit,
    showToast: (String) -> Unit
) {
    var items by remember { mutableStateOf(listOf<SetlistItem>()) }
    var loading by remember { mutableStateOf(true) }
    var deleteItem by remember { mutableStateOf<SetlistItem?>(null) }
    val scope = rememberCoroutineScope()
    val weddingId = SessionManager.getWeddingId()

    fun loadData() {
        scope.launch {
            try {
                if (weddingId != null) {
                    val res = ApiClient.service.getSetlist(weddingId)
                    if (res.isSuccessful) items = res.body() ?: emptyList()
                }
            } catch (_: Exception) { showToast("Error de conexión") }
            finally { loading = false }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    val filled = items.map { it.momentSlug }.distinct().size
    val missing = 14 - filled

    Scaffold(
        topBar = { PacemTopBar("Mi Setlist", onBack = onBack, onLogout = onLogout) },
        bottomBar = { CoupleBottomNav("setlist", onNavigate) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Contadores
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${items.size} canciones en $filled momentos", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (missing > 0) "Faltan $missing" else "Completo ✓",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (missing > 0) Amber else Green
                )
            }
            HorizontalDivider(color = Divider)

            if (loading) {
                LoadingIndicator()
            } else if (items.isEmpty()) {
                EmptyState("Aún no has seleccionado canciones.\nVe a Ensamble para armar tu ceremonia.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = White),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${index + 1}. ${item.momentName.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Gold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.songTitle, style = MaterialTheme.typography.bodyLarge)
                                    Text(item.songAuthor ?: "", style = MaterialTheme.typography.bodyMedium, color = Sand)
                                }
                                IconButton(onClick = { deleteItem = item }) {
                                    Icon(Icons.Default.Delete, "Quitar", tint = Red.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para eliminar
    if (deleteItem != null) {
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Quitar canción") },
            text = { Text("¿Quitar \"${deleteItem!!.songTitle}\" del momento ${deleteItem!!.momentName}?") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteItem!!.id; deleteItem = null
                    scope.launch {
                        try {
                            ApiClient.service.removeFromSetlist(weddingId!!, id)
                            showToast("Canción quitada"); loading = true; loadData()
                        } catch (_: Exception) { showToast("Error") }
                    }
                }) { Text("Quitar", color = Red) }
            },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Cancelar") } }
        )
    }
}
