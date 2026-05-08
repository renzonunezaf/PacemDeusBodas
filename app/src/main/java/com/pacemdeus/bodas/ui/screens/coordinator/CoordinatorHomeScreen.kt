package com.pacemdeus.bodas.ui.screens.coordinator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.DemoData
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.Gold
import com.pacemdeus.bodas.ui.components.GoldSoft
import com.pacemdeus.bodas.ui.components.NavBg
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.StatCard
import com.pacemdeus.bodas.ui.components.StatusBadge

// Pantalla principal del coordinador. Muestra todas las bodas de la app,
// dos contadores (Total y Pendientes) y permite navegar al detalle de
// cada una. La lista se renderiza con Column + verticalScroll porque el
// curso aun no ha enseñado LazyColumn.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinatorHomeScreen(
    weddings: List<Wedding>,
    onOpenDetail: (String) -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenApprove: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    Scaffold(
        topBar = { PacemTopBar(title = "Coordinador General", onLogout = onLogout) },
        bottomBar = {
            CoordinatorBottomNav(
                current = CoordinatorTab.Events,
                onSelectEvents = {},
                onSelectMap = onOpenMap,
                onSelectApprove = onOpenApprove
            )
        },
        containerColor = Cream
    ) { padding ->

        if (weddings.isEmpty()) {
            EmptyState("No hay eventos registrados")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ─── Resumen estadistico ───────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Total",
                    value = weddings.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Pendientes",
                    value = weddings.count {
                        it.status == WeddingStatus.DRAFT ||
                        it.status == WeddingStatus.SUBMITTED
                    }.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.padding(vertical = 8.dp))

            // ─── Lista de bodas ────────────────────────────
            for (w in weddings) {
                CoordinatorWeddingCard(wedding = w) { onOpenDetail(w.id) }
                Spacer(Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun CoordinatorWeddingCard(wedding: Wedding, onClick: () -> Unit) {
    val couple = DemoData.couples.firstOrNull { it.id == wedding.coupleId }
    val planner = wedding.plannerId?.let { pid ->
        DemoData.planners.firstOrNull { it.id == pid }
    }

    PacemCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    couple?.displayName() ?: "Pareja sin datos",
                    color = Brown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${wedding.weddingDate}  -  ${wedding.weddingTime}",
                    color = Sand,
                    fontSize = 12.sp
                )
                Text(
                    wedding.venueName,
                    color = Brown,
                    fontSize = 13.sp
                )
                if (planner != null) {
                    Text(
                        "Planner: ${planner.name}",
                        color = Sand,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        "Sin wedding planner asignado",
                        color = Sand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            StatusBadge(wedding.status)
        }
    }
}

// ─── Bottom navigation del rol Coordinator ─────────────────

enum class CoordinatorTab { Events, Map, Approve }

@Composable
fun CoordinatorBottomNav(
    current: CoordinatorTab,
    onSelectEvents: () -> Unit,
    onSelectMap: () -> Unit,
    onSelectApprove: () -> Unit
) {
    NavigationBar(containerColor = NavBg) {
        NavigationBarItem(
            selected = current == CoordinatorTab.Events,
            onClick = { if (current != CoordinatorTab.Events) onSelectEvents() },
            icon = { Icon(Icons.Default.EventNote, contentDescription = "Eventos") },
            label = { Text("Eventos") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
        NavigationBarItem(
            selected = current == CoordinatorTab.Map,
            onClick = { if (current != CoordinatorTab.Map) onSelectMap() },
            icon = { Icon(Icons.Default.Map, contentDescription = "Mapa") },
            label = { Text("Mapa") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
        NavigationBarItem(
            selected = current == CoordinatorTab.Approve,
            onClick = { if (current != CoordinatorTab.Approve) onSelectApprove() },
            icon = { Icon(Icons.Default.AssignmentTurnedIn, contentDescription = "Aprobar") },
            label = { Text("Aprobar") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CoordinatorHomeScreenPreview() {
    MaterialTheme {
        CoordinatorHomeScreen(weddings = DemoData.initialWeddings)
    }
}
