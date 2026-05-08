package com.pacemdeus.bodas.ui.screens.planner

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatCard
import com.pacemdeus.bodas.ui.components.StatusBadge

// Dashboard del wedding planner. Solo lectura: lista las bodas que el
// admin le ha asignado, con dos contadores arriba (Total y Activos).
// El planner no puede modificar nada, solo ver y llamar al coro.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDashboardScreen(
    session: UserSession,
    weddings: List<Wedding>,
    onOpenDetail: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            PacemTopBar(title = session.displayName(), onLogout = onLogout)
        },
        containerColor = Cream
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ─── Resumen ──────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Total",
                    value = weddings.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Activos",
                    value = weddings.count {
                        it.status == WeddingStatus.DRAFT ||
                        it.status == WeddingStatus.SUBMITTED ||
                        it.status == WeddingStatus.APPROVED ||
                        it.status == WeddingStatus.CONTRACTED
                    }.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.padding(vertical = 8.dp))

            if (weddings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tienes eventos asignados todavia",
                        color = Sand,
                        fontSize = 14.sp
                    )
                }
                return@Column
            }

            SectionLabel("Eventos asignados")

            for (w in weddings) {
                val couple = DemoData.couples.firstOrNull { it.id == w.coupleId }
                PacemCard(onClick = { onOpenDetail(w.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                couple?.displayName() ?: "",
                                color = Brown,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${w.weddingDate}  -  ${w.weddingTime}",
                                color = Sand,
                                fontSize = 12.sp
                            )
                            Text(
                                w.venueName,
                                color = Brown,
                                fontSize = 13.sp
                            )
                        }
                        StatusBadge(w.status)
                    }
                }
                Spacer(Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PlannerDashboardScreenPreview() {
    MaterialTheme {
        PlannerDashboardScreen(
            session = UserSession(
                user = DemoData.users[2],
                coupleProfile = null,
                plannerProfile = DemoData.planners[0],
                weddingId = null
            ),
            weddings = DemoData.initialWeddings.filter { it.plannerId == "plr-1" }
        )
    }
}
