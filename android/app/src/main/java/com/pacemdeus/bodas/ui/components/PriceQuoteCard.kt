package com.pacemdeus.bodas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.PriceQuote
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Divider
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

// Tarjeta de cotizacion en vivo. La usan CreateEditWeddingScreen y
// AssemblyScreen para mostrar el desglose de precio que devuelve el
// endpoint POST /bodas/cotizar.
//
// Tres estados:
//   - Loading: spinner + texto "Calculando precio..."
//   - Quote: desglose Base + Instrumentos + Movilidad + Total
//   - Error: mensaje legible con icono de alerta (no implementado aqui,
//     el caller maneja el error mostrando Text con MaterialTheme.error)
//
// El diseno sigue la paleta Pacem Deus: fondo crema, borde dorado suave,
// total destacado en marron oscuro, etiquetas en gold.

/**
 * Tarjeta de cotizacion en vivo con desglose y total destacado.
 *
 * @param quote La cotizacion a mostrar. Null cuando aun no hay datos.
 * @param isLoading True mientras el backend esta calculando.
 * @param title Encabezado de la tarjeta (default: "Cotizacion en vivo").
 * @param showInstrumentBreakdown Si es true, muestra cada instrumento
 *        listado individualmente (util en AssemblyScreen). Si es false,
 *        solo el total de instrumentos (util en CreateEditWedding).
 */
@Composable
fun PriceQuoteCard(
    quote: PriceQuote?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    title: String = "Cotizacion en vivo",
    showInstrumentBreakdown: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(14.dp))
            .padding(2.dp)
            .background(GoldSoft.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.uppercase(),
                    color = Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Gold,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                quote == null && isLoading -> {
                    Text(
                        "Calculando precio segun ubicacion y trafico estimado...",
                        color = Sand,
                        fontSize = 12.sp
                    )
                }
                quote == null -> {
                    Text(
                        "El precio se calculara automaticamente al completar fecha, hora y ubicacion.",
                        color = Sand,
                        fontSize = 12.sp
                    )
                }
                else -> {
                    // Lima vs fuera de Lima
                    if (quote.outsideOfLima) {
                        ZoneTag(
                            text = "FUERA DE LIMA",
                            bg = Color(0xFFF5E6C8),
                            fg = Color(0xFF8C6A1A)
                        )
                    } else {
                        ZoneTag(
                            text = buildZoneTagText(quote.distanceKm, quote.distanceFactor),
                            bg = Color(0xFFD7EAD2),
                            fg = Color(0xFF2E5E1A)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // Paquete coro (incluye director + piano + voz, sin desagregar)
                    PriceRow("Paquete coro base", quote.basePrice)

                    // Instrumentos: separar entre incluidos en base e instrumentos
                    // adicionales facturables. Los incluidos se muestran como
                    // info (texto "Incluido"), los adicionales con su precio.
                    val incluidos = quote.instrumentsDetail.filter { it.includedInBase }
                    val adicionales = quote.instrumentsDetail.filter { !it.includedInBase }

                    if (showInstrumentBreakdown) {
                        incluidos.forEach { ins ->
                            IncludedRow(ins.name)
                        }
                        adicionales.forEach { ins ->
                            PriceRowSecondary("    ${ins.name}", ins.price)
                        }
                    } else if (adicionales.isNotEmpty()) {
                        PriceRow(
                            "Instrumentos adicionales (${adicionales.size})",
                            quote.instrumentsPrice
                        )
                    }

                    // Movilidad: si es 0 mostramos texto "Sin cargo (km < 20)",
                    // si es > 0 mostramos el monto con la distancia.
                    // El desglose de trafico siempre se muestra debajo
                    // (incluso si es S/. 0) para que la novia sepa cuanto
                    // pesa la hora elegida sobre el precio total.
                    if (quote.travelPrice > 0.0) {
                        PriceRow(
                            "Movilidad (${"%.1f".format(quote.distanceKm)} km)",
                            quote.travelPrice
                        )
                        TrafficBreakdownRow(quote.mobilityTraffic)
                    } else {
                        PriceRowText(
                            "Movilidad",
                            "Sin cargo (km < 20)"
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Divider)
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TOTAL",
                            color = Gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "S/. %.2f".format(quote.totalPrice),
                            color = Brown,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Pie con datos de Distance Matrix (solo si hay)
                    if (quote.distanceKm > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildDistanceFooter(quote),
                            color = Sand,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Brown,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "S/. %.2f".format(amount),
            color = Brown,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PriceRowSecondary(label: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Sand,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "S/. %.2f".format(amount),
            color = Sand,
            fontSize = 12.sp
        )
    }
}

/**
 * Fila para mostrar items sin precio (ej. instrumentos incluidos en el
 * paquete base, movilidad sin cargo). Misma alineacion que PriceRow pero
 * el valor derecho es texto descriptivo en lugar de monto.
 */
@Composable
private fun PriceRowText(label: String, valueText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Brown,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            valueText,
            color = Sand,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Item secundario indicando que un instrumento esta incluido en el paquete
 * base (piano y voz_femenina segun el modelo v2). No tiene precio.
 */
@Composable
private fun IncludedRow(name: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "    $name",
            color = Sand,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "Incluido",
            color = Gold,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}

/**
 * Fila secundaria que muestra cuanto de la movilidad viene por trafico
 * (diferencia entre duracion con trafico y duracion normal). Aparece
 * siempre que se cobra movilidad, incluso si el monto es S/. 0
 * (significa que no hubo trafico extra) - asi el usuario entiende que
 * tomamos en cuenta el trafico aunque hoy no influya.
 */
@Composable
private fun TrafficBreakdownRow(trafficAmount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 1.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "+ recargo por trafico",
            color = Sand,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "S/. %.2f".format(trafficAmount),
            color = Sand,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ZoneTag(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Texto del badge de zona segun la distancia. Si km < 20 mostramos solo
 * "LIMA METROPOLITANA"; si km >= 20 agregamos el factor de recargo para
 * que el usuario vea por que el precio crecio.
 */
private fun buildZoneTagText(distanceKm: Double, factor: Double): String {
    return if (factor > 1.0) {
        "LIMA - RECARGO x%.2f".format(factor)
    } else {
        "LIMA METROPOLITANA"
    }
}

private fun buildDistanceFooter(quote: PriceQuote): String {
    val parts = mutableListOf<String>()
    parts += "%.1f km".format(quote.distanceKm)
    if (quote.durationTrafficMinutes > 0) {
        parts += "${quote.durationTrafficMinutes} min con trafico"
    } else if (quote.durationMinutes > 0) {
        parts += "${quote.durationMinutes} min"
    }
    return "Distancia al punto base del coro: " + parts.joinToString(" - ")
}
