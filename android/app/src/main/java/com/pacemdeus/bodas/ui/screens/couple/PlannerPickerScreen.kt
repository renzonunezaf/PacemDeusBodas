package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.PlannerSummary
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla para que la novia elija a su Wedding Planner. Hay solo 3
// planners en el sistema (insertados por seed SQL). El planner asignado
// puede ver la informacion del evento y subir fotos pero no editar
// cantos ni instrumentos.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerPickerScreen(
    session: UserSession,
    weddingId: String,
    currentPlannerId: String?,
    onBack: () -> Unit = {},
    onPicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var planners by remember { mutableStateOf<List<PlannerSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isAssigning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Carga inicial: traer la lista publica de planners
    LaunchedEffect(Unit) {
        apiClient.listPlannersPublic { result ->
            when (result) {
                is ApiResult.Success -> {
                    planners = result.data
                    isLoading = false
                }
                is ApiResult.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
                else -> isLoading = false
            }
        }
    }

    fun pickPlanner(planner: PlannerSummary) {
        isAssigning = true
        errorMessage = null
        apiClient.couplePickPlanner(weddingId, planner.id) { result ->
            isAssigning = false
            when (result) {
                is ApiResult.Success -> onPicked()
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Wedding planner", onBack = onBack) },
        containerColor = Cream
    ) { padding ->

        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ─── Header ──────────────────────────
            Text(
                if (currentPlannerId == null) "Elige tu wedding planner"
                else "Cambiar wedding planner",
                color = Brown,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "El wedding planner te ayuda a coordinar el dia de tu boda con el coro. " +
                    "Puede ver tu evento y subir fotos del local, pero no puede modificar " +
                    "tu seleccion de cantos ni instrumentos.",
                color = Sand,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(20.dp))

            // ─── Lista de planners ───────────────
            if (planners.isEmpty()) {
                Text(
                    "No hay wedding planners disponibles en este momento.",
                    color = Sand,
                    fontSize = 13.sp
                )
            } else {
                for ((idx, planner) in planners.withIndex()) {
                    PlannerCard(
                        planner = planner,
                        isSelected = planner.id == currentPlannerId,
                        enabled = !isAssigning,
                        onClick = { pickPlanner(planner) }
                    )
                    if (idx < planners.size - 1) Spacer(Modifier.height(12.dp))
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlannerCard(
    planner: PlannerSummary,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Gold else GoldSoft
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(14.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(16.dp)
    ) {
        Column {
            // Nombre + check si esta seleccionado
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GoldSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    planner.name,
                    color = Brown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Gold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Seleccionado",
                            tint = Cream,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Empresa
            if (!planner.company.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = Sand,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        planner.company,
                        color = Brown,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // Telefono
            if (!planner.phone.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = Sand,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        planner.phone,
                        color = Sand,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
