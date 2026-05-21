package com.pacemdeus.bodas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.DayAvailability
import com.pacemdeus.bodas.data.MonthAvailability
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand
import java.util.Calendar
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
// CustomCalendar
// Calendario mensual con:
//   - Header con flechas para navegar entre meses
//   - Grid de 7 columnas (lun-dom)
//   - Marcado de dias segun disponibilidad (rojo/ambar)
//   - Click handler opcional para seleccion
//   - Estado de dia seleccionado destacado
//
// Carga la disponibilidad del mes via ApiClient.getDisponibilidadMes
// cuando cambia el mes visible.
//
// IMPORTANTE: minSdk = 25 no incluye java.time, usamos java.util.Calendar.
// Para evitar bugs con timezones, todos los Calendar se crean en hora
// local y solo se compara la parte calendario (year-month-day).
// ═══════════════════════════════════════════════════════════════════

/**
 * Wrapper inmutable de un par (anio, mes 1-12) para navegar entre meses.
 * Reemplaza java.time.YearMonth que requiere minSdk 26.
 */
data class YearMonthSimple(val year: Int, val month: Int) {
    fun minusMonths(n: Int): YearMonthSimple {
        var y = year; var m = month - n
        while (m < 1) { m += 12; y -= 1 }
        return YearMonthSimple(y, m)
    }
    fun plusMonths(n: Int): YearMonthSimple {
        var y = year; var m = month + n
        while (m > 12) { m -= 12; y += 1 }
        return YearMonthSimple(y, m)
    }
    companion object {
        fun now(): YearMonthSimple {
            val c = Calendar.getInstance()
            return YearMonthSimple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        }
    }
}

private val MESES_ES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

private val DIAS_SEMANA_ES = listOf("L", "M", "X", "J", "V", "S", "D")

// Colores semánticos del calendario, alineados con la paleta del contrato
private val COLOR_FULL_BG      = Color(0xFFE57373)  // rojo suave
private val COLOR_FULL_TEXT    = Color(0xFFFFFFFF)
private val COLOR_PARTIAL_BG   = Color(0xFFE8C070)  // gold-lt
private val COLOR_PARTIAL_TEXT = Color(0xFF3D3530)  // brown
private val COLOR_OCCUPIED_BG  = Color(0xFFF0E8D4)  // cream-dk - sombreado neutro para admin/novia

/**
 * @param initialMonth Mes inicial a mostrar. Por defecto el actual.
 * @param selectedDate Fecha seleccionada (formato YYYY-MM-DD), o null.
 * @param onDateSelected Callback al hacer click en un dia. Se llama
 *   solo si la fecha es seleccionable (no esta en el pasado ni FULL).
 *   Recibe (fecha YYYY-MM-DD, estado).
 * @param onDayTap Callback alternativo que se dispara SIEMPRE (incluso
 *   en dias FULL). Util para mostrar el detalle de un dia ocupado en
 *   un bottom sheet desde el dashboard.
 * @param excludeWeddingId Si la novia esta editando su propia boda,
 *   excluye ese id para que el dia no aparezca como rojo por causa
 *   propia.
 * @param allowPast Si true permite seleccionar dias del pasado. Por
 *   defecto false (la novia solo elige futuro).
 */
/**
 * Modos visuales del calendario:
 *  - PICKER_FOR_NEW: la novia eligiendo fecha al crear/editar su boda.
 *    Muestra rojo (FULL, no seleccionable) y ambar (PARTIAL).
 *  - ADMIN_OVERVIEW: el coro o wedding planner viendo el dashboard.
 *    Todos los dias con bodas se sombrean igual, sin semaforo. Tap
 *    siempre muestra el detalle.
 *  - COUPLE_OWN: la novia viendo su home. Solo se sombrea SU dia de
 *    boda. Los otros estados los ignoramos visualmente.
 */
enum class CalendarMode { PICKER_FOR_NEW, ADMIN_OVERVIEW, COUPLE_OWN }

/**
 * @param mode determina como se pintan los dias (ver enum arriba).
 * @param ownWeddingDate solo para mode=COUPLE_OWN: la fecha (YYYY-MM-DD)
 *   de la boda de la pareja para sombrearla.
 * @param onMonthChange callback al navegar entre meses. Permite que el
 *   parent sepa que mes esta visible para refrescar listas debajo.
 */
@Composable
fun CustomCalendar(
    initialMonth: YearMonthSimple = YearMonthSimple.now(),
    selectedDate: String? = null,
    onDateSelected: ((date: String, status: DayAvailability) -> Unit)? = null,
    onDayTap: ((date: String, status: DayAvailability, hasBookings: Boolean) -> Unit)? = null,
    onMonthChange: ((YearMonthSimple) -> Unit)? = null,
    excludeWeddingId: String? = null,
    mode: CalendarMode = CalendarMode.PICKER_FOR_NEW,
    ownWeddingDate: String? = null,
    /**
     * Si se pasa un set de fechas (YYYY-MM-DD), el calendario NO consulta
     * el endpoint /disponibilidad y solo sombrea esas fechas con OCCUPIED.
     * Util para el wedding planner: el endpoint en modo admin devuelve
     * TODAS las bodas del mes; con este parametro el planner solo ve las
     * que le fueron asignadas (que ya tiene cargadas en su lista).
     */
    highlightedDates: Set<String>? = null,
    allowPast: Boolean = false,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(initialMonth) }
    var availability by remember { mutableStateOf<MonthAvailability?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    LaunchedEffect(currentMonth) {
        isLoading = true
        onMonthChange?.invoke(currentMonth)
        // En modo COUPLE_OWN solo sombreamos el dia propio.
        // Si vienen highlightedDates desde el parent, los usamos en vez
        // de consultar el endpoint (caso wedding planner).
        if (mode == CalendarMode.COUPLE_OWN || highlightedDates != null) {
            isLoading = false
            availability = null
            return@LaunchedEffect
        }
        apiClient.getDisponibilidadMes(
            year = currentMonth.year,
            month = currentMonth.month,
            excludeWeddingId = excludeWeddingId
        ) { result ->
            isLoading = false
            availability = if (result is ApiResult.Success) result.data else null
        }
    }

    Column(modifier = modifier) {
        // ─── Header con navegacion ───────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Mes anterior",
                    tint = Gold
                )
            }
            Text(
                "${MESES_ES[currentMonth.month - 1]} ${currentMonth.year}",
                color = Brown,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Mes siguiente",
                    tint = Gold
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // ─── Header dias de la semana ───────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            DIAS_SEMANA_ES.forEach { dia ->
                Text(
                    dia,
                    color = Sand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(4.dp)
                )
            }
        }

        if (isLoading && availability == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Gold, strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp))
            }
            return@Column
        }

        // ─── Calcular layout del mes ────────────────────────
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(currentMonth.year, currentMonth.month - 1, 1)
        // Calendar.DAY_OF_WEEK: 1=Dom, 2=Lun, ..., 7=Sab. Lo convertimos
        // a 0=Lun..6=Dom para que la semana empiece en lunes.
        val firstDow = cal.get(Calendar.DAY_OF_WEEK)
        val startOffset = if (firstDow == Calendar.SUNDAY) 6 else firstDow - 2
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        // Today (year-month-day)
        val today = Calendar.getInstance()
        val todayY = today.get(Calendar.YEAR)
        val todayM = today.get(Calendar.MONTH) + 1
        val todayD = today.get(Calendar.DAY_OF_MONTH)

        val states = availability?.dayStates.orEmpty()
        val bookings = availability?.dayBookings.orEmpty()

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIdx = row * 7 + col
                    val dayNum = cellIdx - startOffset + 1
                    if (dayNum < 1 || dayNum > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val dateStr = String.format(
                            Locale.US, "%04d-%02d-%02d",
                            currentMonth.year, currentMonth.month, dayNum
                        )
                        // Estado efectivo:
                        //   highlightedDates != null  -> OCCUPIED si el dia esta en el set
                        //                                (caso wedding planner con sus bodas)
                        //   COUPLE_OWN: solo OCCUPIED si es la propia boda
                        //   ADMIN_OVERVIEW: lo que venga del endpoint
                        //   PICKER_FOR_NEW: free/partial/full semaforo
                        val rawState = states[dateStr] ?: DayAvailability.FREE
                        val state = when {
                            highlightedDates != null ->
                                if (dateStr in highlightedDates) DayAvailability.OCCUPIED
                                else DayAvailability.FREE
                            mode == CalendarMode.COUPLE_OWN ->
                                if (dateStr == ownWeddingDate) DayAvailability.OCCUPIED
                                else DayAvailability.FREE
                            else -> rawState
                        }
                        val isToday = currentMonth.year == todayY
                            && currentMonth.month == todayM
                            && dayNum == todayD
                        val isPast = isBeforeToday(
                            currentMonth.year, currentMonth.month, dayNum,
                            todayY, todayM, todayD
                        )
                        val isSelected = dateStr == selectedDate
                        // En modo highlightedDates, hasBookings = esta sombreado
                        val hasBookings = if (highlightedDates != null) {
                            dateStr in highlightedDates
                        } else {
                            bookings[dateStr]?.isNotEmpty() == true
                        }

                        DayCell(
                            day = dayNum,
                            state = state,
                            isToday = isToday,
                            isPast = isPast && !allowPast,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDayTap?.invoke(dateStr, state, hasBookings)
                                // Bloqueo de seleccion solo en modo PICKER.
                                // El admin puede tocar cualquier dia para
                                // ver detalle, sin restriccion.
                                if (mode == CalendarMode.PICKER_FOR_NEW) {
                                    val canSelect = (!isPast || allowPast)
                                        && state != DayAvailability.FULL
                                    if (canSelect) {
                                        onDateSelected?.invoke(dateStr, state)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Leyenda solo en modo PICKER. Admin y novia ven el calendario
        // sin semaforo asi que la leyenda confundiria.
        if (mode == CalendarMode.PICKER_FOR_NEW) {
            Spacer(Modifier.height(8.dp))
            CalendarLegend()
        }
    }
}

/** True si (y,m,d) es estrictamente anterior a (todayY, todayM, todayD). */
private fun isBeforeToday(
    y: Int, m: Int, d: Int,
    todayY: Int, todayM: Int, todayD: Int
): Boolean {
    if (y < todayY) return true
    if (y > todayY) return false
    if (m < todayM) return true
    if (m > todayM) return false
    return d < todayD
}

@Composable
private fun DayCell(
    day: Int,
    state: DayAvailability,
    isToday: Boolean,
    isPast: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected                          -> Gold
        state == DayAvailability.FULL       -> COLOR_FULL_BG
        state == DayAvailability.PARTIAL    -> COLOR_PARTIAL_BG
        state == DayAvailability.OCCUPIED   -> COLOR_OCCUPIED_BG
        else                                 -> Color.Transparent
    }
    val textColor = when {
        isSelected                          -> Cream
        state == DayAvailability.FULL       -> COLOR_FULL_TEXT
        state == DayAvailability.PARTIAL    -> COLOR_PARTIAL_TEXT
        state == DayAvailability.OCCUPIED   -> Brown
        isPast                               -> Sand.copy(alpha = 0.4f)
        else                                 -> Brown
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable(enabled = !isPast || state != DayAvailability.FREE) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(bg, CircleShape)
                .let { if (isToday && !isSelected) it.border(1.5.dp, Gold, CircleShape) else it },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$day",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(COLOR_PARTIAL_BG, "1 boda")
        LegendDot(COLOR_FULL_BG, "2 bodas / no disponible")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier
                .size(12.dp)
                .border(1.5.dp, Gold, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text("Hoy", color = Sand, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Sand, fontSize = 10.sp)
    }
}
