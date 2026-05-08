package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.DemoData
import com.pacemdeus.bodas.data.LiturgicalMoment
import com.pacemdeus.bodas.data.SetlistItem
import com.pacemdeus.bodas.data.Song
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.Divider
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.Gold
import com.pacemdeus.bodas.ui.components.GoldSoft
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand

// Pantalla de armado del ensamble musical. Muestra los 14 momentos
// de la ceremonia ordenados; al tocar uno abre un picker filtrado con
// las canciones permitidas para ese momento. Respeta el limite max_songs
// por momento (entrada=2, fotografias=4, comunion=2, ofertorio=2, resto=1).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssemblyScreen(
    wedding: Wedding?,
    setlist: List<SetlistItem>,
    onBack: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenSetlist: () -> Unit = {},
    onOpenInstruments: () -> Unit = {},
    onAddSong: (weddingId: String, momentId: String, songId: String) -> Unit = { _, _, _ -> },
    onRemoveSong: (itemId: String) -> Unit = {}
) {
    // Si no hay boda activa renderizamos un estado vacio y salimos.
    // Resolver esto antes evita que `wedding` siga siendo nullable en el
    // resto de la funcion (incluido el AlertDialog del picker).
    if (wedding == null) {
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

    val moments = DemoData.moments
    val songs = DemoData.songs
    val isEditable = wedding.status == WeddingStatus.DRAFT ||
            wedding.status == WeddingStatus.SUBMITTED

    /** Momento abierto en el dialog de seleccion (null = cerrado). */
    var pickerMoment by remember { mutableStateOf<LiturgicalMoment?>(null) }

    Scaffold(
        topBar = {
            PacemTopBar(
                title = "Ensamble musical",
                onBack = onBack
            )
        },
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

        val filledMoments = setlist.map { it.momentId }.distinct().count()

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ─── Barra de progreso global ──────────────────
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
                    "$filledMoments / ${moments.size}",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { filledMoments.toFloat() / moments.size },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Gold,
                trackColor = Divider
            )
            Spacer(Modifier.height(16.dp))

            OutlineGoldButton(
                text = "Elegir instrumentos",
                onClick = onOpenInstruments
            )

            Spacer(Modifier.height(16.dp))

            // ─── Lista de momentos (Column con scroll) ─────
            for ((index, m) in moments.withIndex()) {
                val itemsForMoment = setlist.filter { it.momentId == m.id }
                MomentTimelineItem(
                    index = index + 1,
                    moment = m,
                    itemsForMoment = itemsForMoment,
                    songsCatalog = songs,
                    editable = isEditable,
                    onClickAdd = { pickerMoment = m },
                    onRemove = onRemoveSong
                )
                if (index < moments.size - 1) Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // ─── Dialog de seleccion de cancion ────────────────────

    pickerMoment?.let { moment ->
        val available = songs.filter { it.allowedMomentSlugs.contains(moment.slug) }
        val alreadyPicked = setlist.filter { it.momentId == moment.id }.map { it.songId }.toSet()
        val current = alreadyPicked.size
        val canStillAdd = current < moment.maxSongs

        AlertDialog(
            onDismissRequest = { pickerMoment = null },
            title = {
                Column {
                    Text(moment.name, color = Brown, fontWeight = FontWeight.Bold)
                    Text(
                        if (canStillAdd)
                            "Selecciona un canto ($current de ${moment.maxSongs})"
                        else
                            "Maximo alcanzado: ${moment.maxSongs} canto(s)",
                        color = Sand,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                if (available.isEmpty()) {
                    Text("No hay canciones disponibles para este momento.")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        for (song in available) {
                            val already = song.id in alreadyPicked
                            SongPickerRow(
                                song = song,
                                already = already,
                                enabled = canStillAdd && !already,
                                onClick = {
                                    if (canStillAdd && !already) {
                                        onAddSong(wedding.id, moment.id, song.id)
                                        pickerMoment = null
                                    }
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerMoment = null }) {
                    Text("Cerrar", color = Gold)
                }
            }
        )
    }
}

@Composable
private fun MomentTimelineItem(
    index: Int,
    moment: LiturgicalMoment,
    itemsForMoment: List<SetlistItem>,
    songsCatalog: List<Song>,
    editable: Boolean,
    onClickAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    val hasSongs = itemsForMoment.isNotEmpty()
    val full = itemsForMoment.size >= moment.maxSongs

    PacemCard(
        onClick = if (editable && !full) onClickAdd else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Indice numerico
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (hasSongs) Gold else GoldSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (hasSongs) {
                    Icon(
                        Icons.Default.Check, contentDescription = null,
                        tint = Cream, modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        index.toString(),
                        color = Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(moment.name, color = Brown, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    moment.description,
                    color = Sand,
                    fontSize = 11.sp
                )
            }

            if (editable && !full) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Agregar canto",
                    tint = Gold,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (hasSongs) {
            Spacer(Modifier.height(8.dp))
            for (item in itemsForMoment) {
                val song = songsCatalog.firstOrNull { it.id == item.songId }
                if (song != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 46.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = Brown,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                song.author,
                                color = Sand,
                                fontSize = 11.sp
                            )
                        }
                        if (editable) {
                            TextButton(onClick = { onRemove(item.id) }) {
                                Text(
                                    "Quitar",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
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
    onClick: () -> Unit
) {
    val borderColor = when {
        already -> Gold
        enabled -> Color(0xFFE0D9C8)
        else    -> Color(0xFFEEEEEE)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (already) GoldSoft else Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = Brown,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${song.author}  -  ${languageName(song.language)}",
                    color = Sand,
                    fontSize = 11.sp
                )
            }
            if (already) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun languageName(code: String): String = when (code) {
    "ES"   -> "Español"
    "LA"   -> "Latin"
    "EN"   -> "Ingles"
    "INST" -> "Instrumental"
    else   -> code
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AssemblyScreenPreview() {
    MaterialTheme {
        AssemblyScreen(
            wedding = DemoData.initialWeddings[0],
            setlist = DemoData.initialSetlist
        )
    }
}
