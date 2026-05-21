package com.pacemdeus.bodas.ui.screens.coordinator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.StatusBadge
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.NavBg
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla principal del coordinador. Muestra todas las bodas de la app,
// dos contadores (Total y Pendientes) y permite navegar al detalle de
// cada una. Los datos se cargan via apiClient.listBodas() - el backend
// filtra automaticamente por rol del JWT (admin = todas las bodas).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinatorHomeScreen(
    onOpenDetail: (String) -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenApprove: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var weddings by remember { mutableStateOf<List<Wedding>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        apiClient.listBodas { result ->
            isLoading = false
            when (result) {
                is ApiResult.Success -> weddings = result.data
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    // Polling de notificaciones cada 10s mientras esta pantalla este
    // activa. Funciona como fallback complementario al push FCM (que es
    // la via principal en v07+): si por cualquier motivo el push no
    // llega, este polling agarra la novedad y actualiza la UI.
    //
    // El timestamp `serverTime` que devuelve el backend se guarda como
    // cursor para el siguiente poll (evita duplicados sin depender del
    // reloj local).
    var sincePoll by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            apiClient.pollNotifications(sincePoll) { result ->
                if (result is ApiResult.Success) {
                    sincePoll = result.data.serverTime
                    if (result.data.items.isNotEmpty()) {
                        for (notif in result.data.items) {
                            // Mostrar en bandeja del sistema. Pasamos weddingId
                            // (si la notif viene asociada a una boda) para que
                            // al tocarla, MainActivity haga deep link al detalle
                            // como si fuera un push FCM. Si la app ya recibio el
                            // push FCM antes, la bandeja mostrara dos notificaciones
                            // similares — es aceptable mientras validamos que el
                            // flujo funcione. En produccion se de-duplicaria por
                            // id_notificacion.
                            com.pacemdeus.bodas.services.mostrarNotificacion(
                                context,
                                notif.title,
                                notif.message,
                                notif.weddingId
                            )
                            apiClient.markNotificationRead(notif.id) {}
                        }
                        // Refrescar bodas: alguna probablemente cambio
                        // de estado (BODA_SUBMITTED, etc.)
                        apiClient.listBodas { listResult ->
                            if (listResult is ApiResult.Success) {
                                weddings = listResult.data
                            }
                        }
                    }
                }
            }
        }
    }

    com.pacemdeus.bodas.ui.components.PacemDrawerScaffold(
        title = "Coordinador General",
        drawerItems = listOf(
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Eventos",
                icon = Icons.Default.EventNote,
                selected = true,
                onClick = {}
            ),
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Mapa",
                icon = Icons.Default.Map,
                selected = false,
                onClick = onOpenMap
            ),
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Por aprobar",
                icon = Icons.Default.AssignmentTurnedIn,
                selected = false,
                onClick = onOpenApprove
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

        if (weddings.isEmpty()) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                EmptyState(errorMessage ?: "No hay eventos registrados")
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
            // Contador sobrio
            val pendientes = weddings.count {
                it.status == WeddingStatus.DRAFT || it.status == WeddingStatus.SUBMITTED
            }
            Text(
                "$pendientes pendientes de revision  ·  ${weddings.size} en total",
                color = Sand,
                fontSize = 12.sp
            )
            Spacer(Modifier.padding(vertical = 8.dp))

            // Toggle Calendario / Ver todos
            var vistaCalendario by remember { mutableStateOf(true) }
            com.pacemdeus.bodas.ui.components.SegmentedToggle(
                options = listOf("Calendario", "Ver todos"),
                selectedIndex = if (vistaCalendario) 0 else 1,
                onSelect = { vistaCalendario = (it == 0) }
            )
            Spacer(Modifier.padding(vertical = 8.dp))

            if (vistaCalendario) {
                // Estado del mes visible para refrescar la lista debajo.
                var mesVisible by remember {
                    mutableStateOf(com.pacemdeus.bodas.ui.components.YearMonthSimple.now())
                }
                var diaSeleccionado by remember {
                    mutableStateOf<Pair<String, List<com.pacemdeus.bodas.data.DayBooking>>?>(null)
                }
                com.pacemdeus.bodas.ui.components.CustomCalendar(
                    mode = com.pacemdeus.bodas.ui.components.CalendarMode.ADMIN_OVERVIEW,
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

                // ─── Lista del mes visible bajo el calendario ────────
                // Se refresca automaticamente al navegar entre meses.
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
                                    // v07: cada evento es un PacemCard
                                    // clickeable con flecha + badge de
                                    // estado para que se vea claro que
                                    // se puede tocar. Antes era un Row
                                    // suelto sin afordances visuales.
                                    PacemCard(
                                        onClick = {
                                            diaSeleccionado = null
                                            onOpenDetail(b.weddingId)
                                        }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                b.time, color = Gold,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(56.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    b.couple, color = Brown,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(Modifier.padding(vertical = 1.dp))
                                                StatusBadge(
                                                    runCatching { WeddingStatus.valueOf(b.status) }
                                                        .getOrDefault(WeddingStatus.DRAFT)
                                                )
                                            }
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = Gold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.padding(vertical = 3.dp))
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
                AllEventsList(
                    weddings = weddings,
                    onOpenDetail = onOpenDetail
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.padding(vertical = 8.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun CoordinatorWeddingCard(wedding: Wedding, onClick: () -> Unit) {
    PacemCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    wedding.coupleLabel(),
                    color = Brown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${wedding.weddingDate}  -  ${wedding.weddingTime}",
                    color = Sand,
                    fontSize = 12.sp
                )
                Text(wedding.venueName, color = Brown, fontSize = 13.sp)
                val plannerLabel = wedding.plannerName?.let { "Planner: $it" }
                    ?: if (wedding.plannerId != null) "Planner asignado" else "Sin wedding planner asignado"
                Text(
                    plannerLabel,
                    color = Sand,
                    fontSize = 11.sp,
                    fontWeight = if (wedding.plannerId == null) FontWeight.Medium else FontWeight.Normal
                )
            }
            StatusBadge(wedding.status)
        }
    }
}

// ─── Bottom navigation del rol Coordinator ─────────────────

enum class CoordinatorTab { Events, Map, Pending }

@Composable
fun CoordinatorBottomNav(
    current: CoordinatorTab,
    onSelectEvents: () -> Unit,
    onSelectMap: () -> Unit,
    onSelectApprove: () -> Unit
) {
    NavigationBar(containerColor = NavBg) {
        NavigationBarItem(
            selected = current == CoordinatorTab.Events,
            onClick = { if (current != CoordinatorTab.Events) onSelectEvents() },
            icon = { Icon(Icons.Default.EventNote, contentDescription = "Eventos") },
            label = { Text("Eventos") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
        NavigationBarItem(
            selected = current == CoordinatorTab.Map,
            onClick = { if (current != CoordinatorTab.Map) onSelectMap() },
            icon = { Icon(Icons.Default.Map, contentDescription = "Mapa") },
            label = { Text("Mapa") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
        NavigationBarItem(
            selected = current == CoordinatorTab.Pending,
            onClick = { if (current != CoordinatorTab.Pending) onSelectApprove() },
            icon = { Icon(Icons.Default.AssignmentTurnedIn, contentDescription = "Por aprobar") },
            label = { Text("Por aprobar") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
    }
}

/**
 * Lista de eventos del mes visible bajo el calendario. Filtra las
 * bodas que caen en el mes pedido y las ordena por fecha y hora.
 *
 * Las cards son visualmente identicas a las de la vista Lista, para
 * que la transicion entre vistas se sienta consistente.
 */
@Composable
private fun MonthEventsList(
    weddings: List<Wedding>,
    year: Int,
    month: Int,
    onOpenDetail: (String) -> Unit
) {
    // El mes viene como 1-12. Construimos el prefijo "YYYY-MM-" para
    // hacer match contra Wedding.weddingDate que es "YYYY-MM-DD".
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
        CoordinatorWeddingCard(wedding = w) { onOpenDetail(w.id) }
        Spacer(Modifier.padding(vertical = 4.dp))
    }
}

/**
 * Vista "Ver todos" con buscador en vivo y ordenamiento por fecha o nombre.
 * Por defecto ordena por fecha ascendente (proximos primero) que es lo
 * que un coordinador necesita ver al planificar sus proximos eventos.
 *
 * La busqueda filtra contra: nombres de la pareja, nombre del local,
 * nombre del wedding planner (si aplica) y fecha en formato ISO.
 * Es case-insensitive y filtra mientras se escribe (no requiere submit).
 */
@Composable
private fun AllEventsList(
    weddings: List<Wedding>,
    onOpenDetail: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var ordenPorFecha by remember { mutableStateOf(true) }

    // ─── Caja de busqueda ─────────────────────────────────
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text("Buscar por novios, local o planner", color = Sand, fontSize = 13.sp)
        },
        leadingIcon = {
            Icon(
                androidx.compose.material.icons.Icons.Default.Search,
                contentDescription = null,
                tint = Gold
            )
        },
        trailingIcon = {
            // Boton X para limpiar la busqueda rapido, solo visible si
            // hay texto. Evita que el usuario tenga que borrar caracter
            // a caracter cuando quiere reiniciar el filtro.
            if (query.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { query = "" }) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Limpiar busqueda",
                        tint = Sand
                    )
                }
            }
        },
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        colors = com.pacemdeus.bodas.ui.components.goldTextFieldColors()
    )

    Spacer(Modifier.padding(vertical = 6.dp))

    // ─── Toggle de orden ──────────────────────────────────
    com.pacemdeus.bodas.ui.components.SegmentedToggle(
        options = listOf("Por fecha", "Por nombre"),
        selectedIndex = if (ordenPorFecha) 0 else 1,
        onSelect = { ordenPorFecha = (it == 0) }
    )

    Spacer(Modifier.padding(vertical = 8.dp))

    // ─── Filtrar + ordenar ────────────────────────────────
    val q = query.trim().lowercase()
    val filtrados = if (q.isEmpty()) weddings else weddings.filter { w ->
        w.coupleLabel().lowercase().contains(q) ||
        w.venueName.lowercase().contains(q) ||
        (w.plannerName?.lowercase()?.contains(q) ?: false) ||
        w.weddingDate.lowercase().contains(q)
    }
    val ordenados = if (ordenPorFecha) {
        // Por fecha ascendente (proximos primero); dentro del mismo dia
        // ordena por hora.
        filtrados.sortedWith(compareBy({ it.weddingDate }, { it.weddingTime }))
    } else {
        // Por nombre de la pareja, comparacion case-insensitive
        filtrados.sortedBy { it.coupleLabel().lowercase() }
    }

    if (ordenados.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (q.isEmpty()) "No hay eventos registrados"
                else "Ningun evento coincide con \"$query\"",
                color = Sand,
                fontSize = 13.sp
            )
        }
    } else {
        for (w in ordenados) {
            CoordinatorWeddingCard(wedding = w) { onOpenDetail(w.id) }
            Spacer(Modifier.padding(vertical = 4.dp))
        }
    }
}
