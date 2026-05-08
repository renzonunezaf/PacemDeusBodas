package com.pacemdeus.bodas.ui.screens.couple

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.Divider
import com.pacemdeus.bodas.ui.components.Gold
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.GoldSoft
import com.pacemdeus.bodas.ui.components.NavBg
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge

// Pantalla principal del novio/a. Muestra la tarjeta del evento con su
// estado, el progreso del ensamble (cantos asignados / 14 momentos) y
// los botones de accion disponibles segun el estado de la boda.

private const val TOTAL_MOMENTS = 14

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleHomeScreen(
    session: UserSession,
    wedding: Wedding?,
    setlistCount: Int,
    onCreateWedding: () -> Unit = {},
    onEditWedding: (String) -> Unit = {},
    onOpenAssembly: () -> Unit = {},
    onOpenSetlist: () -> Unit = {},
    onOpenInstruments: () -> Unit = {},
    onOpenContract: (String) -> Unit = {},
    onSubmit: (String) -> Unit = {},
    onRequestCancel: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    // Si la pareja aun no creo su evento, mostramos el estado vacio con
    // CTA para crearlo y salimos. Esto deja a `wedding` como Wedding (no
    // nullable) en el resto de la funcion: smart cast aplica naturalmente
    // tanto en el cuerpo del Scaffold como en los AlertDialog finales.
    if (wedding == null) {
        Scaffold(
            topBar = { PacemTopBar(title = "Pacem Deus Bodas", onLogout = onLogout) },
            containerColor = Cream
        ) { padding ->
            EmptyWeddingState(
                modifier = Modifier.padding(padding),
                onCreate = onCreateWedding
            )
        }
        return
    }

    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    val status = wedding.status
    val isEditable = status == WeddingStatus.DRAFT || status == WeddingStatus.SUBMITTED
    val canSubmit = status == WeddingStatus.DRAFT && setlistCount > 0
    val canViewContract = status == WeddingStatus.APPROVED ||
            status == WeddingStatus.CONTRACTED ||
            status == WeddingStatus.COMPLETED
    val canCancel = status == WeddingStatus.DRAFT ||
            status == WeddingStatus.SUBMITTED ||
            status == WeddingStatus.APPROVED

    Scaffold(
        topBar = { PacemTopBar(title = "Pacem Deus Bodas", onLogout = onLogout) },
        bottomBar = {
            CoupleBottomNav(
                current = CoupleTab.Home,
                onSelectHome = {},
                onSelectAssembly = onOpenAssembly,
                onSelectSetlist = onOpenSetlist
            )
        },
        containerColor = Cream
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ─── Saludo con nombre de la pareja ────────────
            SectionLabel("Tu ceremonia")
            Text(
                session.displayName(),
                color = Brown,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            // ─── Card: detalle del evento ──────────────────
            PacemCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Detalle del evento")
                    Spacer(Modifier.weight(1f))
                    StatusBadge(wedding.status)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${wedding.weddingDate}  -  ${wedding.weddingTime}",
                    color = Brown,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    wedding.venueName,
                    color = Brown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    wedding.venueAddress,
                    color = Sand,
                    fontSize = 12.sp
                )
                if (isEditable) {
                    Spacer(Modifier.height(12.dp))
                    OutlineGoldButton(
                        text = "Editar datos del evento",
                        onClick = { onEditWedding(wedding.id) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Card: progreso del ensamble ───────────────
            PacemCard {
                SectionLabel("Ensamble musical")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (setlistCount == 0)
                            "Aun no has seleccionado canciones"
                        else
                            "$setlistCount canciones asignadas",
                        color = Brown,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$setlistCount / $TOTAL_MOMENTS",
                        color = Gold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (setlistCount.coerceAtMost(TOTAL_MOMENTS).toFloat()) / TOTAL_MOMENTS },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Gold,
                    trackColor = Divider
                )
            }

            Spacer(Modifier.height(12.dp))

            // ─── Card: precio total ────────────────────────
            PacemCard {
                SectionLabel("Inversion total")
                Text(
                    "S/. %.2f".format(wedding.totalPrice),
                    color = Brown,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Base S/. %.0f  +  Instrumentos S/. %.0f"
                        .format(wedding.basePrice, wedding.instrumentsPrice),
                    color = Sand,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Acciones segun estado ─────────────────────
            if (isEditable) {
                GoldButton(
                    text = "Editar ensamble musical",
                    onClick = onOpenAssembly
                )
                Spacer(Modifier.height(8.dp))
                OutlineGoldButton(
                    text = "Elegir instrumentos",
                    onClick = onOpenInstruments
                )
                Spacer(Modifier.height(8.dp))
                OutlineGoldButton(
                    text = "Ver mi setlist",
                    onClick = onOpenSetlist
                )
                if (canSubmit) {
                    Spacer(Modifier.height(8.dp))
                    GoldButton(
                        text = "Enviar al coro para aprobacion",
                        onClick = { showSubmitConfirm = true }
                    )
                }
            }

            if (canViewContract) {
                Spacer(Modifier.height(8.dp))
                GoldButton(
                    text = "Ver contrato",
                    onClick = { onOpenContract(wedding.id) }
                )
            }

            if (canCancel) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showCancelConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Solicitar cancelacion", color = Sand, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ─── Dialogs de confirmacion ───────────────────────────

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Enviar al coro") },
            text = {
                Text("Una vez enviado, tu evento pasara a revision. ¿Continuar?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSubmitConfirm = false
                    onSubmit(wedding.id)
                }) { Text("Enviar", color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) {
                    Text("Cancelar", color = Sand)
                }
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Solicitar cancelacion") },
            text = {
                Text("Se enviara una solicitud al coordinador para cancelar el evento.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onRequestCancel(wedding.id)
                }) { Text("Solicitar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Volver", color = Sand)
                }
            }
        )
    }
}

@Composable
private fun EmptyWeddingState(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(96.dp).background(GoldSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.EventNote,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Aun no has creado tu evento",
                color = Brown, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Empieza definiendo la fecha y el lugar",
                color = Sand, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            GoldButton(text = "Crear mi evento", onClick = onCreate)
        }
    }
}

// ─── Bottom navigation del rol Couple ──────────────────────

enum class CoupleTab { Home, Assembly, Setlist }

@Composable
fun CoupleBottomNav(
    current: CoupleTab,
    onSelectHome: () -> Unit,
    onSelectAssembly: () -> Unit,
    onSelectSetlist: () -> Unit
) {
    NavigationBar(containerColor = NavBg) {
        NavigationBarItem(
            selected = current == CoupleTab.Home,
            onClick = { if (current != CoupleTab.Home) onSelectHome() },
            icon = { Icon(Icons.Default.EventNote, contentDescription = "Inicio") },
            label = { Text("Inicio") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
        NavigationBarItem(
            selected = current == CoupleTab.Assembly,
            onClick = { if (current != CoupleTab.Assembly) onSelectAssembly() },
            icon = { Icon(Icons.Default.MusicNote, contentDescription = "Ensamble") },
            label = { Text("Ensamble") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
        NavigationBarItem(
            selected = current == CoupleTab.Setlist,
            onClick = { if (current != CoupleTab.Setlist) onSelectSetlist() },
            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Setlist") },
            label = { Text("Setlist") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Gold,
                selectedTextColor = Gold,
                indicatorColor = GoldSoft
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CoupleHomeScreenPreview() {
    MaterialTheme {
        CoupleHomeScreen(
            session = com.pacemdeus.bodas.data.UserSession(
                user = com.pacemdeus.bodas.data.DemoData.users[5],
                coupleProfile = com.pacemdeus.bodas.data.DemoData.couples[0],
                plannerProfile = null,
                weddingId = "wed-1"
            ),
            wedding = com.pacemdeus.bodas.data.DemoData.initialWeddings[0],
            setlistCount = 3
        )
    }
}
