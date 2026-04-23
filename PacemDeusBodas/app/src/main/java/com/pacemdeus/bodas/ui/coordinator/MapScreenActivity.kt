package com.pacemdeus.bodas.ui.coordinator

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Mapa de Eventos (Placeholder)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Placeholder visual del mapa de Lima con los marcadores de los
// eventos. Se usa Canvas para dibujar un mapa estilizado con pins
// dorados que reflejan la ubicación aproximada de cada venue.
// La implementación real con Google Maps SDK queda pendiente.
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.Wedding
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.auth.LoginActivity
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class MapScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                MapScreen(
                    onNavigate = { dest, extra ->
                        val intent = when (dest) {
                            "events" -> Intent(this, CoordinatorHomeActivity::class.java)
                            "approve" -> Intent(this, ApproveActivity::class.java)
                            "detail" -> Intent(this, WeddingDetailActivity::class.java).apply {
                                putExtra("weddingId", extra)
                            }
                            else -> null
                        }
                        intent?.let {
                            startActivity(it)
                            if (dest in listOf("events", "approve")) finish()
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
fun MapScreen(
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
                if (res.isSuccessful) {
                    // Solo eventos con coordenadas válidas
                    weddings = (res.body() ?: emptyList())
                        .filter { it.venueLat != null && it.venueLng != null }
                }
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar("Mapa de eventos", onLogout = onLogout) },
        bottomBar = { CoordinatorBottomNav(current = "map", onNavigate = onNavigate) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading) {
                LoadingIndicator()
            } else {
                // ─── Placeholder del mapa ─────────────────
                MapPlaceholder(
                    weddingCount = weddings.size,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                // ─── Lista de pins abajo ─────────────────
                Text(
                    "UBICACIONES",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (weddings.isEmpty()) {
                    EmptyState("No hay eventos con ubicación registrada")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(weddings) { w ->
                            MapPinCard(w) { onNavigate("detail", w.id) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mapa placeholder: superficie crema con un grid suave y pins dorados
 * distribuidos visualmente para sugerir la geografía de Lima.
 * Cuando se integre Google Maps SDK, este Composable se reemplaza por
 * un GoogleMap() con los mismos marcadores.
 */
@Composable
private fun MapPlaceholder(weddingCount: Int, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NavBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Grid suave
            val gridStep = 40f
            val gridColor = Sand.copy(alpha = 0.18f)
            var x = 0f
            while (x < w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), 0.5f)
                x += gridStep
            }
            var y = 0f
            while (y < h) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y), 0.5f)
                y += gridStep
            }

            // Línea costera sugerida (borde izquierdo curvo)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * 0.25f)
                cubicTo(
                    w * 0.12f, h * 0.35f,
                    w * 0.08f, h * 0.65f,
                    0f, h * 0.85f
                )
            }
            drawPath(path, Sand.copy(alpha = 0.4f), style = Stroke(width = 2f))

            // Pins dorados simulados
            val positions = listOf(
                Offset(w * 0.35f, h * 0.45f),
                Offset(w * 0.55f, h * 0.30f),
                Offset(w * 0.65f, h * 0.65f),
                Offset(w * 0.28f, h * 0.70f),
                Offset(w * 0.75f, h * 0.45f)
            )
            val visiblePins = minOf(positions.size, maxOf(weddingCount, 3))
            for (i in 0 until visiblePins) {
                val p = positions[i]
                drawCircle(Gold, radius = 12f, center = p)
                drawCircle(White, radius = 4f, center = p)
            }
        }

        // Etiqueta inferior
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            color = White.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                "Lima · $weddingCount eventos con ubicación",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Brown
            )
        }
    }
}

/** Card de un pin del mapa con el evento correspondiente */
@Composable
private fun MapPinCard(wedding: Wedding, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Place, null, tint = Gold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    wedding.venueName ?: "Sin nombre",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${wedding.groomName} & ${wedding.brideName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sand
                )
                Text(
                    "${wedding.venueLat?.let { "%.4f".format(it) }}, " +
                    "${wedding.venueLng?.let { "%.4f".format(it) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrownLight,
                    fontSize = 11.sp
                )
            }
        }
    }
}
