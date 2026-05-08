package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.GoldSoft
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel

// Pantalla de seleccion de instrumentos. El precio total se recalcula
// dinamicamente con derivedStateOf cada vez que cambia la seleccion.
// Al guardar, se reemplaza el set completo de instrumentos contratados
// y se actualiza el campo instrumentsPrice de la boda.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentsScreen(
    wedding: Wedding?,
    selected: Set<String>,
    onBack: () -> Unit = {},
    onSave: (weddingId: String, selected: Set<String>, totalExtra: Double) -> Unit =
        { _, _, _ -> }
) {
    val instruments = DemoData.instruments

    // Estado local: copia mutable de la seleccion actual
    var localSelection by remember { mutableStateOf(selected) }

    // Recalculo automatico del extra cada vez que la seleccion cambia
    val totalExtra by remember(localSelection) {
        derivedStateOf {
            instruments.filter { it.id in localSelection }.sumOf { it.priceLima }
        }
    }

    Scaffold(
        topBar = {
            PacemTopBar(title = "Instrumentos", onBack = onBack)
        },
        containerColor = Cream
    ) { padding ->

        if (wedding == null) {
            EmptyState("Crea primero tu evento")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionLabel("Voces e instrumentos disponibles")
            Text(
                "Selecciona los instrumentos adicionales para tu ceremonia. " +
                "El precio se suma al base de S/. ${"%.0f".format(wedding.basePrice)}.",
                color = Sand,
                fontSize = 12.sp
            )

            Spacer(Modifier.padding(vertical = 8.dp))

            // ─── Lista de instrumentos ─────────────────────
            for (ins in instruments) {
                val isChecked = ins.id in localSelection
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isChecked) GoldSoft else Color.White,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (isChecked) Gold else Color(0xFFE0D9C8),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            localSelection = if (isChecked)
                                localSelection - ins.id
                            else
                                localSelection + ins.id
                        }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).background(GoldSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Gold
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ins.name,
                                color = Brown,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "S/. ${"%.0f".format(ins.priceLima)}",
                                color = Sand,
                                fontSize = 12.sp
                            )
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { value ->
                                localSelection = if (value)
                                    localSelection + ins.id
                                else
                                    localSelection - ins.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Gold)
                        )
                    }
                }
                Spacer(Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.padding(vertical = 12.dp))

            // ─── Resumen del costo total ───────────────────
            PacemCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Resumen",
                            color = Sand,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Base: S/. ${"%.0f".format(wedding.basePrice)}",
                            color = Brown,
                            fontSize = 13.sp
                        )
                        Text(
                            "Instrumentos: S/. ${"%.0f".format(totalExtra)}",
                            color = Brown,
                            fontSize = 13.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "S/. ${"%.2f".format(wedding.basePrice + totalExtra)}",
                            color = Brown,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.padding(vertical = 16.dp))

            GoldButton(
                text = "Guardar instrumentos",
                onClick = { onSave(wedding.id, localSelection, totalExtra) }
            )

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InstrumentsScreenPreview() {
    MaterialTheme {
        InstrumentsScreen(
            wedding = DemoData.initialWeddings[0],
            selected = setOf("ins-1", "ins-2")
        )
    }
}
