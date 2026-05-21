package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
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
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.local.CachedSetlistItem
import com.pacemdeus.bodas.data.local.OfflineWeddingCache
import com.pacemdeus.bodas.data.local.SetlistDatabase
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

// Vista del setlist completo: viewer SOLO LECTURA con formato cuidado.
//
// Muestra:
//   - Resumen: total de cantos y momentos cubiertos
//   - Cada momento como una tarjeta con su canto asignado (titulo + autor)
//   - Bloque de instrumentos contratados (chips)
//   - Boton "Editar ensamble" solo cuando el evento esta en Borrador
//
// La edicion (agregar/quitar cantos) NO se hace aqui. Esta pantalla es
// puramente informativa. Para modificar el ensamble la novia debe ir a
// AssemblyScreen via el boton "Editar".
//
// Tambien implementa HU-06 (offline): muestra cache local mientras
// sincroniza con el backend. Si no hay red, sigue mostrando el cache.

/** Color de fondo de las tarjetas de cancion: cream con un toque mas frio. */
private val SongCardBg = Color(0xFFF7EFDF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistScreen(
    session: UserSession,
    onBack: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenAssembly: () -> Unit = {},
    // Si se pasa, se carga esa boda especifica en lugar de la del session.
    // Lo usa el coordinador (admin) para revisar el ensamble de una boda
    // ajena sin tener que abrir la app de la pareja. Cuando viene, la
    // pantalla se fuerza a modo solo-lectura (sin botones de editar ni
    // bottomBar de navegacion couple).
    weddingIdOverride: String? = null
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }
    val database = remember { SetlistDatabase.get(context) }
    val offlineCache = remember { OfflineWeddingCache.get(context) }

    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var items by remember { mutableStateOf<List<CachedSetlistItem>>(emptyList()) }
    var instruments by remember { mutableStateOf<List<Instrument>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isOffline by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ─── Carga: cache primero, luego sincronizacion ─────────
    LaunchedEffect(weddingIdOverride) {
        isLoading = true
        errorMessage = null

        // Dos rutas:
        //   - Si viene weddingIdOverride (admin viendo una boda),
        //     resolvemos por id directo via getBoda.
        //   - Si no, listamos las bodas del usuario actual (couple)
        //     y tomamos la primera (couple tiene a lo mas una).
        val handleWedding: (Wedding?) -> Unit = { w ->
            wedding = w
            if (w == null) {
                isLoading = false
            } else {
                val cached = database.loadSetlist(w.id)
                if (cached.isNotEmpty()) {
                    items = cached
                    isLoading = false
                }
                syncSetlistFromBackend(
                    apiClient = apiClient,
                    database = database,
                    wedding = w,
                    cachedFallback = cached,
                    onSuccess = { freshItems, freshInstruments ->
                        items = freshItems
                        instruments = freshInstruments
                        isOffline = false
                        isLoading = false
                    },
                    onOffline = {
                        isOffline = true
                        isLoading = false
                        if (cached.isEmpty()) {
                            errorMessage = "Sin conexion y sin datos guardados"
                        }
                    }
                )
            }
        }

        if (weddingIdOverride != null) {
            apiClient.getBoda(weddingIdOverride) { result ->
                when (result) {
                    is ApiResult.Success -> handleWedding(result.data)
                    is ApiResult.Error -> {
                        isOffline = true
                        isLoading = false
                        errorMessage = result.message
                    }
                    else -> {}
                }
            }
        } else {
            apiClient.listBodas { bodaResult ->
                when (bodaResult) {
                    is ApiResult.Success -> handleWedding(bodaResult.data.firstOrNull())
                    is ApiResult.Error -> {
                        // Sin red: caemos al cache offline. Si hay una boda
                        // cacheada, la usamos para resolver el setlist desde
                        // SQLite. Si no hay nada cacheado, mostramos el
                        // empty state habitual.
                        val cachedWedding = offlineCache.loadActiveWedding()
                        if (cachedWedding != null) {
                            isOffline = true
                            wedding = cachedWedding
                            val cached = database.loadSetlist(cachedWedding.id)
                            items = cached
                            isLoading = false
                            if (cached.isEmpty()) {
                                errorMessage = "Sin conexion y no hay setlist guardado todavia. Conectate al menos una vez para descargarlo."
                            }
                        } else {
                            isOffline = true
                            isLoading = false
                            errorMessage = bodaResult.message
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    val currentWedding = wedding
    val isEditable = currentWedding?.isEditable == true && weddingIdOverride == null

    Scaffold(
        topBar = {
            PacemTopBar(
                title = if (isOffline) "Mi setlist (offline)" else "Mi setlist",
                onBack = onBack
            )
        },
        bottomBar = {
            // Cuando un admin esta viendo el setlist de otra boda no
            // mostramos el bottom nav de couple (no aplica para su rol).
            if (weddingIdOverride == null) {
                CoupleBottomNav(
                    current = CoupleTab.Setlist,
                    onSelectHome = onOpenHome,
                    onSelectAssembly = onOpenAssembly,
                    onSelectSetlist = {}
                )
            }
        },
        containerColor = Cream
    ) { padding ->

        if (isLoading && items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        if (currentWedding == null && items.isEmpty()) {
            Box(modifier = Modifier.padding(padding)) {
                EmptyState("Crea primero tu evento")
            }
            return@Scaffold
        }

        val itemsByMoment = items.groupBy { it.idMomento }
        val momentsCovered = itemsByMoment.size

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Banner offline (discreto)
            if (isOffline) {
                OfflineBanner()
                Spacer(Modifier.height(12.dp))
            }

            // ─── Header con stats ─────────────────────────
            SummaryHeader(
                totalCantos = items.size,
                momentos = momentsCovered
            )

            Spacer(Modifier.height(20.dp))

            // ─── Setlist por momento ──────────────────────
            if (items.isEmpty()) {
                EmptyMessage()
            } else {
                val orderedMoments = items
                    .sortedBy { it.ordenMomento }
                    .map { Triple(it.idMomento, it.nombreMomento, it.ordenMomento) }
                    .distinct()

                ContinuousSetlist(
                    orderedMoments = orderedMoments,
                    itemsByMoment = itemsByMoment
                )
            }

            // ─── Instrumentos contratados ─────────────────
            if (instruments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                InstrumentsSection(instruments = instruments)
            }

            // ─── Mensaje de error ────────────────────────
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            // ─── Compartir setlist como PDF ──────────────
            // Disponible siempre que hay al menos un canto en la lista
            // (no requiere ser editable: incluso bodas ya aprobadas
            // pueden compartir su setlist final con familiares, etc.).
            if (items.isNotEmpty() && currentWedding != null) {
                Spacer(Modifier.height(24.dp))
                var isDownloadingPdf by remember { mutableStateOf(false) }
                var downloadPdfError by remember { mutableStateOf<String?>(null) }
                OutlineGoldButton(
                    text = if (isDownloadingPdf) "Generando setlist..."
                           else "Compartir setlist (PDF)",
                    onClick = {
                        if (isDownloadingPdf) return@OutlineGoldButton
                        isDownloadingPdf = true
                        downloadPdfError = null
                        apiClient.getSetlistPdf(currentWedding.id) { result ->
                            isDownloadingPdf = false
                            when (result) {
                                is ApiResult.Success -> compartirSetlistPdf(
                                    context = context,
                                    filename = result.data.filename,
                                    bytes = result.data.bytes
                                )
                                is ApiResult.Error -> downloadPdfError = result.message
                                else -> {}
                            }
                        }
                    }
                )
                if (downloadPdfError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        downloadPdfError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }

            // ─── Boton editar (solo si editable) ──────────
            if (isEditable) {
                Spacer(Modifier.height(16.dp))
                GoldButton(
                    text = "Editar ensamble",
                    onClick = onOpenAssembly
                )
                Text(
                    "Te llevara a la pantalla donde eliges los cantos para cada momento de la ceremonia.",
                    color = Sand,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Sub-composables del viewer
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SummaryHeader(totalCantos: Int, momentos: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GoldSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "TU CEREMONIA",
                    color = Sand,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (totalCantos == 0) "Aun no hay cantos elegidos"
                    else "$totalCantos cantos en $momentos momentos",
                    color = Brown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            BigNumberCircle(number = totalCantos)
        }
    }
}

@Composable
private fun BigNumberCircle(number: Int) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(Gold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            number.toString(),
            color = Cream,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Setlist renderizado como UNA SOLA lista continua, no como bloques
 * por momento. Los momentos aparecen como sub-encabezados internos
 * (pequenas etiquetas en gold), pero todas las canciones fluyen en
 * la misma caja sin cortes visuales fuertes. La idea es leerlo como
 * el orden de la ceremonia, no como un listado de opciones.
 */
@Composable
private fun ContinuousSetlist(
    orderedMoments: List<Triple<String, String, Int>>,
    itemsByMoment: Map<String, List<CachedSetlistItem>>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SongCardBg, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp)
    ) {
        Column {
            var globalIndex = 0
            for ((momentIdx, momentTriple) in orderedMoments.withIndex()) {
                val (momentId, momentName, _) = momentTriple
                val songsInMoment = itemsByMoment[momentId] ?: continue

                // Sub-header del momento: etiqueta pequena en gold, sin
                // linea ni padding extra que parta visualmente la lista.
                Text(
                    momentName.uppercase(),
                    color = Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(
                        top = if (momentIdx == 0) 8.dp else 10.dp,
                        bottom = 2.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                )

                for (song in songsInMoment) {
                    globalIndex++
                    SongRow(index = globalIndex, song = song)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * Fila de un canto en una sola linea: numero tenue + titulo en bold +
 * autor en italic mas claro separado por en-dash. Densidad alta para
 * que un setlist completo entre sin scroll en la mayoria de bodas.
 */
@Composable
private fun SongRow(index: Int, song: CachedSetlistItem) {
    // Defensa contra strings "null" o vacios que puedan venir del cache
    // local desactualizado.
    val titulo = song.tituloCancion
        .takeIf { it.isNotBlank() && it != "null" }
        ?: "(canto sin titulo)"
    val autor = song.autorCancion
        .takeIf { it.isNotBlank() && it != "null" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${index}.",
            color = Sand,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(22.dp)
        )
        Text(
            titulo,
            color = Brown,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (autor != null) {
            Text(
                "  -  $autor",
                color = Sand,
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InstrumentsSection(instruments: List<Instrument>) {
    Text(
        "VOCES E INSTRUMENTOS",
        color = Gold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(12.dp))
            .border(1.dp, GoldSoft, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            for ((idx, ins) in instruments.withIndex()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Gold, CircleShape)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        ins.name,
                        color = Brown,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (ins.includedInBasePackage) {
                        Text(
                            "INCLUIDO",
                            color = Sand,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                if (idx < instruments.size - 1) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0E8D2), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = Sand,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Mostrando datos guardados (sin conexion)",
            color = Sand,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun EmptyMessage() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Aun no hay cantos elegidos",
                color = Brown,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap \"Editar ensamble\" para empezar a armar tu ceremonia.",
                color = Sand,
                fontSize = 12.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Helper de sincronizacion (fuera del composable para legibilidad)
// ═══════════════════════════════════════════════════════════════════

/**
 * Pide al backend setlist + momentos + canciones + instrumentos de la
 * boda, los combina, persiste en cache y entrega la lista lista para
 * renderear. Si cualquier llamada falla, dispara onOffline.
 */
private fun syncSetlistFromBackend(
    apiClient: ApiClient,
    database: SetlistDatabase,
    wedding: Wedding,
    cachedFallback: List<CachedSetlistItem>,
    onSuccess: (List<CachedSetlistItem>, List<Instrument>) -> Unit,
    onOffline: () -> Unit
) {
    apiClient.listSetlist(wedding.id) { setlistResult ->
        if (setlistResult !is ApiResult.Success) {
            onOffline()
            return@listSetlist
        }

        apiClient.listMomentos { momResult ->
            if (momResult !is ApiResult.Success) {
                onOffline()
                return@listMomentos
            }

            apiClient.listCanciones { songResult ->
                if (songResult !is ApiResult.Success) {
                    onOffline()
                    return@listCanciones
                }

                apiClient.listInstrumentos { insResult ->
                    val allInstruments = if (insResult is ApiResult.Success) insResult.data
                                         else emptyList()

                    // Cruzar slugs contratados con catalogo para obtener nombres
                    apiClient.getBodaInstrumentos(wedding.id) { contractedResult ->
                        val contractedSlugs = if (contractedResult is ApiResult.Success)
                            contractedResult.data.toSet()
                        else emptySet()

                        val weddingInstruments = allInstruments.filter { it.id in contractedSlugs }

                        // Armar items combinando setlist + momentos + canciones
                        val moments = momResult.data
                        val songs = songResult.data
                        val cachedItems = setlistResult.data.mapNotNull { sli ->
                            val m = moments.firstOrNull { it.id == sli.momentId }
                            val s = songs.firstOrNull { it.id == sli.songId }
                            if (m != null && s != null) {
                                CachedSetlistItem(
                                    idSetlist = sli.id,
                                    idMomento = sli.momentId,
                                    nombreMomento = m.name,
                                    ordenMomento = m.displayOrder,
                                    idCancion = sli.songId,
                                    tituloCancion = s.title,
                                    autorCancion = s.author,
                                    idiomaCancion = s.language,
                                    ordenSetlist = sli.displayOrder
                                )
                            } else null
                        }
                        database.cacheSetlist(wedding.id, cachedItems)
                        onSuccess(cachedItems, weddingInstruments)
                    }
                }
            }
        }
    }
}

/**
 * Guarda el PDF del setlist en cache y dispara Intent.ACTION_SEND
 * para que el usuario elija app destino (WhatsApp, Gmail, Drive, etc.).
 * Mismo patron que compartirContratoPdf en ContractScreen, distinta
 * subcarpeta y subject para mantener limpio el directorio de cache.
 */
private fun compartirSetlistPdf(
    context: android.content.Context,
    filename: String,
    bytes: ByteArray
) {
    try {
        val cacheDir = java.io.File(context.cacheDir, "setlists").apply { mkdirs() }
        val file = java.io.File(cacheDir, filename)
        file.outputStream().use { it.write(bytes) }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Setlist Coro Pacem Deus")
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "Adjunto el setlist musical de mi boda con el Coro Pacem Deus."
            )
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            android.content.Intent.createChooser(sendIntent, "Compartir setlist")
        )
    } catch (e: Exception) {
        android.util.Log.e("SetlistScreen", "Error al compartir setlist PDF", e)
    }
}
