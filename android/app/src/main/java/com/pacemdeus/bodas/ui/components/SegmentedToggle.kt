package com.pacemdeus.bodas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

/**
 * Segmented control sobrio para alternar entre 2 o 3 vistas, p.ej.
 * "Calendario" / "Lista". El seleccionado tiene fondo gold; los demas
 * quedan transparentes con borde fino.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .border(1.dp, GoldSoft, RoundedCornerShape(10.dp))
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selectedIndex
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(
                        if (isSelected) Gold else androidx.compose.ui.graphics.Color.Transparent,
                        // Solo redondeamos las esquinas externas. Como Compose
                        // RoundedCornerShape no soporta direccion, usamos
                        // shape diferente segun posicion.
                        when (i) {
                            0 -> RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)
                            options.lastIndex -> RoundedCornerShape(
                                topEnd = 9.dp, bottomEnd = 9.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    )
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) Cream else Brown,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
