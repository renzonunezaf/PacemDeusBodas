package com.pacemdeus.bodas.ui.screens.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla principal del wedding planner. Carga del backend las bodas
// que tiene asignadas via apiClient.listPlannerBodas(). El backend
// filtra automaticamente por el id_planner del JWT del usuario.
//
// El planner no puede modificar nada, solo ver y abrir detalle de
// cada evento asignado.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDashboardScreen(
    session: UserSession,
    onOpenDetail: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var weddings by remember { mutableStateOf<List<Wedding>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        apiClient.listPlannerBodas { result ->
            isLoading = false
            when (result) {
                is ApiResult.Success -> weddings = result.data
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    com.pacemdeus.bodas.ui.components.PacemDrawerScaffold(
        title = "Mis eventos",
        drawerItems = listOf(
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Mis eventos",
                icon = Icons.Default.EventNote,
                selected = true,
                onClick = {}
            )
        ),
        onLogout = onLogout
    ) { padding ->

        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@PacemDrawerScaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (weddings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        errorMessage ?: "No tienes eventos asignados todavia",
                        color = Sand,
                        fontSize = 14.sp
                    )
                }
                return@Column
            }

            // ─── Contador sobrio como subtitulo, no como card ────
            val totalActivos = weddings.count {
                it.status == WeddingStatus.DRAFT ||
                it.status == WeddingStatus.SUBMITTED ||
                it.status == WeddingStatus.APPROVED ||
                it.status == WeddingStatus.CONTRACTED
            }
            Text(
                "$totalActivos eventos activos  ·  ${weddings.size} en total",
                color = Sand,
                fontSize = 12.sp
            )
            Spacer(Modifier.padding(vertical = 8.dp))

            // ─── Toggle Calendario / Lista ───────────────────────
            var vistaCalendario by remember { mutableStateOf(true) }
            com.pacemdeus.bodas.ui.components.SegmentedToggle(
                options = listOf("Calendario", "Lista"),
                selectedIndex = if (vistaCalendario) 0 else 1,
                onSelect = { vistaCalendario = (it == 0) }
            )
            Spacer(Modifier.padding(vertical = 8.dp))

            // ─── Vista activa ────────────────────────────────────
            if (vistaCalendario) {
                var mesVisible by remember {
                    mutableStateOf(com.pacemdeus.bodas.ui.components.YearMonthSimple.now())
                }
                var diaSeleccionado by remember {
                    mutableStateOf<Pair<String, List<com.pacemdeus.bodas.data.DayBooking>>?>(null)
                }
                // Set de fechas de las bodas del planner. El backend
                // en modo admin devuelve TODAS las bodas del mes (no
                // filtra por planner), por eso pasamos al calendario
                // este set para que solo sombree las del planner logueado.
                val fechasDelPlanner = remember(weddings) {
                    weddings.map { it.weddingDate }.toSet()
                }
                com.pacemdeus.bodas.ui.components.CustomCalendar(
                    mode = com.pacemdeus.bodas.ui.components.CalendarMode.ADMIN_OVERVIEW,
                    highlightedDates = fechasDelPlanner,
                    onMonthChange = { mes -> mesVisible = mes },
                    onDayTap = { date, _, hasBookings ->
                        if (hasBookings) {
                            val delDia = weddings.filter { it.weddingDate == date }
                            diaSeleccionado = Pair(date,
                                delDia.map { w ->
                                    com.pacemdeus.bodas.data.DayBooking(
                                        weddingId = w.id,
                                        time = w.weddingTime,
                                        couple = w.coupleLabel(),
                                        status = w.status.name
                                    )
                                }
                            )
                        }
                    }
                )

                // Lista del mes visible debajo, refresca al navegar
                Spacer(Modifier.padding(vertical = 12.dp))
                MonthEventsList(
                    weddings = weddings,
                    year = mesVisible.year,
                    month = mesVisible.month,
                    onOpenDetail = onOpenDetail
                )

                diaSeleccionado?.let { (fecha, bodas) ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { diaSeleccionado = null },
                        title = {
                            Text("Eventos del $fecha", color = Brown,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        },
                        text = {
                            Column {
                                bodas.forEach { b ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable {
                                                diaSeleccionado = null
                                                onOpenDetail(b.weddingId)
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            b.time, color = Gold,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(56.dp)
                                        )
                                        Text(
                                            b.couple, color = Brown,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { diaSeleccionado = null }
                            ) { Text("Cerrar", color = Gold) }
                        },
                        containerColor = Cream
                    )
                }
            } else {
                // Vista Lista
                SectionLabel("Eventos asignados")
                for (w in weddings) {
                    PacemCard(onClick = { onOpenDetail(w.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    w.coupleLabel(),
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
                            }
                            StatusBadge(w.status)
                        }
                    }
                    Spacer(Modifier.padding(vertical = 4.dp))
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.padding(vertical = 8.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

/**
 * Lista de eventos del mes visible bajo el calendario del planner.
 * Misma idea que en CoordinatorHomeScreen, replicada aqui para
 * mantener cada dashboard autonomo (no compartimos un layout grande).
 */
@Composable
private fun MonthEventsList(
    weddings: List<Wedding>,
    year: Int,
    month: Int,
    onOpenDetail: (String) -> Unit
) {
    val prefijo = String.format(java.util.Locale.US, "%04d-%02d-", year, month)
    val delMes = weddings
        .filter { it.weddingDate.startsWith(prefijo) }
        .sortedWith(compareBy({ it.weddingDate }, { it.weddingTime }))

    Text(
        "Eventos de este mes",
        color = Brown,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.padding(vertical = 4.dp))

    if (delMes.isEmpty()) {
        Text(
            "Sin eventos en este mes.",
            color = Sand,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }

    Text(
        "${delMes.size} evento${if (delMes.size != 1) "s" else ""}",
        color = Sand,
        fontSize = 11.sp
    )
    Spacer(Modifier.padding(vertical = 4.dp))

    for (w in delMes) {
        PacemCard(onClick = { onOpenDetail(w.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        w.coupleLabel(),
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
                }
                StatusBadge(w.status)
            }
        }
        Spacer(Modifier.padding(vertical = 4.dp))
    }
}
