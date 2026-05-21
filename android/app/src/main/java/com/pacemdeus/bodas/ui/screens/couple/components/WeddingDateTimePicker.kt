package com.pacemdeus.bodas.ui.screens.couple.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.validation.DateValidator
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand
import java.util.Calendar

/**
 * Bloque "Fecha y hora": dos selectores en row con icono + dialogs nativos
 * de Material 3 (DatePicker y TimePicker).
 *
 * Restricciones aplicadas:
 *   - DatePicker bloquea fechas pasadas (selectableDates filtra)
 *   - Si se pasa dateMillis = null el campo muestra placeholder "AAAA-MM-DD"
 *
 * @param dateMillis Fecha actualmente seleccionada (UTC millis).
 * @param hour       Hora 0-23. Null si aun no se selecciono.
 * @param minute     Minuto 0-59. Null si aun no se selecciono.
 * @param enabled    Si false, los selectores se desactivan.
 * @param onDateChange Callback con los millis nuevos.
 * @param onTimeChange Callback con (hour, minute) nuevos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeddingDateTimePicker(
    dateMillis: Long?,
    hour: Int?,
    minute: Int?,
    enabled: Boolean,
    onDateChange: (Long) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    /**
     * Si la novia esta editando una boda existente, pasamos su id para
     * que el calendario NO la cuente como conflicto consigo misma.
     */
    excludeWeddingId: String? = null
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateString = dateMillis?.let { DateValidator.toIsoString(it) } ?: ""
    val timeString = if (hour != null && minute != null) {
        "%02d:%02d".format(hour, minute)
    } else ""

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateTimeField(
            label = "Fecha",
            value = dateString.ifBlank { "AAAA-MM-DD" },
            valueAssigned = dateString.isNotBlank(),
            icon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Gold) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            onClick = { showDatePicker = true }
        )
        DateTimeField(
            label = "Hora",
            value = timeString.ifBlank { "HH:MM" },
            valueAssigned = timeString.isNotBlank(),
            icon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = Gold) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            onClick = { showTimePicker = true }
        )
    }

    if (showDatePicker) {
        DatePickerSheet(
            initialMillis = dateMillis ?: DateValidator.todayUtcMillis(),
            excludeWeddingId = excludeWeddingId,
            onDismiss = { showDatePicker = false },
            onConfirm = { millis ->
                onDateChange(millis)
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        TimePickerSheet(
            initialHour = hour ?: 17,
            initialMinute = minute ?: 0,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                onTimeChange(h, m)
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun DateTimeField(
    label: String,
    value: String,
    valueAssigned: Boolean,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (valueAssigned) Gold else Color(0xFFE0D9C8),
                shape = RoundedCornerShape(12.dp)
            )
            .background(Cream, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                label,
                color = Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.size(8.dp))
                Text(
                    value,
                    color = if (valueAssigned) Brown else Sand,
                    fontSize = 15.sp,
                    fontWeight = if (valueAssigned) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** DatePicker que solo permite fechas iguales o posteriores a hoy. */
/**
 * Hoja de seleccion de fecha. Usa el CustomCalendar del proyecto (que
 * consulta /disponibilidad y pinta rojo/ambar segun bodas existentes)
 * en vez del DatePicker nativo de Material, que no tiene contexto de
 * las bodas ya contratadas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initialMillis: Long,
    excludeWeddingId: String?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    // Mes inicial: del valor que ya tenia el campo, o el actual
    val initialIso = DateValidator.toIsoString(initialMillis)
    val initialYear = initialIso.substring(0, 4).toInt()
    val initialMonth = initialIso.substring(5, 7).toInt()
    val initialMonthSimple = com.pacemdeus.bodas.ui.components.YearMonthSimple(
        year = initialYear, month = initialMonth
    )
    val initialSelected = DateValidator.toIsoString(initialMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Elige la fecha",
                color = Brown,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            com.pacemdeus.bodas.ui.components.CustomCalendar(
                initialMonth = initialMonthSimple,
                selectedDate = initialSelected,
                excludeWeddingId = excludeWeddingId,
                onDateSelected = { date, _ ->
                    val millis = DateValidator.fromIsoString(date) ?: return@CustomCalendar
                    onConfirm(millis)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Gold, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Cream
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Hora de la ceremonia", color = Brown, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = GoldSoft,
                        clockDialSelectedContentColor = Cream,
                        clockDialUnselectedContentColor = Brown,
                        selectorColor = Gold,
                        periodSelectorBorderColor = Gold,
                        timeSelectorSelectedContainerColor = Gold,
                        timeSelectorUnselectedContainerColor = GoldSoft,
                        timeSelectorSelectedContentColor = Cream,
                        timeSelectorUnselectedContentColor = Brown
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) { Text("Aceptar", color = Gold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Sand)
            }
        },
        containerColor = Cream
    )
}
