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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.pacemdeus.bodas.data.DemoData
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge

// Detalle de un evento desde la perspectiva del coordinador. Resume
// pareja, planner, ubicacion, estado y desglose economico, y ofrece
// los botones de accion: tomar foto del local, asignar planner, ver
// el contrato y abrir la ubicacion en la app de mapas del telefono.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeddingDetailScreen(
    wedding: Wedding?,
    instrumentIds: Set<String>,
    setlistCount: Int,
    onBack: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onAssignPlanner: () -> Unit = {},
    onOpenContract: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = { PacemTopBar(title = "Detalle del evento", onBack = onBack) },
        containerColor = Cream
    ) { padding ->

        if (wedding == null) {
            EmptyState("No se encontro el evento")
            return@Scaffold
        }

        val couple = DemoData.couples.firstOrNull { it.id == wedding.coupleId }
        val planner = wedding.plannerId?.let { pid ->
            DemoData.planners.firstOrNull { it.id == pid }
        }
        val instruments = DemoData.instruments.filter { it.id in instrumentIds }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ─── Resumen pareja + estado ───────────────────
            PacemCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            couple?.displayName() ?: "",
                            color = Brown,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${wedding.weddingDate}  -  ${wedding.weddingTime}",
                            color = Sand,
                            fontSize = 12.sp
                        )
                    }
                    StatusBadge(wedding.status)
                }
                Spacer(Modifier.padding(vertical = 4.dp))
                Text(
                    couple?.phone ?: "",
                    color = Brown,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ─── Lugar y planner ───────────────────────────
            PacemCard {
                SectionLabel("Lugar")
                Text(
                    wedding.venueName,
                    color = Brown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    wedding.venueAddress,
                    color = Sand,
                    fontSize = 12.sp
                )
                Spacer(Modifier.padding(vertical = 4.dp))
                Text(
                    if (wedding.venuePhotoTaken)
                        "Foto del local: registrada"
                    else
                        "Foto del local: no registrada",
                    color = if (wedding.venuePhotoTaken) Brown else Sand,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (wedding.venueLat != null && wedding.venueLng != null) {
                    Spacer(Modifier.padding(vertical = 4.dp))
                    OutlineGoldButton(
                        text = "Abrir ubicacion en Maps",
                        onClick = {
                            openInMaps(
                                context,
                                wedding.venueLat,
                                wedding.venueLng,
                                wedding.venueName
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ─── Wedding planner ───────────────────────────
            PacemCard {
                SectionLabel("Wedding planner")
                if (planner != null) {
                    Text(
                        planner.name,
                        color = Brown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        planner.company ?: "Freelance",
                        color = Sand,
                        fontSize = 12.sp
                    )
                    Text(
                        planner.phone,
                        color = Brown,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        "Sin planner asignado",
                        color = Sand,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.padding(vertical = 4.dp))
                OutlineGoldButton(
                    text = if (planner == null) "Asignar planner" else "Cambiar planner",
                    onClick = onAssignPlanner
                )
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ─── Ensamble musical resumido ─────────────────
            PacemCard {
                SectionLabel("Ensamble musical")
                Text(
                    "$setlistCount canciones registradas",
                    color = Brown,
                    fontSize = 13.sp
                )
                Spacer(Modifier.padding(vertical = 4.dp))
                if (instruments.isEmpty()) {
                    Text(
                        "Sin instrumentos contratados",
                        color = Sand,
                        fontSize = 12.sp
                    )
                } else {
                    for (ins in instruments) {
                        Row {
                            Text(
                                "  - ${ins.name}",
                                color = Brown,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "S/. ${"%.0f".format(ins.priceLima)}",
                                color = Brown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ─── Inversion total ───────────────────────────
            PacemCard {
                SectionLabel("Inversion total")
                Text(
                    "S/. ${"%.2f".format(wedding.totalPrice)}",
                    color = Brown,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Base S/. ${"%.0f".format(wedding.basePrice)} + " +
                        "Instrumentos S/. ${"%.0f".format(wedding.instrumentsPrice)}",
                    color = Sand,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.padding(vertical = 16.dp))

            // ─── Acciones del coordinador ──────────────────
            GoldButton(
                text = if (wedding.venuePhotoTaken)
                    "Volver a tomar foto del local"
                else
                    "Tomar foto del local",
                onClick = onTakePhoto
            )
            Spacer(Modifier.padding(vertical = 4.dp))
            OutlineGoldButton(text = "Ver contrato", onClick = onOpenContract)

            Spacer(Modifier.padding(vertical = 16.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun WeddingDetailScreenPreview() {
    MaterialTheme {
        WeddingDetailScreen(
            wedding = DemoData.initialWeddings[1],
            instrumentIds = setOf("ins-1", "ins-2"),
            setlistCount = 4
        )
    }
}
