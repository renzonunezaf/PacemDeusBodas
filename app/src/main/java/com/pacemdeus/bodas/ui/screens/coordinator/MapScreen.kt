package com.pacemdeus.bodas.ui.screens.coordinator

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.pacemdeus.bodas.data.DemoData
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.Gold
import com.pacemdeus.bodas.ui.components.GoldSoft
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge

// Pantalla de mapa simulada. No se usa Google Maps SDK porque aun no se
// ha enseñado en el curso. En su lugar mostramos una lista de venues
// con coordenadas; al tocar uno se delega a la app de mapas del
// sistema con un Intent ACTION_VIEW + URI geo:.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    weddings: List<Wedding>,
    onOpenHome: () -> Unit = {},
    onOpenApprove: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val withCoords = weddings.filter { it.venueLat != null && it.venueLng != null }

    Scaffold(
        topBar = { PacemTopBar(title = "Mapa de eventos", onLogout = onLogout) },
        bottomBar = {
            CoordinatorBottomNav(
                current = CoordinatorTab.Map,
                onSelectEvents = onOpenHome,
                onSelectMap = {},
                onSelectApprove = onOpenApprove
            )
        },
        containerColor = Cream
    ) { padding ->

        if (withCoords.isEmpty()) {
            EmptyState("No hay eventos con ubicacion registrada")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            SectionLabel("Eventos en Lima")
            Text(
                "Toca cualquier ubicacion para abrirla en la app de mapas de tu telefono.",
                color = Sand,
                fontSize = 12.sp
            )
            Spacer(Modifier.padding(vertical = 8.dp))

            for (w in withCoords) {
                val couple = DemoData.couples.firstOrNull { it.id == w.coupleId }
                PacemCard(onClick = { onOpenDetail(w.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(GoldSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Gold
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                w.venueName,
                                color = Brown,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                w.venueAddress,
                                color = Sand,
                                fontSize = 12.sp
                            )
                            Text(
                                couple?.displayName() ?: "",
                                color = Brown,
                                fontSize = 12.sp
                            )
                        }
                        StatusBadge(w.status)
                    }
                    Spacer(Modifier.padding(vertical = 4.dp))

                    // Boton para abrir en Maps
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%.4f , %.4f".format(w.venueLat, w.venueLng),
                            color = Sand,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Abrir en Maps",
                            color = Gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                                .background(GoldSoft, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

/** Helper para construir la URI geo: que abre la app de mapas. */
fun buildGeoUri(lat: Double, lng: Double, label: String): String {
    val safe = label.replace(" ", "+")
    return "geo:$lat,$lng?q=$lat,$lng($safe)"
}

/** Lanza el Intent ACTION_VIEW para abrir la ubicacion en la app de mapas. */
fun openInMaps(context: android.content.Context, lat: Double, lng: Double, label: String) {
    val intent = Intent(Intent.ACTION_VIEW, buildGeoUri(lat, lng, label).toUri())
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MapScreenPreview() {
    MaterialTheme { MapScreen(weddings = DemoData.initialWeddings) }
}
