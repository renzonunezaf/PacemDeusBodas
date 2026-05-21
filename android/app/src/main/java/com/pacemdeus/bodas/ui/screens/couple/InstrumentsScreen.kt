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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.Instrument
import com.pacemdeus.bodas.data.PriceQuote
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand
import kotlinx.coroutines.delay

// Pantalla de seleccion de instrumentos. Refactor v5:
//   - Piano y Voz Femenina aparecen como OBLIGATORIOS (locked, no se
//     pueden deseleccionar). El backend ya los fuerza al persistir,
//     pero ahora es visualmente claro para el usuario.
//   - Carga los instrumentos actualmente contratados de la boda y los
//     pre-marca como seleccionados (no empieza desde cero).
//   - Panel de cotizacion en vivo: cada cambio en la seleccion lanza
//     POST /bodas/cotizar (con debounce 400ms) y muestra el desglose
//     completo (base + cada instrumento + movilidad + total).

// Slugs de instrumentos obligatorios. Coinciden con la regla del coro
// y con la validacion server-side en post_boda_crear.py.
private val OBLIGATORIOS = setOf("piano", "voz_femenina")

// Delay tras cambiar la seleccion antes de re-cotizar.
private const val QUOTE_DEBOUNCE_MS = 400L

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun InstrumentsScreen(
    session: UserSession,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    // Igual que en SetlistScreen: si se pasa, se carga esa boda
    // especifica (vista admin) y se fuerza modo read-only.
    weddingIdOverride: String? = null
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var instruments by remember { mutableStateOf<List<Instrument>>(emptyList()) }
    var localSelection by remember { mutableStateOf<Set<String>>(OBLIGATORIOS) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Cotizacion en vivo
    var quote by remember { mutableStateOf<PriceQuote?>(null) }
    var isQuoting by remember { mutableStateOf(false) }

    // Carga inicial: boda + catalogo + instrumentos ya contratados.
    // Tenemos 3 llamadas, dos en paralelo (listBodas, listInstrumentos)
    // y una tercera anidada (getBodaInstrumentos) que depende de la boda.
    // El contador `pending` baja cuando termina cada una.
    LaunchedEffect(weddingIdOverride) {
        var pending = 3
        val finish = { pending--; if (pending == 0) isLoading = false }

        // Cuando el admin esta viendo la boda de otra pareja, resuelve
        // la boda por id directo en lugar de buscar la del session.
        val processWedding: (Wedding?) -> Unit = { w ->
            wedding = w
            finish()
            if (w != null) {
                apiClient.getBodaInstrumentos(w.id) { instResult ->
                    when (instResult) {
                        is ApiResult.Success -> {
                            val combined = instResult.data.toSet() + OBLIGATORIOS
                            localSelection = combined
                        }
                        is ApiResult.Error -> errorMessage = instResult.message
                        else -> {}
                    }
                    finish()
                }
            } else {
                finish()
            }
        }

        if (weddingIdOverride != null) {
            apiClient.getBoda(weddingIdOverride) { result ->
                when (result) {
                    is ApiResult.Success -> processWedding(result.data)
                    is ApiResult.Error -> {
                        errorMessage = result.message
                        finish(); finish()
                    }
                    else -> { finish(); finish() }
                }
            }
        } else {
            apiClient.listBodas { result ->
                when (result) {
                    is ApiResult.Success -> processWedding(result.data.firstOrNull())
                    is ApiResult.Error -> {
                        errorMessage = result.message
                        finish(); finish()
                    }
                    else -> { finish(); finish() }
                }
            }
        }

        apiClient.listInstrumentos { result ->
            when (result) {
                is ApiResult.Success -> instruments = result.data
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
            finish()
        }
    }

    // Cotizacion en vivo cada vez que la seleccion cambia.
    val currentBoda = wedding
    val lat = currentBoda?.venueLat
    val lng = currentBoda?.venueLng
    LaunchedEffect(localSelection, lat, lng, currentBoda?.weddingDate, currentBoda?.weddingTime) {
        if (currentBoda == null || lat == null || lng == null ||
            currentBoda.weddingDate.isBlank() || currentBoda.weddingTime.isBlank()
        ) return@LaunchedEffect

        delay(QUOTE_DEBOUNCE_MS)

        isQuoting = true
        apiClient.cotizar(
            venueLat = lat,
            venueLng = lng,
            weddingDate = currentBoda.weddingDate,
            weddingTime = currentBoda.weddingTime,
            instrumentSlugs = localSelection.toList()
        ) { result ->
            isQuoting = false
            when (result) {
                is ApiResult.Success -> quote = result.data
                is ApiResult.Error -> { /* mantener cotizacion previa */ }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Voces e instrumentos", onBack = onBack) },
        bottomBar = {
            // Sticky bar de cotizacion. Siempre visible mientras la
            // novia (o el admin viendo) recorre la lista de
            // instrumentos. Se actualiza en vivo al cambiar la
            // seleccion.
            com.pacemdeus.bodas.ui.components.StickyPriceBar(
                quote = quote,
                isLoading = isQuoting
            )
        },
        containerColor = Cream
    ) { padding ->

        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        val currentWedding = wedding
        if (currentWedding == null) {
            EmptyState("Crea primero tu evento")
            return@Scaffold
        }

        // Modo read-only: aplica si la boda no esta en estado editable
        // (normal para la novia), o si el admin esta visualizandola
        // (weddingIdOverride != null). Para el admin permitimos ver la
        // lista de instrumentos con los checkboxes deshabilitados, en
        // vez de mostrar el mensaje "no editable" que sale para la
        // novia bloqueada.
        val isReadOnly = !currentWedding.isEditable || weddingIdOverride != null

        // Bloqueo de edicion para la novia (no admin): solo se editan
        // instrumentos cuando la boda esta en DRAFT.
        if (!currentWedding.isEditable && weddingIdOverride == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Instrumentos no editables",
                        color = Brown,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.padding(vertical = 6.dp))
                    Text(
                        "Tu evento esta en estado \"${currentWedding.statusDisplayName()}\". " +
                            "No puedes modificar los instrumentos hasta que vuelva a Borrador.",
                        color = Sand,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.padding(vertical = 12.dp))
                    GoldButton(text = "Volver al inicio", onClick = onBack)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionLabel("Voces e instrumentos del coro")
            Text(
                "Piano y Voz Femenina van siempre incluidos (obligatorios). " +
                "Agrega los instrumentos adicionales que deseas para tu ceremonia.",
                color = Sand,
                fontSize = 12.sp
            )

            Spacer(Modifier.padding(vertical = 8.dp))

            for (ins in instruments) {
                val isObligatorio = ins.includedInBasePackage
                val isChecked = ins.id in localSelection

                InstrumentRow(
                    instrument = ins,
                    isChecked = isChecked,
                    isObligatorio = isObligatorio,
                    onToggle = {
                        if (isObligatorio || isReadOnly) return@InstrumentRow
                        localSelection = if (isChecked)
                            localSelection - ins.id
                        else
                            localSelection + ins.id
                    }
                )
                Spacer(Modifier.padding(vertical = 4.dp))
            }

            // Nota: el desglose de precio aparece sticky abajo en el
            // bottomBar (StickyPriceBar). No duplicamos aqui.

            if (errorMessage != null) {
                Spacer(Modifier.padding(vertical = 8.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.padding(vertical = 16.dp))

            // Solo mostramos boton "Guardar" cuando la novia tiene la
            // boda editable. En modo read-only (admin viendo) no aplica.
            if (!isReadOnly) {
                GoldButton(
                    text = if (isSaving) "Guardando..." else "Guardar",
                    enabled = !isSaving,
                    onClick = {
                        isSaving = true
                        errorMessage = null
                        apiClient.updateInstrumentos(
                            currentWedding.id,
                            localSelection.toList()
                        ) { result ->
                            isSaving = false
                            when (result) {
                                is ApiResult.Success -> onSaved()
                                is ApiResult.Error -> errorMessage = result.message
                                else -> {}
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}

/**
 * Fila de un instrumento. Si es obligatorio (`isObligatorio = true`)
 * aparece visualmente bloqueado: icono de candado, fondo dorado suave
 * permanente, checkbox no interactivo. Si es opcional, comporta como
 * un toggle normal.
 */
@Composable
private fun InstrumentRow(
    instrument: Instrument,
    isChecked: Boolean,
    isObligatorio: Boolean,
    onToggle: () -> Unit
) {
    val bgColor = when {
        isObligatorio -> GoldSoft
        isChecked -> GoldSoft
        else -> Color.White
    }
    val borderColor = if (isObligatorio || isChecked) Gold else Color(0xFFE0D9C8)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .let { if (!isObligatorio) it.clickable(onClick = onToggle) else it }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(GoldSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isObligatorio) Icons.Default.Lock else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Gold
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        instrument.name,
                        color = Brown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isObligatorio) {
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Gold, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "OBLIGATORIO",
                                color = Cream,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                Text(
                    if (isObligatorio) "Incluido en paquete base"
                    else "Precio segun distancia",
                    color = Sand,
                    fontSize = 12.sp
                )
            }
            if (isObligatorio) {
                // Icono visual indicando que esta fijo, no checkbox interactivo
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Obligatorio",
                    tint = Gold,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = Gold)
                )
            }
        }
    }
}
