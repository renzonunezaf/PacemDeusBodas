package com.pacemdeus.bodas.ui.screens.planner

import android.content.Intent
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
import androidx.core.net.toUri
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
import com.pacemdeus.bodas.ui.screens.coordinator.openInMaps

// Detalle de evento desde la perspectiva del wedding planner. Solo
// lectura. Las dos acciones nativas son llamar al coro (Intent
// ACTION_DIAL con tel:) y abrir la ubicacion en Maps (Intent
// ACTION_VIEW con geo:).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDetailScreen(
    wedding: Wedding?,
    instrumentIds: Set<String>,
    setlistCount: Int,
    onBack: () -> Unit = {},
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
        val instruments = DemoData.instruments.filter { it.id in instrumentIds }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ─── Pareja ────────────────────────────────────
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
                        Text(couple?.phone ?: "", color = Brown, fontSize = 13.sp)
                    }
                    StatusBadge(wedding.status)
                }
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ─── Lugar ─────────────────────────────────────
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
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ─── Detalle musical ───────────────────────────
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
                        Text(
                            "  - ${ins.name}",
                            color = Brown,
                            fontSize = 12.sp
                        )
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
            }

            Spacer(Modifier.padding(vertical = 16.dp))

            // ─── Acciones nativas ──────────────────────────
            GoldButton(
                text = "Llamar al coro",
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_DIAL,
                        "tel:${DemoData.CHOIR_PHONE}".toUri()
                    )
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                }
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

            Spacer(Modifier.padding(vertical = 4.dp))
            OutlineGoldButton(text = "Ver contrato", onClick = onOpenContract)

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PlannerDetailScreenPreview() {
    MaterialTheme {
        PlannerDetailScreen(
            wedding = DemoData.initialWeddings[0],
            instrumentIds = emptySet(),
            setlistCount = 0
        )
    }
}
