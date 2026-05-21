package com.pacemdeus.bodas.ui.screens.coordinator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla "Pendientes" del coordinador. Lista las bodas que requieren
// accion del coro:
//   - SUBMITTED: enviada por la novia, esperando aprobacion.
//   - RETURNED_WITH_NOTES: el coro ya devolvio con anotaciones y la
//     novia aun no responde. La dejamos visible para seguimiento.
//
// Tap en una card abre WeddingDetailScreen, donde el coordinador puede
// editar setlist/instrumentos/ubicacion y decidir aprobar o devolver
// con anotaciones.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproveScreen(
    onOpenHome: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var weddings by remember { mutableStateOf<List<Wedding>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        isLoading = true
        apiClient.listBodas { result ->
            isLoading = false
            when (result) {
                is ApiResult.Success -> weddings = result.data
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    // El admin solo aprueba/devuelve eventos SUBMITTED. Los que ya
    // devolvio (RETURNED_WITH_NOTES) estan del lado de la novia para
    // corregir; no deben aparecer aqui.
    val pending = weddings.filter {
        it.status == WeddingStatus.SUBMITTED
    }.sortedWith(compareBy({ it.weddingDate }, { it.weddingTime }))

    com.pacemdeus.bodas.ui.components.PacemDrawerScaffold(
        title = "Por aprobar",
        drawerItems = listOf(
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Eventos",
                icon = Icons.Default.EventNote,
                selected = false,
                onClick = onOpenHome
            ),
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Mapa",
                icon = Icons.Default.Map,
                selected = false,
                onClick = onOpenMap
            ),
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Por aprobar",
                icon = Icons.Default.AssignmentTurnedIn,
                selected = true,
                onClick = {}
            )
        ),
        onLogout = onLogout
    ) { padding ->

        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@PacemDrawerScaffold
        }

        if (pending.isEmpty()) {
            EmptyState(errorMessage ?: "No hay eventos pendientes")
            return@PacemDrawerScaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                "${pending.size} evento${if (pending.size != 1) "s" else ""} esperando revision",
                color = Sand,
                fontSize = 12.sp
            )
            Spacer(Modifier.padding(vertical = 6.dp))

            SectionLabel("Toca un evento para revisar y editarlo")
            Spacer(Modifier.padding(vertical = 4.dp))

            for (w in pending) {
                PacemCard(onClick = { onOpenDetail(w.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                w.coupleLabel(),
                                color = Brown,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${w.weddingDate}  -  ${w.weddingTime}",
                                color = Sand,
                                fontSize = 12.sp
                            )
                            Text(w.venueName, color = Brown, fontSize = 13.sp)
                            if (w.plannerName != null) {
                                Text("Planner: ${w.plannerName}", color = Sand, fontSize = 11.sp)
                            }
                        }
                        StatusBadge(w.status)
                    }
                }
                Spacer(Modifier.padding(vertical = 4.dp))
            }

            if (errorMessage != null) {
                Spacer(Modifier.padding(vertical = 8.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.padding(vertical = 16.dp))
        }
    }
}
