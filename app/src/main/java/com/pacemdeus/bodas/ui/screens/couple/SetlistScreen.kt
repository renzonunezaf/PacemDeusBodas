package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.DemoData
import com.pacemdeus.bodas.data.SetlistItem
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.Gold
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel

// Vista del setlist completo agrupado por momento, con la opcion de
// remover canciones (solo si la boda esta en DRAFT o SUBMITTED).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistScreen(
    wedding: Wedding?,
    items: List<SetlistItem>,
    onBack: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenAssembly: () -> Unit = {},
    onRemove: (String) -> Unit = {}
) {
    val moments = DemoData.moments
    val songs = DemoData.songs
    val isEditable = wedding?.status == WeddingStatus.DRAFT ||
            wedding?.status == WeddingStatus.SUBMITTED

    var pendingRemove by remember { mutableStateOf<SetlistItem?>(null) }

    Scaffold(
        topBar = { PacemTopBar(title = "Mi setlist", onBack = onBack) },
        bottomBar = {
            CoupleBottomNav(
                current = CoupleTab.Setlist,
                onSelectHome = onOpenHome,
                onSelectAssembly = onOpenAssembly,
                onSelectSetlist = {}
            )
        },
        containerColor = Cream
    ) { padding ->

        if (wedding == null) {
            EmptyState("Crea primero tu evento")
            return@Scaffold
        }

        val itemsByMoment = items.groupBy { it.momentId }
        val filledCount = itemsByMoment.size
        val missing = moments.size - filledCount

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ─── Resumen ──────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${items.size} canciones en $filledCount momentos",
                    color = Brown,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (missing > 0) "Faltan $missing momentos" else "Completo",
                    color = if (missing > 0) Sand else Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(20.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Aun no has elegido canciones.\nVe a Ensamble para agregarlas.",
                        color = Sand,
                        fontSize = 13.sp
                    )
                }
            } else {
                // ─── Lista agrupada por momento ────────────
                for (moment in moments) {
                    val list = itemsByMoment[moment.id] ?: continue
                    SectionLabel(moment.name)
                    PacemCard {
                        for ((idx, item) in list.withIndex()) {
                            val song = songs.firstOrNull { it.id == item.songId } ?: continue
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Gold
                                )
                                Spacer(Modifier.padding(horizontal = 6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        color = Brown,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(song.author, color = Sand, fontSize = 11.sp)
                                }
                                if (isEditable) {
                                    TextButton(onClick = { pendingRemove = item }) {
                                        Text(
                                            "Quitar",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            if (idx < list.size - 1) Spacer(Modifier.padding(vertical = 4.dp))
                        }
                    }
                    Spacer(Modifier.padding(vertical = 6.dp))
                }
            }

            Spacer(Modifier.padding(vertical = 20.dp))
        }
    }

    pendingRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Quitar canto") },
            text = { Text("¿Quitar este canto del setlist?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(item.id)
                    pendingRemove = null
                }) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("Cancelar", color = Sand)
                }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SetlistScreenPreview() {
    MaterialTheme {
        SetlistScreen(
            wedding = DemoData.initialWeddings[1],
            items = DemoData.initialSetlist
        )
    }
}
