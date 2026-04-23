package com.pacemdeus.bodas.ui.planner

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Detalle de Evento (Wedding Planner)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Vista read-only para el wedding planner con los datos del evento
// asignado. Ofrece tres acciones:
//   - Abrir ubicación en Google Maps (Intent externo)
//   - Llamar al coordinador del coro (Intent.ACTION_DIAL)
//   - Ver contrato completo
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.couple.ContractActivity
import com.pacemdeus.bodas.ui.theme.*

// Teléfono del coordinador del Coro Pacem Deus (para HU-08).
// Sustituir por el real del coro cuando esté disponible.
private const val CHOIR_PHONE = "+51999000000"

class PlannerDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weddingId = intent.getStringExtra("weddingId") ?: ""
        val coupleName = intent.getStringExtra("coupleName") ?: ""
        val date = intent.getStringExtra("date") ?: ""
        val venue = intent.getStringExtra("venue") ?: ""
        val address = intent.getStringExtra("address") ?: ""
        val price = intent.getDoubleExtra("price", 0.0)
        val status = intent.getStringExtra("status") ?: ""
        val lat = intent.getDoubleExtra("lat", 0.0)
        val lng = intent.getDoubleExtra("lng", 0.0)

        setContent {
            PacemDeusTheme {
                DetailScreen(
                    couple = coupleName,
                    date = date,
                    venue = venue,
                    address = address,
                    price = price,
                    status = status,
                    onBack = { finish() },
                    onMaps = {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(venue)})")
                            )
                        )
                    },
                    onCall = {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$CHOIR_PHONE")))
                    },
                    onContract = {
                        startActivity(
                            Intent(this, ContractActivity::class.java)
                                .putExtra("weddingId", weddingId)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    couple: String,
    date: String,
    venue: String,
    address: String,
    price: Double,
    status: String,
    onBack: () -> Unit,
    onMaps: () -> Unit,
    onCall: () -> Unit,
    onContract: () -> Unit
) {
    Scaffold(
        topBar = { PacemTopBar("Detalle del evento", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(couple, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            PacemCard {
                SectionLabel("Información")
                Text("Fecha: $date", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("Lugar: $venue", style = MaterialTheme.typography.bodyLarge)
                Text(
                    address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sand
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Inversión: S/. ${"%.2f".format(price)}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                StatusBadge(status)
            }
            Spacer(Modifier.height(16.dp))

            // Dos acciones nativas en fila
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onMaps, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Map, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Mapa")
                }
                Button(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold)
                ) {
                    Icon(Icons.Default.Call, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Llamar")
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlineGoldButton("Ver contrato", onClick = onContract)
        }
    }
}
