package com.pacemdeus.bodas.ui.screens.coordinator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge

// Bandeja de aprobacion del coordinador. Filtra las bodas en DRAFT y
// SUBMITTED, y permite aprobarlas (pasan a APPROVED) o devolverlas
// (vuelven a DRAFT). Cada accion abre un dialog de confirmacion.

private enum class PendingAction { APPROVE, REJECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproveScreen(
    weddings: List<Wedding>,
    onOpenHome: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onApprove: (String) -> Unit = {},
    onReject: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val pending = weddings.filter {
        it.status == WeddingStatus.DRAFT || it.status == WeddingStatus.SUBMITTED
    }
    var pendingDialog by remember { mutableStateOf<Pair<Wedding, PendingAction>?>(null) }

    Scaffold(
        topBar = { PacemTopBar(title = "Aprobar eventos", onLogout = onLogout) },
        bottomBar = {
            CoordinatorBottomNav(
                current = CoordinatorTab.Approve,
                onSelectEvents = onOpenHome,
                onSelectMap = onOpenMap,
                onSelectApprove = {}
            )
        },
        containerColor = Cream
    ) { padding ->

        if (pending.isEmpty()) {
            EmptyState("No hay eventos pendientes de aprobar")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionLabel("${pending.size} evento(s) por revisar")

            Spacer(Modifier.padding(vertical = 6.dp))

            for (w in pending) {
                val couple = DemoData.couples.firstOrNull { it.id == w.coupleId }
                val planner = w.plannerId?.let { pid ->
                    DemoData.planners.firstOrNull { it.id == pid }
                }

                PacemCard {
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
                            Text(w.venueName, color = Brown, fontSize = 13.sp)
                            if (planner != null) {
                                Text(
                                    "Planner: ${planner.name}",
                                    color = Sand,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        StatusBadge(w.status)
                    }

                    Spacer(Modifier.padding(vertical = 6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineGoldButton(
                            text = "Devolver",
                            onClick = { pendingDialog = w to PendingAction.REJECT }
                        )
                    }
                    Spacer(Modifier.padding(vertical = 4.dp))
                    GoldButton(
                        text = "Aprobar",
                        onClick = { pendingDialog = w to PendingAction.APPROVE }
                    )
                }

                Spacer(Modifier.padding(vertical = 6.dp))
            }
            Spacer(Modifier.padding(vertical = 16.dp))
        }
    }

    pendingDialog?.let { (w, action) ->
        val (title, body, confirmText, confirmColor) = when (action) {
            PendingAction.APPROVE -> Quad(
                "Aprobar evento",
                "El evento pasara a estado APROBADO y se notificara a la pareja.",
                "Aprobar",
                Gold
            )
            PendingAction.REJECT -> Quad(
                "Devolver a borrador",
                "El evento volvera a estado DRAFT para que la pareja lo ajuste.",
                "Devolver",
                MaterialTheme.colorScheme.error
            )
        }
        AlertDialog(
            onDismissRequest = { pendingDialog = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        PendingAction.APPROVE -> onApprove(w.id)
                        PendingAction.REJECT  -> onReject(w.id)
                    }
                    pendingDialog = null
                }) {
                    Text(confirmText, color = confirmColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDialog = null }) {
                    Text("Cancelar", color = Sand)
                }
            }
        )
    }
}

/** Tupla de 4 elementos auxiliar para empacar el contenido del dialog. */
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ApproveScreenPreview() {
    MaterialTheme { ApproveScreen(weddings = DemoData.initialWeddings) }
}
