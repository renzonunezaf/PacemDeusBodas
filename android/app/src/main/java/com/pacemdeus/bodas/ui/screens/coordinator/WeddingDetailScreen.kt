package com.pacemdeus.bodas.ui.screens.coordinator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge
import com.pacemdeus.bodas.ui.components.goldTextFieldColors
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Danger
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.Sand
import com.pacemdeus.bodas.ui.theme.Success
import com.pacemdeus.bodas.ui.util.openInMaps

// Detalle del evento para el rol coordinador (admin). Refactor v5:
//   - Botones de Aprobar / Devolver con dialog de observaciones cuando
//     el estado es DRAFT o SUBMITTED.
//   - Boton "Generar contrato" / "Ver contrato" cuando estado es
//     APPROVED, CONTRACTED o COMPLETED.
//   - Tras aprobar/devolver, refresca automaticamente el detalle.
//
// Endpoints consumidos:
//   - GET /bodas/{id}                     -> detalle completo
//   - GET /bodas/{id}/setlist             -> conteo de canciones
//   - POST /admin/bodas/{id}/aprobar      -> aprobar / devolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeddingDetailScreen(
    weddingId: String,
    onBack: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onOpenSetlist: () -> Unit = {},
    onOpenInstruments: () -> Unit = {},
    onAssignPlanner: () -> Unit = {},
    onOpenContract: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var setlistCount by remember { mutableStateOf(0) }
    var photos by remember { mutableStateOf<List<com.pacemdeus.bodas.data.WeddingPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    // Dialogs de aprobar / rechazar / aprobar cancelacion
    var showApproveDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var showCancelApproveDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // Maximizar / eliminar foto. photoToView abre modal de visualizacion,
    // photoToDelete abre dialog de confirmacion antes de borrar en BD+S3.
    var photoToView by remember { mutableStateOf<com.pacemdeus.bodas.data.WeddingPhoto?>(null) }
    var photoToDelete by remember { mutableStateOf<com.pacemdeus.bodas.data.WeddingPhoto?>(null) }
    var isDeletingPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(weddingId, refreshTick) {
        isLoading = true
        apiClient.getBoda(weddingId) { result ->
            when (result) {
                is ApiResult.Success -> {
                    wedding = result.data
                    apiClient.listSetlist(weddingId) { sl ->
                        if (sl is ApiResult.Success) setlistCount = sl.data.size
                        // En paralelo cargamos la galeria completa
                        // (todas las fotos del local) para mostrarla
                        // abajo de los datos del evento.
                        apiClient.listarFotos(weddingId) { fr ->
                            if (fr is ApiResult.Success) photos = fr.data
                            isLoading = false
                        }
                    }
                }
                is ApiResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Detalle del evento", onBack = onBack) },
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
            EmptyState(errorMessage ?: "No se encontro el evento")
            return@Scaffold
        }

        // Decisiones por estado para mostrar botones correctos
        val status = currentWedding.status
        // El admin solo aprueba/devuelve eventos SUBMITTED. En DRAFT la
        // novia aun esta editando, en RETURNED_WITH_NOTES la pelota esta
        // del lado de la novia, no del admin.
        val canApprove = status == WeddingStatus.SUBMITTED
        val canApproveCancellation = status == WeddingStatus.CANCELLATION_REQUESTED
        val canViewContract = status == WeddingStatus.APPROVED ||
                status == WeddingStatus.CONTRACTED ||
                status == WeddingStatus.COMPLETED

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ═══ DETALLE DEL EVENTO (cabecera + lugar fusionados) ═══
            // v07: cabecera y "Lugar" se fusionan porque siempre se
            // ven juntos al abrir el detalle: nombre, fecha, hora,
            // local y opcion de abrir ubicacion en Maps.
            PacemCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentWedding.coupleLabel(),
                            color = Brown,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${currentWedding.weddingDate}  -  ${currentWedding.weddingTime}",
                            color = Sand,
                            fontSize = 12.sp
                        )
                    }
                    StatusBadge(currentWedding.status)
                }
                Spacer(Modifier.padding(vertical = 8.dp))
                Text(
                    currentWedding.venueName,
                    color = Brown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(currentWedding.venueAddress, color = Sand, fontSize = 12.sp)
                if (currentWedding.venueLat != null && currentWedding.venueLng != null) {
                    Spacer(Modifier.padding(vertical = 6.dp))
                    OutlineGoldButton(
                        text = "Abrir ubicacion en Maps",
                        onClick = {
                            openInMaps(
                                context,
                                currentWedding.venueLat,
                                currentWedding.venueLng,
                                currentWedding.venueName
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ═══ BANNER INFORMATIVO POR ESTADO ════════════════════
            // Da contexto al admin sobre que esta pasando con la boda
            // segun el estado actual, especialmente cuando NO hay
            // acciones disponibles desde el detalle.
            AdminStatusBanner(status = currentWedding.status)

            // ═══ ENSAMBLE MUSICAL ════════════════════════════════
            PacemCard {
                SectionLabel("Ensamble musical", icon = Icons.Default.LibraryMusic)
                Text(
                    "$setlistCount cancion(es) en el setlist",
                    color = Brown,
                    fontSize = 13.sp
                )
                Spacer(Modifier.padding(vertical = 8.dp))
                OutlineGoldButton(
                    text = "Ver setlist",
                    onClick = onOpenSetlist
                )
                Spacer(Modifier.padding(vertical = 6.dp))
                OutlineGoldButton(
                    text = "Ver voces e instrumentos",
                    onClick = onOpenInstruments
                )
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ═══ WEDDING PLANNER ═════════════════════════════════
            PacemCard {
                SectionLabel("Wedding planner", icon = Icons.Default.Person)
                if (currentWedding.plannerName != null) {
                    Text(
                        currentWedding.plannerName,
                        color = Brown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (currentWedding.plannerId != null) {
                    Text("Planner asignado", color = Brown, fontSize = 14.sp)
                } else {
                    Text("Sin planner asignado", color = Sand, fontSize = 13.sp)
                }
                Spacer(Modifier.padding(vertical = 6.dp))
                OutlineGoldButton(
                    text = if (currentWedding.plannerId == null) "Asignar planner" else "Cambiar planner",
                    onClick = onAssignPlanner
                )
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ═══ FOTOS DEL LOCAL ═════════════════════════════════
            // Renzo pidio bajarlas al 4to lugar (eran las primeras en v06).
            PacemCard {
                SectionLabel("Fotos del local", icon = Icons.Default.PhotoCamera)
                if (photos.isEmpty()) {
                    Text(
                        "Aun no hay fotos del local",
                        color = Sand,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.padding(vertical = 8.dp))
                } else {
                    Text(
                        "${photos.size} foto(s) registrada(s)",
                        color = Brown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.padding(vertical = 6.dp))
                    for ((idx, ph) in photos.withIndex()) {
                        PhotoCard(
                            photo = ph,
                            onTap = { photoToView = ph },
                            onDelete = { photoToDelete = ph }
                        )
                        if (idx < photos.size - 1) Spacer(Modifier.padding(vertical = 5.dp))
                    }
                    Spacer(Modifier.padding(vertical = 8.dp))
                }
                OutlineGoldButton(
                    text = if (photos.isEmpty()) "Agregar fotos del local"
                           else "Agregar mas fotos",
                    onClick = onOpenGallery
                )
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            // ═══ INVERSION TOTAL ═════════════════════════════════
            PacemCard {
                SectionLabel("Inversion total", icon = Icons.Default.AttachMoney)
                Text(
                    "S/. ${"%.2f".format(currentWedding.totalPrice)}",
                    color = Brown,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Base S/. ${"%.0f".format(currentWedding.basePrice)} + " +
                            "Instrumentos S/. ${"%.0f".format(currentWedding.instrumentsPrice)} + " +
                            "Movilidad S/. ${"%.0f".format(currentWedding.travelPrice)}",
                    color = Sand,
                    fontSize = 11.sp
                )
            }

            // ═══ OBSERVACIONES (si existen) ══════════════════════
            if (!currentWedding.notes.isNullOrBlank()) {
                Spacer(Modifier.padding(vertical = 6.dp))
                PacemCard {
                    SectionLabel("Observaciones", icon = Icons.AutoMirrored.Filled.Comment)
                    Text(
                        currentWedding.notes,
                        color = Brown,
                        fontSize = 12.sp
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.padding(vertical = 8.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            // ═══ ACCIONES DECISIONALES ═══════════════════════════
            // Bloque final, separado visualmente por un Spacer mas
            // grande. Aprobar = GoldButton (accion primaria positiva).
            // Devolver = OutlineGoldButton (accion secundaria que pide
            // observaciones antes). Mantienen la paleta gold/brown sin
            // verdes ni rojos chillones que rompen la estetica del
            // resto de la app.
            if (canApprove) {
                Spacer(Modifier.padding(vertical = 20.dp))
                GoldButton(
                    text = "Aprobar evento",
                    onClick = { showApproveDialog = true },
                    enabled = !isProcessing
                )
                Spacer(Modifier.padding(vertical = 6.dp))
                OutlineGoldButton(
                    text = "Devolver con anotaciones",
                    onClick = { showRejectDialog = true }
                )
            }

            if (canApproveCancellation) {
                Spacer(Modifier.padding(vertical = 20.dp))
                GoldButton(
                    text = "Aprobar cancelacion",
                    onClick = { showCancelApproveDialog = true },
                    enabled = !isProcessing
                )
            }

            if (canViewContract) {
                Spacer(Modifier.padding(vertical = if (canApprove) 6.dp else 20.dp))
                GoldButton(
                    text = if (status == WeddingStatus.APPROVED) "Generar contrato" else "Ver contrato",
                    onClick = onOpenContract
                )
            }

            Spacer(Modifier.padding(vertical = 16.dp))
        }
    }

    // ─── Dialog: Aprobar ─────────────────────────────────────

    if (showApproveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showApproveDialog = false },
            title = { Text("Aprobar evento", color = Brown, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Al aprobar, el evento pasa al estado APROBADO y se podra generar el contrato. " +
                        "Esta accion es irreversible.",
                    color = Brown
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isProcessing = true
                        apiClient.aprobarBoda(weddingId, aprobada = true, comentario = null) { result ->
                            isProcessing = false
                            showApproveDialog = false
                            when (result) {
                                is ApiResult.Success -> refreshTick++
                                is ApiResult.Error -> errorMessage = result.message
                                else -> {}
                            }
                        }
                    },
                    enabled = !isProcessing
                ) { Text("Aprobar", color = Success, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showApproveDialog = false },
                    enabled = !isProcessing
                ) { Text("Cancelar", color = Sand) }
            },
            containerColor = Cream
        )
    }

    // ─── Dialog: Aprobar cancelacion ─────────────────────────

    if (showCancelApproveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showCancelApproveDialog = false },
            title = { Text("Aprobar cancelacion", color = Brown, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Al aprobar, el evento pasa al estado CANCELADO y la fecha y hora " +
                        "quedan liberadas para otras bodas. Esta accion es irreversible.",
                    color = Brown
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isProcessing = true
                        apiClient.aprobarCancelacion(weddingId) { result ->
                            isProcessing = false
                            showCancelApproveDialog = false
                            when (result) {
                                is ApiResult.Success -> refreshTick++
                                is ApiResult.Error -> errorMessage = result.message
                                else -> {}
                            }
                        }
                    },
                    enabled = !isProcessing
                ) { Text("Aprobar cancelacion", color = Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelApproveDialog = false },
                    enabled = !isProcessing
                ) { Text("Volver", color = Sand) }
            },
            containerColor = Cream
        )
    }

    // ─── Dialog: Devolver con observaciones ──────────────────

    if (showRejectDialog) {
        var rejectNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) showRejectDialog = false },
            title = { Text("Devolver con anotaciones", color = Brown, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Si hiciste cambios al setlist, instrumentos o ubicacion, " +
                            "explicalos aqui. La pareja vera tus anotaciones, el " +
                            "precio nuevo y un comparativo del antes/despues, y podra " +
                            "aceptar o rechazar.",
                        color = Brown,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.padding(vertical = 6.dp))
                    OutlinedTextField(
                        value = rejectNotes,
                        onValueChange = { rejectNotes = it },
                        label = { Text("Anotaciones para la pareja") },
                        placeholder = { Text("Ej. Cambiamos el ofertorio por uno mas corto...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        shape = RoundedCornerShape(10.dp),
                        colors = goldTextFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rejectNotes.isBlank()) return@TextButton
                        isProcessing = true
                        apiClient.devolverConAnotaciones(
                            idBoda = weddingId,
                            textoNota = rejectNotes.trim()
                        ) { result ->
                            isProcessing = false
                            showRejectDialog = false
                            when (result) {
                                is ApiResult.Success -> refreshTick++
                                is ApiResult.Error -> errorMessage = result.message
                                else -> {}
                            }
                        }
                    },
                    enabled = !isProcessing && rejectNotes.isNotBlank()
                ) { Text("Devolver", color = Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRejectDialog = false },
                    enabled = !isProcessing
                ) { Text("Cancelar", color = Sand) }
            },
            containerColor = Cream
        )
    }

    // ─── Dialog: Maximizar foto ───────────────────────────────────
    // Muestra la foto ampliada con su caption y autor. Read-only.

    photoToView?.let { p ->
        AlertDialog(
            onDismissRequest = { photoToView = null },
            confirmButton = {
                TextButton(onClick = { photoToView = null }) {
                    Text("Cerrar", color = Gold, fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column {
                    coil.compose.AsyncImage(
                        model = p.url,
                        contentDescription = "Foto del local",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(320.dp)
                    )
                    if (!p.caption.isNullOrBlank()) {
                        Spacer(Modifier.padding(vertical = 6.dp))
                        Text(
                            "\"${p.caption}\"",
                            color = Brown,
                            fontSize = 14.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    val rolText = when (p.authorRole) {
                        "COUPLE"          -> "Pareja"
                        "ADMIN"           -> "Coro"
                        "WEDDING_PLANNER" -> "Wedding planner"
                        else              -> p.authorRole ?: "Autor desconocido"
                    }
                    val nombre = p.authorName?.takeIf { it.isNotBlank() }
                    Spacer(Modifier.padding(vertical = 4.dp))
                    Text(
                        if (nombre != null) "— $nombre  ·  $rolText" else "— $rolText",
                        color = Sand,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            containerColor = Cream
        )
    }

    // ─── Dialog: Confirmar eliminacion de foto ──────────────────────
    // El admin puede eliminar cualquier foto. El backend valida acceso
    // (ADMIN tiene permiso total) y borra DB + S3.

    photoToDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!isDeletingPhoto) photoToDelete = null },
            title = { Text("Quitar foto", color = Brown, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Esta foto se eliminara permanentemente del evento. Continuar?",
                    color = Brown
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingPhoto,
                    onClick = {
                        isDeletingPhoto = true
                        apiClient.eliminarFoto(weddingId, p.id) { result ->
                            isDeletingPhoto = false
                            photoToDelete = null
                            when (result) {
                                is ApiResult.Success -> refreshTick++
                                is ApiResult.Error -> errorMessage = result.message
                                else -> {}
                            }
                        }
                    }
                ) { Text("Quitar", color = Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingPhoto,
                    onClick = { photoToDelete = null }
                ) { Text("Cancelar", color = Sand) }
            },
            containerColor = Cream
        )
    }
}

/**
 * Tarjeta con una foto del local + su comentario y autor. El admin
 * puede maximizarla tocando la imagen y eliminarla con el boton X de
 * la esquina superior derecha.
 */
@Composable
private fun PhotoCard(
    photo: com.pacemdeus.bodas.data.WeddingPhoto,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Color(0xFFF7EFDF),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        // El ImageRequest se memoiza por url. Sin remember, cada
        // recomposicion construia un ImageRequest nuevo, lo que
        // forzaba a rememberAsyncImagePainter a recrearse y reiniciar
        // el load desde Loading. Para una imagen recien subida que
        // todavia no esta en cache, eso genera un loop visible de
        // "loading -> imagen -> loading" hasta que Coil termina de
        // bajarla y la pone en cache.
        val imageRequest = remember(photo.url) {
            coil.request.ImageRequest.Builder(context)
                .data(photo.url)
                .crossfade(true)
                .listener(
                    onStart = { _ ->
                        android.util.Log.d("PhotoCard", "START ${photo.url}")
                    },
                    onError = { _, result ->
                        val cause = result.throwable
                        android.util.Log.e(
                            "PhotoCard",
                            "ERROR cargando ${photo.url}: ${cause::class.simpleName}: ${cause.message}",
                            cause
                        )
                    },
                    onSuccess = { _, result ->
                        android.util.Log.d(
                            "PhotoCard",
                            "OK ${photo.url} - source=${result.dataSource}"
                        )
                    }
                )
                .build()
        }
        val painter = coil.compose.rememberAsyncImagePainter(model = imageRequest)
        val state = painter.state
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFEDE5D2))
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = "Foto del local",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            when (state) {
                is coil.compose.AsyncImagePainter.State.Loading ->
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Gold,
                        strokeWidth = 2.dp
                    )
                is coil.compose.AsyncImagePainter.State.Error -> {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No se pudo cargar",
                            color = Brown,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            state.result.throwable.message?.take(80) ?: "Error desconocido",
                            color = Sand,
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> Unit
            }

            // Boton de eliminar en esquina superior derecha. Fondo negro
            // translucido para legibilidad sobre cualquier fondo de foto.
            androidx.compose.material3.IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .background(
                        androidx.compose.ui.graphics.Color(0xCC000000),
                        androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "Eliminar foto",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (!photo.caption.isNullOrBlank()) {
            Spacer(Modifier.padding(vertical = 4.dp))
            Text(
                photo.caption!!,
                color = Brown,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        val rolText = when (photo.authorRole) {
            "COUPLE" -> "Pareja"
            "ADMIN" -> "Coro"
            "WEDDING_PLANNER" -> "Wedding planner"
            else -> photo.authorRole ?: "Autor desconocido"
        }
        val nombre = photo.authorName?.takeIf { it.isNotBlank() }
        Spacer(Modifier.padding(vertical = 2.dp))
        Text(
            if (nombre != null) "Por $nombre  -  $rolText" else rolText,
            color = Sand,
            fontSize = 11.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

/**
 * Banner informativo para el admin que describe el estado actual de la
 * boda y de quien depende la siguiente accion. Muestra al admin por que
 * NO hay botones disponibles cuando la boda no esta en SUBMITTED.
 *
 * Para SUBMITTED no se muestra banner porque el admin tiene los botones
 * de Aprobar / Devolver directamente.
 */
@Composable
private fun AdminStatusBanner(status: WeddingStatus) {
    val mensaje = when (status) {
        WeddingStatus.DRAFT ->
            "La novia está editando su evento. Cuando esté lista lo enviará para tu revisión."
        WeddingStatus.RETURNED_WITH_NOTES ->
            "Devuelto con anotaciones. La novia debe corregir y reenviar."
        WeddingStatus.APPROVED ->
            "Aprobado. Puedes generar el contrato cuando estén listos para firmar."
        WeddingStatus.CONTRACTED ->
            "Contrato firmado. Evento confirmado."
        WeddingStatus.CANCELLATION_REQUESTED ->
            "La novia solicitó cancelar este evento. Aprueba la cancelación para liberar la fecha y hora."
        WeddingStatus.CANCELLED ->
            "Evento cancelado. Fecha y hora liberadas."
        WeddingStatus.COMPLETED ->
            "Evento completado."
        WeddingStatus.SUBMITTED -> null
    } ?: return

    PacemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(
                mensaje,
                color = Brown,
                fontSize = 13.sp
            )
        }
    }
    Spacer(Modifier.padding(vertical = 6.dp))
}
