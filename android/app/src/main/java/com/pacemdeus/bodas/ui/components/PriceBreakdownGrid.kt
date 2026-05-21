package com.pacemdeus.bodas.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.PriceQuote
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Gold

/**
 * Grilla horizontal de 4 columnas con el desglose de la cotizacion:
 * BASE, VOCES E INSTR., MOVILIDAD, TRAFICO.
 *
 * Se usa tanto en StickyPriceBar (footer durante eleccion de instrumentos)
 * como en la tarjeta "Inversion total" del Couple Home, para que el
 * desglose se vea igual en toda la app y reemplaza al texto suelto de las
 * versiones anteriores (que apilaba 3-5 lineas grises de breakdown).
 *
 * La columna "TRAFICO" siempre se muestra (incluso si es S/. 0): asi el
 * usuario sabe que el sistema considera trafico aunque hoy no influya, y
 * la grilla mantiene siempre 4 columnas alineadas.
 *
 * Numeros sin decimales (%.0f) por compactness — es referencia rapida, no
 * detalle contable.
 */
@Composable
fun PriceBreakdownGrid(quote: PriceQuote, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        PriceGridColumn(
            label = "BASE",
            amount = quote.basePrice,
            modifier = Modifier.weight(1f)
        )
        PriceGridColumn(
            label = "VOCES E INSTR.",
            amount = quote.instrumentsPrice,
            modifier = Modifier.weight(1f)
        )
        PriceGridColumn(
            label = "MOVILIDAD",
            amount = quote.travelPrice,
            modifier = Modifier.weight(1f)
        )
        PriceGridColumn(
            label = "TRAFICO",
            amount = quote.mobilityTraffic,
            modifier = Modifier.weight(1f),
            // El "+" indica que es un incremento sobre la movilidad base;
            // solo lo agregamos si hay monto real para no ensuciar el grid
            // con "+S/. 0" cuando no hay trafico extra.
            addPlusSign = quote.mobilityTraffic > 0
        )
    }
}

@Composable
private fun PriceGridColumn(
    label: String,
    amount: Double,
    modifier: Modifier = Modifier,
    addPlusSign: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            label,
            color = Gold,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(2.dp))
        val prefix = if (addPlusSign) "+" else ""
        Text(
            "${prefix}S/. %.0f".format(amount),
            color = Brown,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
