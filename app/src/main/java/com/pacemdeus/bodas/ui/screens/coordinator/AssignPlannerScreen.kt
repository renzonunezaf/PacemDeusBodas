package com.pacemdeus.bodas.ui.screens.coordinator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel

// Lista de wedding planners disponibles para asignar al evento. El
// numero de eventos por planner se calcula en vivo sobre weddingsAll
// para que el coordinador vea quien tiene mas carga.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignPlannerScreen(
    wedding: Wedding?,
    weddingsAll: List<Wedding>,
    onBack: () -> Unit = {},
    onConfirm: (String) -> Unit = {}
) {
    var selected by remember { mutableStateOf(wedding?.plannerId) }

    Scaffold(
        topBar = { PacemTopBar(title = "Asignar planner", onBack = onBack) },
        containerColor = Cream
    ) { padding ->

        if (wedding == null) {
            EmptyState("No se encontro el evento")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionLabel("Wedding planners disponibles")
            Text(
                "Toca uno para seleccionarlo. El numero indica cuantos eventos tiene ya asignados.",
                color = Sand,
                fontSize = 12.sp
            )

            Spacer(Modifier.padding(vertical = 8.dp))

            for (planner in DemoData.planners) {
                val isSelected = planner.id == selected
                val weddingCount = weddingsAll.count { it.plannerId == planner.id }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) GoldSoft else Color.White,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) Gold else Color(0xFFE0D9C8),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selected = planner.id }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(GoldSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Gold
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
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
                                color = Sand,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            "$weddingCount evento(s)",
                            color = Gold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.padding(vertical = 16.dp))

            GoldButton(
                text = "Confirmar asignacion",
                enabled = selected != null,
                onClick = { selected?.let { onConfirm(it) } }
            )

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AssignPlannerScreenPreview() {
    MaterialTheme {
        AssignPlannerScreen(
            wedding = DemoData.initialWeddings[0],
            weddingsAll = DemoData.initialWeddings
        )
    }
}
