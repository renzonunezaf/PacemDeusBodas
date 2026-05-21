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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.LiturgicalMoment
import com.pacemdeus.bodas.data.PriceQuote
import com.pacemdeus.bodas.data.SetlistItem
import com.pacemdeus.bodas.data.Song
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.PriceQuoteCard
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Divider
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla central de armado del ensamble musical. Refactor v5:
//   - Carga GET /momentos?fecha=X para aplicar restricciones del tiempo
//     liturgico vigente (Cuaresma desactiva Gloria/Aleluya, Adviento
//     desactiva Gloria). Momentos deshabilitados aparecen grayed con la
//     razon visible.
//   - Carga los instrumentos ya contratados de la boda para alimentar
//     el panel de cotizacion en vivo.
//   - Panel "Cotizacion en vivo" sticky al inicio: refleja el precio
//     total actual segun ubicacion + instrumentos seleccionados.
//
// Carga inicial:
//   - Boda activa del couple
//   - Momentos liturgicos con flags habilitado/razon segun fecha boda
//   - Setlist actual
//   - Instrumentos contratados (para la cotizacion)
//
// Mutaciones:
//   - Agregar canto a un momento (apiClient.addSetlistItem)
//   - Quitar canto del setlist (apiClient.removeSetlistItem)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun AssemblyScreen(
    session: UserSession,
    onBack: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenSetlist: () -> Unit = {},
    onOpenInstruments: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var moments by remember { mutableStateOf<List<LiturgicalMoment>>(emptyList()) }
    var setlist by remember { mutableStateOf<List<SetlistItem>>(emptyList()) }
    var contractedInstruments by remember { mutableStateOf<List<String>>(emptyList()) }
    var seasonName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    // Cache de canciones (alimentado a medida que el usuario abre dialogs)
    var songCache by remember { mutableStateOf<Map<String, Song>>(emptyMap()) }

    // Picker de canciones para un momento
    var pickerMoment by remember { mutableStateOf<LiturgicalMoment?>(null) }
    var pickerSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var pickerLoading by remember { mutableStateOf(false) }
    // v05: para momentos con maxSongs > 1 (ej. Fotografias = 4), el picker
    // funciona en modo multi-seleccion. Esto evita que el usuario tenga
    // que abrir el dialog 4 veces, marcar 1 canto y cerrar.
    var pickerSelectedSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pickerIsAdding by remember { mutableStateOf(false) }

    // Cotizacion en vivo
    var quote by remember { mutableStateOf<PriceQuote?>(null) }
    var isQuoting by remember { mutableStateOf(false) }

    // Carga principal: cuando cambia el refreshTick (despues de agregar
    // o quitar un canto), volvemos a pedir todo. Es simple y robusto.
    LaunchedEffect(refreshTick) {
        isLoading = true
        errorMessage = null

        apiClient.listBodas { bodaResult ->
            when (bodaResult) {
                is ApiResult.Success -> {
                    val w = bodaResult.data.firstOrNull()
                    wedding = w
                    if (w == null) {
                        isLoading = false
                        return@listBodas
                    }

                    // Cargamos en paralelo: momentos (con fecha boda para
                    // restricciones), setlist, e instrumentos contratados.
                    var pending = 3
                    val finish = {
                        pending--
                        if (pending == 0) isLoading = false
                    }

                    val fechaBoda = w.weddingDate.takeIf { it.isNotBlank() }
                    apiClient.listMomentos(fecha = fechaBoda) { result ->
                        if (result is ApiResult.Success) moments = result.data
                        else if (result is ApiResult.Error) errorMessage = result.message
                        finish()
                    }
                    apiClient.listSetlist(w.id) { result ->
                        if (result is ApiResult.Success) setlist = result.data
                        else if (result is ApiResult.Error) errorMessage = result.message
                        finish()
                    }
                    apiClient.getBodaInstrumentos(w.id) { result ->
                        if (result is ApiResult.Success) contractedInstruments = result.data
                        finish()
                    }
                }
                is ApiResult.Error -> {
                    isLoading = false
                    errorMessage = bodaResult.message
                }
                else -> {}
            }
        }
    }

    // Cotizacion en vivo cada vez que cambian instrumentos contratados
    // o la fecha/hora/ubicacion.
    val w = wedding
    val lat = w?.venueLat
    val lng = w?.venueLng
    LaunchedEffect(lat, lng, w?.weddingDate, w?.weddingTime, contractedInstruments) {
        if (w == null || lat == null || lng == null ||
            w.weddingDate.isBlank() || w.weddingTime.isBlank()
        ) return@LaunchedEffect

        isQuoting = true
        apiClient.cotizar(
            venueLat = lat,
            venueLng = lng,
            weddingDate = w.weddingDate,
            weddingTime = w.weddingTime,
            instrumentSlugs = contractedInstruments
        ) { result ->
            isQuoting = false
            when (result) {
                is ApiResult.Success -> quote = result.data
                else -> {}
            }
        }
    }

    // Picker: cargar canciones permitidas para el momento abierto.
    LaunchedEffect(pickerMoment) {
        val moment = pickerMoment ?: return@LaunchedEffect
        pickerLoading = true
        pickerSongs = emptyList()
        apiClient.listCanciones(idMomento = moment.id) { result ->
            pickerLoading = false
            when (result) {
                is ApiResult.Success -> {
                    pickerSongs = result.data
                    songCache = songCache + result.data.associateBy { it.id }
                }
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    // ─── Estado: cargando ──────────────────────────────────
    if (isLoading) {
        Scaffold(
            topBar = { PacemTopBar(title = "Ensamble musical", onBack = onBack) },
            containerColor = Cream
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
        }
        return
    }

    // ─── Estado: sin boda creada ───────────────────────────
    val currentWedding = wedding
    if (currentWedding == null) {
        Scaffold(
            topBar = { PacemTopBar(title = "Ensamble musical", onBack = onBack) },
            containerColor = Cream
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                EmptyState("Crea primero tu evento")
            }
        }
        return
    }

    val isEditable = currentWedding.isEditable

    fun addSongToSetlist(momentId: String, songId: String) {
        apiClient.addSetlistItem(currentWedding.id, momentId, songId) { result ->
            when (result) {
                is ApiResult.Success -> {
                    // Actualizacion optimista in-place: agregamos al state
                    // local sin disparar refreshTick. Esto preserva el
                    // scroll position porque no se reconstruye la pantalla.
                    setlist = setlist + result.data
                }
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    fun removeSongFromSetlist(itemId: String) {
        apiClient.removeSetlistItem(currentWedding.id, itemId) { result ->
            when (result) {
                is ApiResult.Success -> {
                    // Misma estrategia: quitamos del state local sin
                    // disparar refresh, preservando scroll.
                    setlist = setlist.filterNot { it.id == itemId }
                }
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Ensamble musical", onBack = onBack) },
        bottomBar = {
            CoupleBottomNav(
                current = CoupleTab.Assembly,
                onSelectHome = onOpenHome,
                onSelectAssembly = {},
                onSelectSetlist = onOpenSetlist
            )
        },
        containerColor = Cream
    ) { padding ->

        // Solo contamos momentos habilitados para el progreso
        val enabledMoments = moments.filter { it.enabled }
        val filledMoments = setlist.map { it.momentId }
            .distinct()
            .filter { mid -> enabledMoments.any { it.id == mid } }
            .count()

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ─── Cotizacion en vivo ──────────────────────────
            PriceQuoteCard(
                quote = quote,
                isLoading = isQuoting,
                title = "Cotizacion en vivo"
            )

            Spacer(Modifier.height(16.dp))

            // ─── Botones de accion ───────────────────────────
            OutlineGoldButton(
                text = "Elegir voces e instrumentos (${contractedInstruments.size})",
                onClick = onOpenInstruments
            )

            Spacer(Modifier.height(16.dp))

            // ─── Header del progreso de momentos ────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MOMENTOS DE LA CEREMONIA",
                    color = Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$filledMoments / ${enabledMoments.size}",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (enabledMoments.isEmpty()) 0f
                    else filledMoments.toFloat() / enabledMoments.size
                },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Gold,
                trackColor = Divider
            )

            // Aviso del tiempo liturgico si hay momentos deshabilitados
            val firstDisabledReason = moments.firstOrNull { !it.enabled }?.disabledReason
            if (firstDisabledReason != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFDF1D6), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = Color(0xFF8C6A1A),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            firstDisabledReason,
                            color = Color(0xFF8C6A1A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Banner discreto cuando el evento no es editable. La pantalla
            // sigue funcionando como viewer (la novia puede ver lo que envio),
            // pero ningun boton de agregar/quitar aparece.
            if (!isEditable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GoldSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Modo solo lectura: tu evento esta en estado " +
                            "\"${currentWedding.statusDisplayName()}\". " +
                            "Para hacer cambios, vuelve al inicio.",
                        color = Brown,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            for ((index, m) in moments.withIndex()) {
                val itemsForMoment = setlist.filter { it.momentId == m.id }
                MomentTimelineItem(
                    index = index + 1,
                    moment = m,
                    itemsForMoment = itemsForMoment,
                    songCache = songCache,
                    editable = isEditable && m.enabled,
                    onClickAdd = { if (m.enabled) pickerMoment = m },
                    onRemove = { itemId -> removeSongFromSetlist(itemId) }
                )
                if (index < moments.size - 1) Spacer(Modifier.height(8.dp))
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))

            // Boton final para confirmar el ensamble y regresar al
            // dashboard de la novia. No persiste nada extra (todos los
            // cambios de instrumentos/setlist ya se guardan en el momento
            // que la novia los hace): solo cierra esta pantalla.
            com.pacemdeus.bodas.ui.components.GoldButton(
                text = "Confirmar ensamble",
                onClick = onBack
            )

            Spacer(Modifier.height(20.dp))
        }
    }

    // ─── Dialog de seleccion de cancion ────────────────────

    pickerMoment?.let { moment ->
        // alreadyPicked: songIds que YA estan en el setlist (vienen del
        // backend). En multi-select queremos que aparezcan marcados y
        // sean desmarcables, no inmutables.
        val alreadyPicked = setlist.filter { it.momentId == moment.id }.map { it.songId }.toSet()
        // Mapa songId -> setlistItemId, para poder borrar del backend
        // cuando el usuario desmarca una cancion que ya estaba.
        val setlistItemBySongId = setlist
            .filter { it.momentId == moment.id }
            .associate { it.songId to it.id }
        val current = alreadyPicked.size
        val remainingSlots = moment.maxSongs - current
        val canStillAdd = remainingSlots > 0
        // v05: si el momento permite varios cantos (ej. Fotografias = 4),
        // el picker funciona en modo multi-seleccion. Asi el usuario marca
        // todos los que quiere y confirma una sola vez.
        val isMultiSelect = moment.maxSongs > 1
        val selectedCount = pickerSelectedSongIds.size
        val canAddMore = isMultiSelect && selectedCount < remainingSlots

        // Funcion para cerrar el dialog limpiando el estado
        fun closePicker() {
            pickerMoment = null
            pickerSelectedSongIds = emptySet()
            pickerIsAdding = false
        }

        // Funcion para agregar todos los seleccionados (modo multi)
        // El ApiClient procesa de a 1, asi que las llamadas son secuenciales
        // pero el state local se actualiza al final via setlist += result.
        fun confirmMultiAdd() {
            if (pickerSelectedSongIds.isEmpty() || pickerIsAdding) return
            pickerIsAdding = true
            val toAdd = pickerSelectedSongIds.toList()
            var pendingCount = toAdd.size
            for (sid in toAdd) {
                apiClient.addSetlistItem(currentWedding.id, moment.id, sid) { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            setlist = setlist + result.data
                        }
                        is ApiResult.Error -> errorMessage = result.message
                        else -> {}
                    }
                    pendingCount--
                    if (pendingCount == 0) closePicker()
                }
            }
        }

        AlertDialog(
            onDismissRequest = { closePicker() },
            title = {
                Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            moment.name,
                            color = Brown,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        // Badge prominente con el contador N/max. Lo
                        // mostramos solo en modo multi-select porque en
                        // single-select no aporta (siempre seria 0/1 o 1/1).
                        if (isMultiSelect) {
                            // Mostrar el total real: ya agregadas en el
                            // setlist + las nuevas que tildaste en este
                            // dialog. Antes mostraba solo las nuevas y
                            // no coincidia con las tildes visibles.
                            val totalReal = current + selectedCount
                            Box(
                                modifier = Modifier
                                    .background(Gold, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "$totalReal/${moment.maxSongs}",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            !canStillAdd -> "Maximo alcanzado: ${moment.maxSongs} canto(s)"
                            isMultiSelect ->
                                "Seleccione hasta ${moment.maxSongs} temas"
                            else ->
                                "Selecciona un canto ($current de ${moment.maxSongs})"
                        },
                        color = Sand,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                when {
                    pickerLoading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Gold)
                        }
                    }
                    pickerSongs.isEmpty() -> {
                        Text("No hay canciones disponibles para este momento.")
                    }
                    else -> {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            for (song in pickerSongs) {
                                val already = song.id in alreadyPicked
                                val isSelectedNow = song.id in pickerSelectedSongIds
                                if (isMultiSelect) {
                                    // En multi-select el tilde refleja
                                    // "esta cancion estara en el setlist
                                    // al confirmar". Aplica tanto a las
                                    // que ya estaban como a las nuevas.
                                    val isChecked = already || isSelectedNow
                                    // canAddMore aplica solo a NUEVAS:
                                    // si quieres marcar una mas, debe
                                    // caber. Si ya estaba marcada o ya
                                    // estaba en setlist, siempre puedes
                                    // desmarcarla.
                                    val canToggle = isChecked || canAddMore
                                    SongPickerRow(
                                        song = song,
                                        already = false, // ya no usamos already para bloquear visual
                                        enabled = canToggle,
                                        isSelected = isChecked,
                                        onClick = {
                                            when {
                                                already -> {
                                                    // Estaba en el setlist: borrar de backend.
                                                    val itemId = setlistItemBySongId[song.id]
                                                    if (itemId != null) {
                                                        removeSongFromSetlist(itemId)
                                                    }
                                                }
                                                isSelectedNow -> {
                                                    // Era una nueva tildada: solo quitar del set local.
                                                    pickerSelectedSongIds = pickerSelectedSongIds - song.id
                                                }
                                                canAddMore -> {
                                                    // Marcar nueva.
                                                    pickerSelectedSongIds = pickerSelectedSongIds + song.id
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    // Modo single: tap agrega + cierra
                                    SongPickerRow(
                                        song = song,
                                        already = already,
                                        enabled = canStillAdd && !already,
                                        onClick = {
                                            if (canStillAdd && !already) {
                                                addSongToSetlist(moment.id, song.id)
                                                closePicker()
                                            }
                                        }
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isMultiSelect && selectedCount > 0) {
                    TextButton(
                        onClick = { confirmMultiAdd() },
                        enabled = !pickerIsAdding
                    ) {
                        Text(
                            if (pickerIsAdding) "Agregando..." else "Ok",
                            color = Gold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    TextButton(onClick = { closePicker() }) {
                        Text("Cerrar", color = Gold)
                    }
                }
            },
            dismissButton = if (isMultiSelect && selectedCount > 0) {
                {
                    TextButton(onClick = { closePicker() }) {
                        Text("Cancelar", color = Sand)
                    }
                }
            } else null
        )
    }
}

/**
 * Item de la linea de tiempo. Si el momento esta deshabilitado por
 * tiempo liturgico, se muestra grayed y no responde a clicks. Si esta
 * lleno, muestra el icono check pero sin boton de agregar.
 */
@Composable
private fun MomentTimelineItem(
    index: Int,
    moment: LiturgicalMoment,
    itemsForMoment: List<SetlistItem>,
    songCache: Map<String, Song>,
    editable: Boolean,
    onClickAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    val hasSongs = itemsForMoment.isNotEmpty()
    val full = itemsForMoment.size >= moment.maxSongs
    val disabledBySeason = !moment.enabled

    val cardBg = if (disabledBySeason) Color(0xFFF5F2EA) else Color.White
    val titleColor = if (disabledBySeason) Sand else Brown

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBg, RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (disabledBySeason) Color(0xFFE5DDC9) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .let {
                if (editable && !full && !disabledBySeason)
                    it.clickable(onClick = onClickAdd) else it
            }
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).background(
                        when {
                            disabledBySeason -> Color(0xFFE5DDC9)
                            hasSongs -> Gold
                            else -> GoldSoft
                        },
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        disabledBySeason -> Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = Sand,
                            modifier = Modifier.size(18.dp)
                        )
                        hasSongs -> Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Cream,
                            modifier = Modifier.size(18.dp)
                        )
                        else -> Text(
                            index.toString(),
                            color = Gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        moment.name,
                        color = titleColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        moment.disabledReason ?: moment.description,
                        color = Sand,
                        fontSize = 11.sp
                    )
                }

                if (editable && !full && !disabledBySeason) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Agregar canto",
                        tint = Gold,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (hasSongs && !disabledBySeason) {
                Spacer(Modifier.height(8.dp))
                for (item in itemsForMoment) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 46.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // v05: priorizar el titulo que viene en el item
                            // desde el backend; el songCache se mantiene
                            // como fallback (por compatibilidad) pero ya
                            // no es la fuente principal, asi se elimina
                            // el flash de "(canto #ID)" al navegar y volver.
                            val displayTitle = item.title
                                ?: songCache[item.songId]?.title
                                ?: "(canto #${item.songId})"
                            val displayAuthor = item.author
                                ?: songCache[item.songId]?.author
                            Text(
                                displayTitle,
                                color = Brown,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (!displayAuthor.isNullOrBlank()) {
                                Text(displayAuthor, color = Sand, fontSize = 11.sp)
                            }
                        }
                        if (editable) {
                            TextButton(onClick = { onRemove(item.id) }) {
                                Text("Quitar", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongPickerRow(
    song: Song,
    already: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    isSelected: Boolean = false   // v05: para modo multi-select
) {
    val borderColor = when {
        already    -> Gold
        isSelected -> Gold
        enabled    -> Color(0xFFE0D9C8)
        else       -> Color(0xFFEEEEEE)
    }
    val bgColor = when {
        already    -> GoldSoft
        isSelected -> GoldSoft.copy(alpha = 0.5f)
        else       -> Color.White
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = Brown, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${song.author}  -  ${languageName(song.language)}", color = Sand, fontSize = 11.sp)
            }
            when {
                already    -> Icon(Icons.Default.Check, contentDescription = null,
                                   tint = Gold, modifier = Modifier.size(20.dp))
                isSelected -> Icon(Icons.Default.Check, contentDescription = null,
                                   tint = Gold, modifier = Modifier.size(20.dp))
                else       -> { /* sin icono */ }
            }
        }
    }
}

private fun languageName(code: String): String = when (code) {
    "ES"   -> "Espanol"
    "LA"   -> "Latin"
    "EN"   -> "Ingles"
    "INST" -> "Instrumental"
    else   -> code
}
