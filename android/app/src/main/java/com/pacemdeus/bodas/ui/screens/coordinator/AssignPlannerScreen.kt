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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.PlannerSummary
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla de asignacion de wedding planner a una boda. Carga del backend:
//   - El detalle de la boda (apiClient.getBoda) para precargar el planner
//     actual si ya tiene uno.
//   - La lista de planners disponibles (apiClient.listPlanners).
//
// Al confirmar, dispara apiClient.assignPlanner(idBoda, idPlanner) y
// regresa al detalle de la boda.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignPlannerScreen(
    weddingId: String,
    onBack: () -> Unit = {},
    onConfirm: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var planners by remember { mutableStateOf<List<PlannerSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(weddingId) {
        isLoading = true
        var pending = 2
        val finish = { pending--; if (pending == 0) isLoading = false }

        apiClient.getBoda(weddingId) { result ->
            when (result) {
                is ApiResult.Success -> {
                    wedding = result.data
                    selected = result.data.plannerId
                }
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
            finish()
        }

        apiClient.listPlanners { result ->
            when (result) {
                is ApiResult.Success -> planners = result.data
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
            finish()
        }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Asignar planner", onBack = onBack) },
        containerColor = Cream
    ) { padding ->

        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        val currentWedding = wedding
        if (currentWedding == null) {
            EmptyState(errorMessage ?: "No se encontro el evento")
            return@Scaffold
        }

        if (planners.isEmpty()) {
            EmptyState("No hay wedding planners registrados")
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
                "Toca uno para seleccionarlo.",
                color = Sand,
                fontSize = 12.sp
            )

            Spacer(Modifier.padding(vertical = 8.dp))

            for (planner in planners) {
                val isSelected = planner.id == selected

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
                        .clickable(enabled = !isSaving) { selected = planner.id }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(GoldSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Gold)
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
                            if (planner.phone != null) {
                                Text(planner.phone, color = Sand, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(vertical = 4.dp))
            }

            if (errorMessage != null) {
                Spacer(Modifier.padding(vertical = 8.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.padding(vertical = 16.dp))

            GoldButton(
                text = if (isSaving) "Asignando..." else "Confirmar asignacion",
                enabled = selected != null && !isSaving,
                onClick = {
                    val plannerId = selected ?: return@GoldButton
                    isSaving = true
                    errorMessage = null
                    apiClient.assignPlanner(currentWedding.id, plannerId) { result ->
                        isSaving = false
                        when (result) {
                            is ApiResult.Success -> onConfirm()
                            is ApiResult.Error -> errorMessage = result.message
                            else -> {}
                        }
                    }
                }
            )

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}
