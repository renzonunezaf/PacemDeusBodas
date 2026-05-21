package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.PriceQuote
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.data.validation.DateValidator
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.PriceQuoteCard
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.screens.couple.components.LocationPickerState
import com.pacemdeus.bodas.ui.screens.couple.components.VenueLocationPicker
import com.pacemdeus.bodas.ui.screens.couple.components.WeddingDateTimePicker
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla para crear o editar un evento.
//
// Flujo crear (weddingId = null):
//   1. Usuario llena fecha, hora, nombre, direccion
//   2. Geocoder convierte direccion en pin (debounce 800ms)
//   3. Usuario puede ajustar el pin
//   4. Tappea "Confirmar ubicacion" -> POST /bodas/cotizar -> muestra desglose
//   5. Si algo cambia, el panel se invalida y vuelve a salir el boton
//   6. "Crear evento" -> POST /bodas
//
// Flujo editar (weddingId != null):
//   1. Al abrir se hace GET /bodas/{id} y se precargan TODOS los campos
//      (fecha, hora, nombre, direccion, lat, lng) y se muestra el pin
//      en el mapa.
//   2. La cotizacion aparece ya calculada con los datos actuales.
//   3. Si el usuario cambia algo (incluso el pin), la cotizacion se
//      invalida y vuelve a salir "Confirmar ubicacion".
//   4. "Guardar cambios" -> PUT /bodas/{id} (solo si DRAFT).
//
// Esta pantalla quedo modularizada: el bloque fecha/hora vive en
// WeddingDateTimePicker, el bloque ubicacion en VenueLocationPicker.
// Esta pantalla actua como orquestador delgado.

// Instrumentos minimos que el backend cobra como "incluidos en paquete".
// Se envian a la cotizacion para que el desglose los liste como incluidos.
private val INSTRUMENTOS_MINIMOS = listOf("piano", "voz_femenina")

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun CreateEditWeddingScreen(
    session: UserSession,
    weddingId: String?,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }
    val isEditMode = weddingId != null

    // ─── Estado de fecha/hora ────────────────────────────────
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var timeHour by remember { mutableStateOf<Int?>(null) }
    var timeMinute by remember { mutableStateOf<Int?>(null) }

    // ─── Estado de ubicacion (encapsulado) ───────────────────
    var locationState by remember { mutableStateOf(LocationPickerState()) }

    // ─── Estado de cotizacion ────────────────────────────────
    var quote by remember { mutableStateOf<PriceQuote?>(null) }
    var isQuoting by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var locationConfirmed by remember { mutableStateOf(false) }

    // ─── Estado de carga inicial en modo edit ────────────────
    var isPrefetching by remember { mutableStateOf(isEditMode) }
    var prefetchError by remember { mutableStateOf<String?>(null) }

    // En modo edit, una vez precargada la boda, validamos que su estado
    // permita edicion. Si no, mostramos pantalla bloqueada con mensaje.
    var blockedReason by remember { mutableStateOf<String?>(null) }

    // ─── Estado general ──────────────────────────────────────
    var formError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Estado del bottom sheet que muestra el conflicto de fecha/hora.
    // Cuando el backend rechaza la combinacion fecha+hora, en vez de
    // mostrar texto rojo seco, abrimos un ModalBottomSheet con las
    // horas disponibles como chips tappables.
    data class ConflictInfo(val reason: String, val availableHours: String)
    var conflictInfo by remember { mutableStateOf<ConflictInfo?>(null) }

    // ─── Precarga en modo edit ──────────────────────────────
    // Trae los datos actuales del evento desde el backend y rellena todos
    // los campos incluyendo el pin del mapa. Si todo es valido, dispara
    // automaticamente una cotizacion inicial.
    LaunchedEffect(weddingId) {
        if (!isEditMode) return@LaunchedEffect

        val id = weddingId!!
        apiClient.getBoda(id) { result ->
            when (result) {
                is ApiResult.Success -> {
                    val w = result.data

                    // Bloqueo de edicion: solo se permite editar si el
                    // evento esta en DRAFT (incluyendo Observado, que es
                    // DRAFT con notas del coro).
                    if (!w.isEditable) {
                        blockedReason = when (w.status) {
                            com.pacemdeus.bodas.data.WeddingStatus.SUBMITTED ->
                                "Tu evento esta enviado al coro. Para editarlo, vuelve a la " +
                                    "pantalla principal y usa el boton \"Volver a borrador\"."
                            com.pacemdeus.bodas.data.WeddingStatus.APPROVED,
                            com.pacemdeus.bodas.data.WeddingStatus.CONTRACTED ->
                                "Tu evento ya fue aprobado por el coro. Para hacer cambios " +
                                    "debes solicitar la cancelacion."
                            com.pacemdeus.bodas.data.WeddingStatus.CANCELLATION_REQUESTED ->
                                "Hay una solicitud de cancelacion en revision. No puedes " +
                                    "editar el evento mientras tanto."
                            com.pacemdeus.bodas.data.WeddingStatus.COMPLETED ->
                                "Este evento ya fue completado. No se puede editar."
                            else ->
                                "Este evento no se puede editar en su estado actual."
                        }
                        isPrefetching = false
                        return@getBoda
                    }

                    dateMillis = DateValidator.fromIsoString(w.weddingDate)
                    val (h, m) = parseTimeHM(w.weddingTime)
                    timeHour = h
                    timeMinute = m
                    locationState = LocationPickerState(
                        venueName = w.venueName,
                        venueAddress = w.venueAddress,
                        // Pre-llenamos searchQuery con la direccion para que
                        // el pin se mantenga sin re-buscar. Si el usuario
                        // quiere reubicar, puede editar este campo libremente
                        // sin tocar la direccion oficial.
                        searchQuery = w.venueAddress,
                        pinLat = w.venueLat,
                        pinLng = w.venueLng,
                        locationLabel = if (w.venueLat != null && w.venueLng != null) {
                            "Ubicacion guardada del evento"
                        } else null
                    )
                    if (w.venueLat != null && w.venueLng != null) {
                        // Cotizacion inicial automatica para que el usuario
                        // vea el precio actual sin re-confirmar.
                        runQuote(
                            apiClient,
                            w.venueLat, w.venueLng,
                            w.weddingDate, w.weddingTime
                        ) { q, err ->
                            quote = q
                            quoteError = err
                            locationConfirmed = (q != null)
                            isPrefetching = false
                        }
                    } else {
                        isPrefetching = false
                    }
                }
                is ApiResult.Error -> {
                    prefetchError = result.message
                    isPrefetching = false
                }
                else -> { isPrefetching = false }
            }
        }
    }

    // ─── Invalidacion automatica de la cotizacion ───────────
    // Si cualquier input cambia (pin, fecha, hora) despues de haber
    // confirmado, invalidamos la cotizacion para forzar otro tap.
    LaunchedEffect(
        locationState.pinLat, locationState.pinLng,
        dateMillis, timeHour, timeMinute
    ) {
        if (locationConfirmed) {
            locationConfirmed = false
            quote = null
            quoteError = null
        }
    }

    val dateString = dateMillis?.let { DateValidator.toIsoString(it) } ?: ""
    val timeString = if (timeHour != null && timeMinute != null) {
        "%02d:%02d".format(timeHour, timeMinute)
    } else ""

    /** Trigger del boton "Confirmar ubicacion". */
    fun confirmAndQuote() {
        val lat = locationState.pinLat
        val lng = locationState.pinLng
        if (lat == null || lng == null) {
            quoteError = "Falta el pin en el mapa."
            return
        }
        if (dateString.isBlank() || timeString.isBlank()) {
            quoteError = "Completa fecha y hora antes de confirmar."
            return
        }
        if (DateValidator.errorMessage(dateMillis) != null) {
            quoteError = DateValidator.errorMessage(dateMillis)
            return
        }

        isQuoting = true
        quoteError = null
        runQuote(apiClient, lat, lng, dateString, timeString) { q, err ->
            isQuoting = false
            quote = q
            quoteError = err
            locationConfirmed = (q != null)
        }
    }

    /** Trigger del boton "Crear evento" / "Guardar cambios". */
    fun submit() {
        if (locationState.venueName.isBlank() || locationState.venueAddress.isBlank()) {
            formError = "Completa nombre y direccion del local."
            return
        }
        if (dateString.isBlank() || timeString.isBlank()) {
            formError = "Completa fecha y hora."
            return
        }
        DateValidator.errorMessage(dateMillis)?.let {
            formError = it
            return
        }
        val lat = locationState.pinLat
        val lng = locationState.pinLng
        if (lat == null || lng == null) {
            formError = "Falta confirmar la ubicacion en el mapa."
            return
        }
        formError = null
        isSaving = true

        // Pre-validar conflicto de fecha+hora antes de guardar. Esto
        // muestra mensaje claro al usuario si elige una fecha donde
        // ya hay 2 bodas CONTRACTED o donde no queda ventana de >=5h.
        // En modo edicion excluimos la propia boda para que no se
        // cuente como conflicto consigo misma.
        apiClient.validarConflicto(
            fecha = dateString,
            hora = timeString,
            latitud = lat,
            longitud = lng,
            excludeWeddingId = if (isEditMode) weddingId else null
        ) { vResult ->
            if (vResult is ApiResult.Success && vResult.data.conflict) {
                isSaving = false
                conflictInfo = ConflictInfo(
                    reason = vResult.data.reason,
                    availableHours = vResult.data.availableHours
                )
                return@validarConflicto
            }
            // Sin conflicto -> proceder con create/update
            doSaveWedding(
                isEditMode = isEditMode,
                weddingId = weddingId,
                dateString = dateString,
                timeString = timeString,
                locationState = locationState,
                lat = lat, lng = lng,
                apiClient = apiClient,
                onResult = { ok, errorMsg ->
                    isSaving = false
                    if (ok) onSaved() else formError = errorMsg
                }
            )
        }
    }

    Scaffold(
        topBar = {
            PacemTopBar(
                title = if (isEditMode) "Editar evento" else "Crear evento",
                onBack = onBack
            )
        },
        containerColor = Cream
    ) { padding ->

        if (isPrefetching) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Gold) }
            return@Scaffold
        }

        if (prefetchError != null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    prefetchError ?: "Error al cargar el evento",
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Scaffold
        }

        // Pantalla bloqueada por estado (Submitted, Approved, etc.)
        blockedReason?.let { reason ->
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Edicion no disponible",
                        color = com.pacemdeus.bodas.ui.theme.Brown,
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        reason,
                        color = Sand,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    GoldButton(
                        text = "Volver al inicio",
                        onClick = onBack
                    )
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
            SectionLabel("Fecha y hora")
            WeddingDateTimePicker(
                dateMillis = dateMillis,
                hour = timeHour,
                minute = timeMinute,
                enabled = !isSaving,
                onDateChange = { dateMillis = it; formError = null },
                onTimeChange = { h, m -> timeHour = h; timeMinute = m; formError = null },
                excludeWeddingId = if (isEditMode) weddingId else null
            )
            // Aviso si la fecha es invalida (pasada)
            DateValidator.errorMessage(dateMillis)?.let { msg ->
                if (dateMillis != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Lugar de la ceremonia")
            VenueLocationPicker(
                state = locationState,
                enabled = !isSaving,
                onChange = { locationState = it }
            )

            // ─── Bloque cotizacion ────────────────────────
            val canQuote = locationState.hasValidPin &&
                dateString.isNotBlank() && timeString.isNotBlank() &&
                DateValidator.errorMessage(dateMillis) == null

            if (canQuote && !locationConfirmed) {
                Spacer(Modifier.height(20.dp))
                GoldButton(
                    text = if (isQuoting) "Calculando precio..." else "Confirmar ubicacion",
                    enabled = !isQuoting,
                    onClick = { confirmAndQuote() }
                )
                if (quoteError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No se pudo calcular el precio: $quoteError",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
                Text(
                    "Al confirmar, calcularemos el precio segun la distancia, " +
                        "el trafico estimado y los instrumentos minimos.",
                    color = Sand,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (locationConfirmed && quote != null) {
                Spacer(Modifier.height(20.dp))
                PriceQuoteCard(
                    quote = quote,
                    isLoading = false,
                    title = "Cotizacion estimada",
                    showInstrumentBreakdown = true
                )
                Text(
                    "Incluye el paquete completo del coro (director + piano + voz " +
                        "femenina). Podras agregar mas instrumentos en el ensamble.",
                    color = Sand,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ─── Mensaje de error general ──────────────────
            formError?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(28.dp))
            GoldButton(
                text = when {
                    isSaving -> "Guardando..."
                    isEditMode -> "Guardar cambios"
                    !locationConfirmed -> "Confirma la ubicacion primero"
                    else -> "Crear evento"
                },
                enabled = !isSaving && locationConfirmed,
                onClick = { submit() }
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    // Bottom sheet de conflicto fecha/hora. Solo se muestra cuando
    // conflictInfo != null. Reemplaza al antiguo texto rojo plano.
    conflictInfo?.let { info ->
        com.pacemdeus.bodas.ui.screens.couple.components.ConflictBottomSheet(
            reason = info.reason,
            availableHours = info.availableHours,
            onPickHour = { hora ->
                // Aplica la hora seleccionada al picker y cierra el sheet.
                val parts = hora.split(":")
                if (parts.size == 2) {
                    timeHour = parts[0].toIntOrNull()
                    timeMinute = parts[1].toIntOrNull()
                }
                conflictInfo = null
            },
            onPickAnotherDate = {
                conflictInfo = null
                // El usuario seguira en la pantalla con su fecha actual
                // pero ya vio el aviso; puede abrir el picker de fecha
                // a su gusto.
            },
            onDismiss = { conflictInfo = null }
        )
    }
}

/**
 * Wrapper para la llamada a apiClient.cotizar() que centraliza el manejo
 * de resultado. Devuelve (quote, error) en el callback (uno de los dos es null).
 */
private fun runQuote(
    apiClient: ApiClient,
    lat: Double,
    lng: Double,
    weddingDate: String,
    weddingTime: String,
    onDone: (PriceQuote?, String?) -> Unit
) {
    apiClient.cotizar(
        venueLat = lat,
        venueLng = lng,
        weddingDate = weddingDate,
        weddingTime = weddingTime,
        instrumentSlugs = INSTRUMENTOS_MINIMOS
    ) { result ->
        when (result) {
            is ApiResult.Success -> onDone(result.data, null)
            is ApiResult.Error -> onDone(null, result.message)
            else -> onDone(null, "Estado inesperado")
        }
    }
}

/** Parsea "HH:MM" a (hour, minute). Si el string es invalido devuelve (null, null). */
private fun parseTimeHM(timeStr: String): Pair<Int?, Int?> {
    return try {
        val parts = timeStr.split(":")
        if (parts.size != 2) return Pair(null, null)
        Pair(parts[0].toInt(), parts[1].toInt())
    } catch (e: Exception) {
        Pair(null, null)
    }
}

/**
 * Helper que dispara el create o update segun el modo. Vive fuera del
 * composable para que la pre-validacion de conflicto pueda llamarlo
 * sin re-anidar callbacks dentro del scope del Composable.
 */
private fun doSaveWedding(
    isEditMode: Boolean,
    weddingId: String?,
    dateString: String,
    timeString: String,
    locationState: com.pacemdeus.bodas.ui.screens.couple.components.LocationPickerState,
    lat: Double,
    lng: Double,
    apiClient: com.pacemdeus.bodas.data.network.ApiClient,
    onResult: (success: Boolean, errorMsg: String?) -> Unit
) {
    val venueName = locationState.venueName.trim()
    val venueAddress = locationState.venueAddress.trim()
    if (isEditMode) {
        apiClient.updateBoda(
            idBoda = weddingId!!,
            weddingDate = dateString,
            weddingTime = timeString,
            venueName = venueName,
            venueAddress = venueAddress,
            venueLat = lat,
            venueLng = lng
        ) { result ->
            when (result) {
                is com.pacemdeus.bodas.data.network.ApiResult.Success -> onResult(true, null)
                is com.pacemdeus.bodas.data.network.ApiResult.Error -> onResult(false, result.message)
                else -> onResult(false, "Estado inesperado")
            }
        }
    } else {
        apiClient.createBoda(
            weddingDate = dateString,
            weddingTime = timeString,
            venueName = venueName,
            venueAddress = venueAddress,
            venueLat = lat,
            venueLng = lng
        ) { result ->
            when (result) {
                is com.pacemdeus.bodas.data.network.ApiResult.Success -> onResult(true, null)
                is com.pacemdeus.bodas.data.network.ApiResult.Error -> onResult(false, result.message)
                else -> onResult(false, "Estado inesperado")
            }
        }
    }
}
