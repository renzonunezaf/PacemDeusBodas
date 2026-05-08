package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
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
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.Gold
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel

// Vista informativa del contrato. Muestra los datos del evento, la
// pareja, el desglose de precios y los instrumentos contratados,
// imitando el look de un contrato impreso. El boton firmar es un
// placeholder visual: por ahora solo cambia un boolean local.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractScreen(
    wedding: Wedding?,
    instrumentIds: Set<String>,
    onBack: () -> Unit = {}
) {
    val instruments = DemoData.instruments.filter { it.id in instrumentIds }
    val couple = wedding?.let { w ->
        DemoData.couples.firstOrNull { it.id == w.coupleId }
    }
    val planner = wedding?.plannerId?.let { pid ->
        DemoData.planners.firstOrNull { it.id == pid }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Contrato", onBack = onBack) },
        containerColor = Cream
    ) { padding ->

        if (wedding == null || couple == null) {
            EmptyState("No se encontro el contrato")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ─── Cabecera estilo formal ────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "CONTRATO DE SERVICIO MUSICAL",
                        color = Brown,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Coro Pacem Deus",
                        color = Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Lima, Peru",
                        color = Sand,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Divider(color = Gold, thickness = 1.dp)
            Spacer(Modifier.height(20.dp))

            // ─── Datos de la pareja ────────────────────────
            SectionLabel("Contratantes")
            PacemCard {
                LineRow("Novio",  couple.groomName)
                LineRow("DNI",    couple.groomDni)
                Spacer(Modifier.height(6.dp))
                LineRow("Novia",  couple.brideName)
                LineRow("DNI",    couple.brideDni)
                Spacer(Modifier.height(6.dp))
                LineRow("Telefono", couple.phone)
            }

            Spacer(Modifier.height(12.dp))

            // ─── Detalle del evento ────────────────────────
            SectionLabel("Detalle del evento")
            PacemCard {
                LineRow("Fecha",     wedding.weddingDate)
                LineRow("Hora",      wedding.weddingTime)
                LineRow("Lugar",     wedding.venueName)
                LineRow("Direccion", wedding.venueAddress)
                if (planner != null) {
                    LineRow("Wedding Planner", planner.name)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Servicios contratados ─────────────────────
            SectionLabel("Servicios contratados")
            PacemCard {
                Row {
                    Text("Servicio base del coro", color = Brown, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text(
                        "S/. ${"%.2f".format(wedding.basePrice)}",
                        color = Brown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (instruments.isEmpty()) {
                    Text(
                        "Sin instrumentos adicionales",
                        color = Sand,
                        fontSize = 12.sp
                    )
                } else {
                    for (ins in instruments) {
                        Row {
                            Text(ins.name, color = Brown, fontSize = 13.sp,
                                modifier = Modifier.weight(1f))
                            Text(
                                "S/. ${"%.2f".format(ins.priceLima)}",
                                color = Brown,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Divider(color = Sand)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        "TOTAL",
                        color = Gold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "S/. ${"%.2f".format(wedding.totalPrice)}",
                        color = Gold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Texto legal ───────────────────────────────
            Text(
                "El presente contrato establece el acuerdo entre los contratantes y el Coro " +
                "Pacem Deus para la prestacion del servicio musical descrito en la fecha y " +
                "lugar indicados. El valor total incluye la asistencia del coro durante la " +
                "ceremonia, la preparacion del repertorio acordado y los instrumentos " +
                "musicales seleccionados.",
                color = Brown,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(20.dp))

            // ─── Espacios para firmas ──────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SignatureBox("Por los novios", modifier = Modifier.weight(1f))
                SignatureBox("Por el coro",    modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LineRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            color = Sand,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            color = Brown,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun SignatureBox(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Divider(color = Brown, thickness = 1.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Sand, fontSize = 11.sp)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ContractScreenPreview() {
    MaterialTheme {
        ContractScreen(
            wedding = DemoData.initialWeddings[1],
            instrumentIds = setOf("ins-1", "ins-2")
        )
    }
}
