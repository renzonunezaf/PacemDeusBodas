package com.pacemdeus.bodas.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
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

/**
 * Barra inferior compacta con el total de la cotizacion y un desglose
 * de una linea. Pensada para usar como bottomBar del Scaffold cuando
 * la pantalla deja al usuario tomar decisiones que afectan el precio
 * (ej. elegir voces e instrumentos): asi el costo nunca desaparece
 * detras del scroll.
 *
 * Tres estados:
 *   - quote=null && isLoading: spinner + "Calculando precio..."
 *   - quote=null && !isLoading: mensaje neutro
 *   - quote!=null: total + desglose en una sola linea, badge XL si aplica
 */
@Composable
fun StickyPriceBar(
    quote: PriceQuote?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // Sombra suave hacia arriba para diferenciarla visualmente del
    // scroll del contenido. Es lo unico que separa la barra del resto.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp)
            .background(Cream)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column {
            // Top row: label TOTAL ESTIMADO + spinner si esta calculando.
            // El badge "Movilidad XL +20%" se mueve al final del bloque
            // junto al monto, para no competir con el spinner.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TOTAL ESTIMADO",
                    color = Gold,
                    fontSize = 10.sp,
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
                // Badge XL: ahora con texto completo "Movilidad XL +20%"
                // para que la novia entienda a que aplica el recargo.
                if (quote?.isXl == true) {
                    Spacer(Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .background(GoldSoft, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "Movilidad XL +20%",
                            color = Brown,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

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
                        "Completa fecha, hora y ubicacion para ver el precio.",
                        color = Sand,
                        fontSize = 12.sp
                    )
                }
                else -> {
                    Text(
                        "S/. %.2f".format(quote.totalPrice),
                        color = Brown,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 30.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // Linea divisoria sutil entre total y desglose
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Divider)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Grilla de 4 columnas con el desglose
                    PriceBreakdownGrid(quote = quote)
                }
            }
        }
    }
}
