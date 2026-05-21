package com.pacemdeus.bodas.ui.screens.couple.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

// Bottom sheet con diseno cuidado para mostrar al usuario que la
// fecha/hora elegida tiene conflicto. Si el backend devuelve horas
// disponibles, las muestra como chips tappables (el callback permite
// que el padre auto-aplique la hora). Si no hay ventanas validas,
// muestra el caso "fecha completa" e invita a elegir otra fecha.
//
// Se descarta deslizando hacia abajo (comportamiento nativo del
// ModalBottomSheet de Material 3) o con el boton "Elegir otra hora".

/**
 * @param reason texto plano del backend con el motivo. Solo se usa la
 *   parte util (antes de "Horas disponibles") porque las horas las
 *   pintamos como chips aparte.
 * @param availableHours CSV de "HH:MM, HH:MM" desde el endpoint.
 *   Vacio si la fecha esta completamente bloqueada.
 * @param onPickHour callback cuando el usuario toca una hora libre.
 *   Recibe la hora "HH:MM" para que el padre la aplique al picker.
 * @param onPickAnotherDate callback para "elegir otra fecha".
 * @param onDismiss cierre por gesture o tap fuera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictBottomSheet(
    reason: String,
    availableHours: String,
    onPickHour: (String) -> Unit,
    onPickAnotherDate: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val horasList = parseHoras(availableHours)
    val esFechaCompleta = horasList.isEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Icono circular grande arriba: cambia segun el caso
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(GoldSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (esFechaCompleta) Icons.Default.EventBusy
                                  else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.height(14.dp))

            // Titulo
            Text(
                text = if (esFechaCompleta) "Esa fecha ya no tiene cupo"
                       else "Necesitamos cambiar la hora",
                color = Brown,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))

            // Mensaje amable
            Text(
                text = if (esFechaCompleta) {
                    "Ya hay otras bodas comprometidas ese dia y no podemos " +
                        "garantizar el servicio del coro. Te invitamos a elegir " +
                        "otra fecha cercana."
                } else {
                    "Hay otra boda contratada ese mismo dia. Para que el coro " +
                        "pueda atenderlas a ambas con calidad, necesitamos un " +
                        "margen entre ellas."
                },
                color = Brown.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // Razon especifica del backend (limpia, sin la parte de
            // "Horas disponibles:" que ya pintamos como chips).
            val reasonClean = limpiarRazon(reason)
            if (reasonClean.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            GoldSoft.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        reasonClean,
                        color = Brown,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // Chips de horas disponibles
            if (!esFechaCompleta) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Horarios libres ese dia",
                    color = Sand,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                // Grid manual con wrap a 3 columnas
                val filas = horasList.chunked(3)
                for (fila in filas) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (hora in fila) {
                            HoraChip(
                                hora = hora,
                                onClick = { onPickHour(hora) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Rellenar columnas vacias para alinear la grid
                        repeat(3 - fila.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Toca un horario para aplicarlo automaticamente.",
                    color = Sand,
                    fontSize = 11.sp
                )
            }

            // Boton primario
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onPickAnotherDate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Cream
                )
            ) {
                Text(
                    text = if (esFechaCompleta) "Elegir otra fecha"
                           else "Elegir otra fecha u hora",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Boton secundario para cerrar sin cambiar nada
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Brown
                )
            ) {
                Text(
                    "Cerrar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Chip tappable de hora. Diseno: pill con fondo gold suave + icono
 * pequeno de reloj.
 */
@Composable
private fun HoraChip(
    hora: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .background(
                color = GoldSoft.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                hora,
                color = Brown,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Parsea el CSV "18:00, 19:00, 20:00" devuelto por el backend en
 * la lista de strings limpias.
 */
private fun parseHoras(csv: String): List<String> {
    if (csv.isBlank()) return emptyList()
    return csv.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/**
 * Quita del mensaje del backend la frase "Horas disponibles: ..."
 * porque las pintamos como chips aparte.
 */
private fun limpiarRazon(reason: String): String {
    val idx = reason.indexOf("Horas disponibles")
    return (if (idx >= 0) reason.substring(0, idx) else reason).trim()
}
