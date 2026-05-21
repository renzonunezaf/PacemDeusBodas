package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.data.local.OfflineWeddingCache
import com.pacemdeus.bodas.data.local.SetlistDatabase
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Divider
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.NavBg
import com.pacemdeus.bodas.ui.theme.Sand
import com.pacemdeus.bodas.ui.theme.Danger
import com.pacemdeus.bodas.ui.theme.Success

// Pantalla principal del novio/a. Muestra la tarjeta del evento con su
// estado, el progreso del ensamble (cantos asignados / 14 momentos) y
// los botones de accion disponibles segun el estado de la boda.
//
// Esta pantalla es responsable de cargar su propia data:
//   - La boda activa de la pareja (apiClient.listBodas)
//   - El conteo de canciones del setlist (apiClient.listSetlist)
// y de disparar las mutaciones (enviar a aprobacion, cancelar).

private const val TOTAL_MOMENTS = 14

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleHomeScreen(
    session: UserSession,
    onCreateWedding: () -> Unit = {},
    onEditWedding: (String) -> Unit = {},
    onOpenAssembly: () -> Unit = {},
    onOpenSetlist: () -> Unit = {},
    onOpenInstruments: () -> Unit = {},
    onOpenContract: (String) -> Unit = {},
    onOpenGallery: (String) -> Unit = {},
    onPickPlanner: (String, String?) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }
    val offlineCache = remember { OfflineWeddingCache.get(context) }
    val database = remember { SetlistDatabase.get(context) }

    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var setlistCount by remember { mutableStateOf(0) }
    // Cotizacion calculada en vivo (mismo endpoint que usa la pantalla
    // Ensamble). Es la fuente de verdad del precio mostrado, en lugar
    // del campo precio_total guardado en BD que puede quedar desfasado
    // si cambiaron las tarifas internas o la lista de instrumentos.
    var liveQuote by remember { mutableStateOf<com.pacemdeus.bodas.data.PriceQuote?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Modo offline activo cuando la peticion principal a /bodas fallo
    // y caemos al cache local (HU-06). Bloquea las acciones que requieren
    // backend (enviar, cancelar, editar, firmar, etc.) y muestra el
    // banner discreto al tope de la pantalla.
    var isOffline by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showUnsubmitConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Ultimo estado conocido de la boda. Se usa para detectar cambios
    // durante el polling y disparar una notificacion local del sistema
    // cuando el coro aprueba, devuelve o firma. Null antes de la primera
    // carga: la primera vez NO notificamos (seria spam de estados
    // viejos al abrir la app).
    var lastKnownStatus by remember { mutableStateOf<String?>(null) }

    // Polling foreground cada 5 segundos. Mientras la pareja tenga esta
    // pantalla visible, refrescamos el estado de la boda y comparamos
    // contra el ultimo conocido. Si hay cambio relevante (aprobacion,
    // devolucion, contrato firmado), notificamos al sistema.
    //
    // No usamos WorkManager ni FCM aqui: con la app abierta esto da
    // experiencia "tiempo real" (latencia maxima 5s). Cuando la app
    // se va a background, el LaunchedEffect se cancela automatico y
    // el polling se detiene (Android maneja el ciclo de vida).
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            apiClient.listBodas { pollResult ->
                if (pollResult is ApiResult.Success) {
                    // La red volvio: salimos del modo offline (si
                    // estabamos en el) y el siguiente refreshTick++
                    // re-cargara todo lo demas (setlist, cotizacion).
                    if (isOffline) {
                        isOffline = false
                        refreshTick++
                    }
                    val polledWedding = pollResult.data.firstOrNull()
                    val newStatus = polledWedding?.status?.name
                    if (newStatus != null) {
                        com.pacemdeus.bodas.services.NotificationHelper.notifyStatusChange(
                            context = context,
                            oldStatus = lastKnownStatus,
                            newStatus = newStatus
                        )
                        lastKnownStatus = newStatus
                        // Si el estado cambio, refrescamos la UI tambien
                        // para que la pareja vea el nuevo badge / banner
                        // sin tener que cerrar y abrir la app.
                        if (wedding?.status?.name != newStatus) {
                            refreshTick++
                        }
                    }
                }
                // Si el polling falla (sin red), no hacemos nada:
                // dejamos los datos del cache visibles y el banner
                // offline activo. La proxima ronda intentara otra vez.
            }
        }
    }

    // Cargar la boda activa del couple y el conteo del setlist cada vez
    // que la pantalla se monta o se solicita un refresh (despues de un
    // cambio de estado de la boda, por ejemplo).
    LaunchedEffect(refreshTick) {
        isLoading = true
        errorMessage = null

        apiClient.listBodas { result ->
            when (result) {
                is ApiResult.Success -> {
                    // El couple tiene como mucho una boda ACTIVA. Si tiene
                    // bodas en estados terminales (CANCELLED, COMPLETED)
                    // las ignoramos para que pueda crear una nueva.
                    val w = result.data.firstOrNull {
                        it.status != WeddingStatus.CANCELLED &&
                        it.status != WeddingStatus.COMPLETED
                    }
                    wedding = w
                    isOffline = false
                    // Guardar el estado conocido para que el polling
                    // no notifique transiciones falsas (no hubo cambio,
                    // simplemente es la primera vez que lo vemos).
                    lastKnownStatus = w?.status?.name
                    if (w != null) {
                        // Conteo del setlist en una segunda llamada
                        apiClient.listSetlist(w.id) { setlistResult ->
                            when (setlistResult) {
                                is ApiResult.Success -> setlistCount = setlistResult.data.size
                                is ApiResult.Error -> setlistCount = 0
                                else -> {}
                            }
                            // Cotizar en vivo con los datos actuales de
                            // la boda. Necesitamos lat/lng + fecha/hora
                            // + lista de instrumentos contratados.
                            // Si la boda no tiene esos datos completos
                            // (raro), saltamos la cotizacion y queda
                            // null (la UI muestra el precio guardado).
                            val lat = w.venueLat
                            val lng = w.venueLng
                            if (lat != null && lng != null &&
                                w.weddingDate.isNotBlank() && w.weddingTime.isNotBlank()
                            ) {
                                apiClient.getBodaInstrumentos(w.id) { instrResult ->
                                    val slugs = if (instrResult is ApiResult.Success) {
                                        instrResult.data
                                    } else emptyList()
                                    apiClient.cotizar(
                                        venueLat = lat,
                                        venueLng = lng,
                                        weddingDate = w.weddingDate,
                                        weddingTime = w.weddingTime,
                                        instrumentSlugs = slugs
                                    ) { qResult ->
                                        if (qResult is ApiResult.Success) {
                                            liveQuote = qResult.data
                                        }
                                        isLoading = false
                                    }
                                }
                            } else {
                                isLoading = false
                            }
                        }
                    } else {
                        isLoading = false
                    }
                }
                is ApiResult.Error -> {
                    // Sin red (o backend caido): caemos al cache offline
                    // para que la novia siga viendo su evento y pueda
                    // navegar a su setlist guardado. Las acciones de
                    // estado quedan deshabilitadas hasta que vuelva la
                    // conexion.
                    val cachedWedding = offlineCache.loadActiveWedding()
                    if (cachedWedding != null) {
                        wedding = cachedWedding
                        isOffline = true
                        // Contamos del cache local del setlist para que la
                        // tarjeta de ensamble musical no muestre cero falso.
                        setlistCount = database.countSetlist(cachedWedding.id)
                        lastKnownStatus = cachedWedding.status.name
                        errorMessage = null
                        liveQuote = null  // no hay cotizacion sin red
                        isLoading = false
                    } else {
                        // No hay cache: probablemente la novia abrio la app
                        // por primera vez sin red. Mostramos el empty
                        // state habitual con el mensaje del error.
                        isLoading = false
                        isOffline = true
                        errorMessage = result.message
                    }
                }
                else -> {}
            }
        }
    }

    // ─── Estado: cargando ──────────────────────────────────
    if (isLoading) {
        com.pacemdeus.bodas.ui.components.PacemDrawerScaffold(
            title = "Coro Pacem Deus",
            drawerItems = coupleDrawerItems(
                onOpenHome = {},
                onOpenAssembly = onOpenAssembly,
                onOpenSetlist = onOpenSetlist,
                selected = "home"
            ),
            onLogout = onLogout
        ) { padding ->
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Gold)
            }
        }
        return
    }

    // ─── Estado: sin evento creado ─────────────────────────
    val currentWedding = wedding
    if (currentWedding == null) {
        com.pacemdeus.bodas.ui.components.PacemDrawerScaffold(
            title = "Coro Pacem Deus",
            drawerItems = coupleDrawerItems(
                onOpenHome = {},
                onOpenAssembly = onOpenAssembly,
                onOpenSetlist = onOpenSetlist,
                selected = "home"
            ),
            onLogout = onLogout
        ) { padding ->
            EmptyWeddingState(
                modifier = Modifier.padding(padding),
                onCreate = onCreateWedding,
                errorMessage = errorMessage,
                isOffline = isOffline
            )
        }
        return
    }

    // ─── Estado: evento existente, vista completa ──────────
    val status = currentWedding.status
    // Cuando estamos en modo offline (fallback al cache local) bloqueamos
    // TODAS las acciones que requieren red: editar, enviar al coro,
    // volver a borrador, solicitar cancelacion, cambiar planner.
    // La navegacion al setlist sigue funcionando porque lee del cache local.
    val isEditable = currentWedding.isEditable && !isOffline   // solo DRAFT y online
    val canSubmit = currentWedding.isEditable && setlistCount > 0 && !isOffline
    val canUnsubmit = currentWedding.canUnsubmit && !isOffline
    val canRequestCancellation = currentWedding.canRequestCancellation && !isOffline
    val canViewContract = (status == WeddingStatus.APPROVED ||
            status == WeddingStatus.CONTRACTED ||
            status == WeddingStatus.COMPLETED) && !isOffline

    com.pacemdeus.bodas.ui.components.PacemDrawerScaffold(
        title = "Coro Pacem Deus",
        drawerItems = coupleDrawerItems(
            onOpenHome = {},
            onOpenAssembly = onOpenAssembly,
            onOpenSetlist = onOpenSetlist,
            selected = "home"
        ),
        onLogout = onLogout
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Banner offline: avisa a la novia que esta viendo datos
            // guardados y que las acciones de cambio de estado estan
            // temporalmente deshabilitadas hasta que recupere conexion.
            if (isOffline) {
                OfflineBanner()
                Spacer(Modifier.height(12.dp))
            }

            SectionLabel("Tu ceremonia", icon = Icons.Default.Favorite)
            Text(
                session.displayName(),
                color = Brown,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            // Tarjetas destacadas segun estado especial
            when {
                currentWedding.isObservado -> {
                    ObservadoCard(notes = currentWedding.notes ?: "")
                    Spacer(Modifier.height(12.dp))
                }
                status == WeddingStatus.SUBMITTED -> {
                    SubmittedCard()
                    Spacer(Modifier.height(12.dp))
                }
                status == WeddingStatus.RETURNED_WITH_NOTES -> {
                    ReturnedWithNotesBanner(
                        weddingId = currentWedding.id,
                        onResponded = { refreshTick++ }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                status == WeddingStatus.CANCELLATION_REQUESTED -> {
                    CancellationRequestedCard()
                    Spacer(Modifier.height(12.dp))
                }
                else -> { /* sin tarjeta destacada */ }
            }

            // ─── Detalle del evento ─────────────────────────
            // Botón "Editar" inline arriba a la derecha del título cuando
            // la boda está en estado editable. Elimina el botón de abajo.
            PacemCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Detalle del evento", icon = Icons.Default.CalendarMonth)
                    Spacer(Modifier.weight(1f))
                    StatusBadgeWide(currentWedding)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${currentWedding.weddingDate}  -  ${currentWedding.weddingTime}",
                    color = Brown,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    currentWedding.venueName,
                    color = Brown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    currentWedding.venueAddress,
                    color = Sand,
                    fontSize = 12.sp
                )
                if (isEditable) {
                    Spacer(Modifier.height(10.dp))
                    InlineEditButton(onClick = { onEditWedding(currentWedding.id) })
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Wedding planner asignado ────────────────
            PacemCard {
                SectionLabel("Wedding planner", icon = Icons.Default.Person)
                Spacer(Modifier.height(4.dp))
                val plannerName = currentWedding.plannerName
                if (!plannerName.isNullOrBlank()) {
                    Text(
                        plannerName,
                        color = Brown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Te ayudara a coordinar el dia de la ceremonia con el coro.",
                        color = Sand,
                        fontSize = 11.sp
                    )
                    if (isEditable) {
                        Spacer(Modifier.height(10.dp))
                        InlineEditButton(
                            label = "Cambiar planner",
                            onClick = { onPickPlanner(currentWedding.id, currentWedding.plannerId) }
                        )
                    }
                } else {
                    Text(
                        "Aun no has asignado un wedding planner a tu evento.",
                        color = Sand,
                        fontSize = 12.sp
                    )
                    if (isEditable) {
                        Spacer(Modifier.height(8.dp))
                        OutlineGoldButton(
                            text = "Elegir wedding planner",
                            onClick = { onPickPlanner(currentWedding.id, null) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Voces e instrumentos ─────────────────────────
            // Card entera clickeable, lleva a la pantalla de instrumentos.
            // El conteo se infiere de liveQuote.instrumentsDetail filtrando
            // por slug que empieza con "voz_" (las voces) vs el resto.
            PacemCard(onClick = if (isEditable) onOpenInstruments else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Voces e instrumentos", icon = Icons.Default.MusicNote)
                    Spacer(Modifier.weight(1f))
                    if (isEditable) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                val voicesCount = liveQuote?.instrumentsDetail
                    ?.count { it.slug.startsWith("voz_") } ?: 0
                val instrumentsCount = liveQuote?.instrumentsDetail
                    ?.count { !it.slug.startsWith("voz_") } ?: 0
                val totalSelected = voicesCount + instrumentsCount
                Text(
                    if (totalSelected == 0) {
                        "Aun no has seleccionado voces ni instrumentos"
                    } else {
                        val vozStr = if (voicesCount == 1) "voz" else "voces"
                        val instStr = if (instrumentsCount == 1) "instrumento" else "instrumentos"
                        "$voicesCount $vozStr, $instrumentsCount $instStr"
                    },
                    color = Brown,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // ─── Ensamble musical (canciones) ─────────────────
            // Card clickeable cuando es editable, lleva a Assembly.
            PacemCard(onClick = if (isEditable) onOpenAssembly else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Ensamble musical", icon = Icons.Default.LibraryMusic)
                    Spacer(Modifier.weight(1f))
                    if (isEditable) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    if (setlistCount == 0)
                        "Aun no has seleccionado canciones"
                    else
                        "$setlistCount canciones asignadas",
                    color = Brown,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // ─── Fotos del local ──────────────────────────────
            // Card clickeable que lleva a la galería. Disponible en
            // cualquier estado de la boda.
            PacemCard(onClick = { onOpenGallery(currentWedding.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Fotos del local", icon = Icons.Default.PhotoCamera)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Hasta 5 fotos para que el coro planifique",
                    color = Sand,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // ─── Contrato (solo APPROVED/CONTRACTED) ─────────
            if (canViewContract) {
                PacemCard(onClick = { onOpenContract(currentWedding.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Contrato", icon = Icons.Default.Description)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Detalle del acuerdo con el coro",
                        color = Sand,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            // ─── Inversion total ──────────────────────────────
            PacemCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Inversion total", icon = Icons.Default.AttachMoney)
                    Spacer(Modifier.weight(1f))
                    // Badge XL +20% como en el sticky bar, para que la
                    // novia entienda por que sube el precio con grupo grande.
                    if (liveQuote?.isXl == true) {
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
                // Preferimos el precio de la cotizacion en vivo (calcula
                // base + instrumentos + movilidad on the fly). Si por
                // alguna razon no se pudo cotizar (sin red, falla del
                // endpoint), caemos al precio guardado en BD para no
                // dejar la UI vacia.
                val totalAMostrar = liveQuote?.totalPrice ?: currentWedding.totalPrice
                Text(
                    "S/. %.2f".format(totalAMostrar),
                    color = Brown,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                // Si hay cotizacion en vivo, mostramos la grilla con el
                // desglose por categoria (mismo componente que el sticky
                // bar de InstrumentsScreen). Si no, caemos a una linea
                // sencilla con los precios guardados en BD.
                if (liveQuote != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Divider)
                    )
                    Spacer(Modifier.height(10.dp))
                    com.pacemdeus.bodas.ui.components.PriceBreakdownGrid(quote = liveQuote!!)
                } else {
                    Text(
                        "Base S/. %.0f  +  Instrumentos S/. %.0f"
                            .format(currentWedding.basePrice, currentWedding.instrumentsPrice),
                        color = Sand,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Acciones de cambio de estado ──────────────────
            // Las acciones de edición (evento, ensamble, voces e instrumentos,
            // fotos, contrato) ya están inline en las tarjetas de arriba.
            // Aquí abajo solo van los botones que cambian el estado de la
            // boda: enviar al coro, volver a borrador, solicitar cancelación.
            if (isEditable && canSubmit) {
                GoldButton(
                    text = "Enviar al coro para aprobación",
                    onClick = { showSubmitConfirm = true }
                )
            }

            // SUBMITTED: solo permite volver a borrador (mientras el coro
            // no haya revisado todavía)
            if (status == WeddingStatus.SUBMITTED && canUnsubmit) {
                GoldButton(
                    text = "Volver a borrador",
                    onClick = { showUnsubmitConfirm = true }
                )
            }

            // Solicitar cancelacion (APPROVED o CONTRACTED): accion
            // destructiva, va como texto discreto al final
            if (canRequestCancellation) {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { showCancelConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Solicitar cancelacion del evento",
                        color = Sand,
                        fontSize = 13.sp
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ─── Dialogs de confirmacion ───────────────────────────

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Enviar al coro") },
            text = { Text("Una vez enviado, tu evento pasara a revision. Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showSubmitConfirm = false
                    // Disparar el cambio de estado en el backend
                    apiClient.enviarBoda(currentWedding.id) { result ->
                        when (result) {
                            is ApiResult.Success -> refreshTick++
                            is ApiResult.Error -> errorMessage = result.message
                            else -> {}
                        }
                    }
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
            text = { Text("Se enviara una solicitud al coordinador para cancelar el evento.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    apiClient.cancelarBoda(currentWedding.id, motivo = "Solicitud del couple") { result ->
                        when (result) {
                            is ApiResult.Success -> refreshTick++
                            is ApiResult.Error -> errorMessage = result.message
                            else -> {}
                        }
                    }
                }) { Text("Solicitar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Volver", color = Sand)
                }
            }
        )
    }

    if (showUnsubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsubmitConfirm = false },
            title = { Text("Volver a borrador", color = Brown, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Tu evento volvera a Borrador y podras editarlo de nuevo. " +
                        "Despues tendras que volver a enviarlo al coro para que lo revise. " +
                        "Esta accion solo funciona si el coro aun no ha revisado tu evento."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnsubmitConfirm = false
                    apiClient.desenviarBoda(currentWedding.id) { result ->
                        when (result) {
                            is ApiResult.Success -> refreshTick++
                            is ApiResult.Error -> errorMessage = result.message
                            else -> {}
                        }
                    }
                }) { Text("Volver a borrador", color = Gold, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showUnsubmitConfirm = false }) {
                    Text("Cancelar", color = Sand)
                }
            },
            containerColor = Cream
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Tarjetas destacadas por estado
// ═══════════════════════════════════════════════════════════════════

/**
 * Tarjeta amber con las observaciones del coro cuando el admin rechazo
 * el evento. Aparece arriba de todo para que la novia las vea de inmediato.
 */
@Composable
private fun ObservadoCard(notes: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFDF1D6), RoundedCornerShape(14.dp))
            .padding(2.dp)
            .background(Color(0xFFE6B85C).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color(0xFF8C6A1A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "OBSERVADO POR EL CORO",
                    color = Color(0xFF8C6A1A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "El coro reviso tu evento y dejo estas observaciones para que las atiendas " +
                    "antes de volver a enviarlo:",
                color = Brown,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    notes,
                    color = Brown,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Tarjeta informativa cuando el evento esta enviado al coro. Usa la
 * paleta dorada del proyecto para no romper el look and feel: fondo
 * cream con borde gold y acento brown.
 */
@Composable
private fun SubmittedCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GoldSoft, RoundedCornerShape(14.dp))
            .padding(2.dp)
            .background(Cream, RoundedCornerShape(13.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "ENVIADO AL CORO",
                    color = Brown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tu evento esta en revision por el coro. No podras editarlo hasta que el " +
                    "coro lo apruebe o lo devuelva con observaciones. " +
                    "Si quieres hacer cambios ahora, usa el boton \"Volver a borrador\".",
                color = Brown,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CancellationRequestedCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8E6E6), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = null,
                    tint = Color(0xFF8B2E2E),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "CANCELACION SOLICITADA",
                    color = Color(0xFF8B2E2E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "El coro esta revisando tu solicitud de cancelacion. Te notificaremos " +
                    "cuando haya una respuesta.",
                color = Brown,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Badge de estado mas grande y descriptivo que el StatusBadge generico.
 * Distingue Observado de Borrador con colores propios. La paleta se
 * mantiene dentro del look del proyecto (gold/brown/cream + acentos
 * verde y rojo solo para estados que requieren llamar la atencion).
 */
@Composable
private fun StatusBadgeWide(wedding: Wedding) {
    val dotColor = when {
        wedding.isObservado                                     -> Color(0xFFD68A1A) // naranja: requiere atencion
        wedding.status == WeddingStatus.SUBMITTED               -> Color(0xFF1F6FB2) // azul
        wedding.status == WeddingStatus.APPROVED                -> Color(0xFF2E8B3D) // verde
        wedding.status == WeddingStatus.CONTRACTED              -> Color(0xFF6B3FA0) // violeta
        wedding.status == WeddingStatus.CANCELLATION_REQUESTED  -> Color(0xFFB23A3A) // rojo apagado
        wedding.status == WeddingStatus.COMPLETED               -> Color(0xFF777777) // gris
        wedding.status == WeddingStatus.CANCELLED               -> Color(0xFF555555) // gris oscuro
        else                                                    -> Color(0xFFC9A227) // ambar DRAFT
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            wedding.statusDisplayName(),
            color = Brown,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Action list items: cards con icono + titulo + subtitulo + chevron.
// Sustituyen la lista plana de botones para dar jerarquia visual y
// hacer las acciones mas reconocibles "de un vistazo".
// ═══════════════════════════════════════════════════════════════════

/** Agrupa items de accion bajo un titulo de seccion en gold. */
@Composable
private fun ActionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        title.uppercase(),
        color = Gold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

/**
 * Item de accion: card horizontal clickable con un icono circular
 * a la izquierda, un titulo prominente, un subtitulo descriptivo y
 * una flecha chevron a la derecha. Cuando `accent = true` resalta
 * con fondo GoldSoft (uso para la accion principal de cada grupo,
 * por ejemplo "Ver contrato" cuando ya esta aprobado).
 */
@Composable
private fun ActionListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (accent) GoldSoft else Cream
    val borderColor = if (accent) Gold else GoldSoft
    val iconBg = if (accent) Gold else GoldSoft
    val iconTint = if (accent) Cream else Gold

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        // Icono circular a la izquierda
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(iconBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(14.dp))

        // Titulo + subtitulo
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Brown,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = Sand,
                fontSize = 12.sp,
                lineHeight = 15.sp
            )
        }

        // Chevron a la derecha
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Sand,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun EmptyWeddingState(
    modifier: Modifier = Modifier,
    onCreate: () -> Unit,
    errorMessage: String? = null,
    isOffline: Boolean = false
) {
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
                    if (isOffline) Icons.Default.CloudOff else Icons.Default.EventNote,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            if (isOffline) {
                // Caso especial: sin red y sin cache. Probablemente la
                // novia abrio la app por primera vez offline (no hay
                // datos guardados todavia). El boton "Crear mi evento"
                // tampoco funcionaria sin red, asi que pedimos que
                // recupere conexion antes de continuar.
                Text(
                    "Sin conexion",
                    color = Brown, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Conectate al menos una vez para descargar tu evento.",
                    color = Sand, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
                )
            } else {
                Text(
                    "Aun no has creado tu evento",
                    color = Brown, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Empieza definiendo la fecha y el lugar",
                    color = Sand, fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))
                GoldButton(text = "Crear mi evento", onClick = onCreate)
            }

            if (errorMessage != null && !isOffline) {
                Spacer(Modifier.height(16.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Banner discreto que se muestra al tope de la home cuando la app no
 * pudo contactar al backend y esta operando con datos del cache local.
 * Estilo soft (fondo crema mas oscuro + icono CloudOff en sand), para
 * informar sin alarmar.
 */
@Composable
private fun OfflineBanner() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0E8D2), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = Sand,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Estas viendo tus datos guardados",
                color = Brown,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Sin conexion: las acciones de cambio estan en pausa.",
                color = Sand,
                fontSize = 11.sp
            )
        }
    }
}

// Items del drawer del rol Couple. Se construye desde cada pantalla
// pasando los callbacks de navegacion y cual debe quedar marcado como
// seleccionado. Mantenemos los 3 destinos del bottom nav original
// (Inicio / Ensamble / Setlist) para no perder navegacion lateral.
private fun coupleDrawerItems(
    onOpenHome: () -> Unit,
    onOpenAssembly: () -> Unit,
    onOpenSetlist: () -> Unit,
    selected: String
): List<com.pacemdeus.bodas.ui.components.PacemDrawerItem> = listOf(
    com.pacemdeus.bodas.ui.components.PacemDrawerItem(
        label = "Inicio",
        icon = Icons.Default.Favorite,
        selected = selected == "home",
        onClick = onOpenHome
    ),
    com.pacemdeus.bodas.ui.components.PacemDrawerItem(
        label = "Ensamble",
        icon = Icons.Default.MusicNote,
        selected = selected == "assembly",
        onClick = onOpenAssembly
    ),
    com.pacemdeus.bodas.ui.components.PacemDrawerItem(
        label = "Setlist",
        icon = Icons.Default.LibraryMusic,
        selected = selected == "setlist",
        onClick = onOpenSetlist
    )
)

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
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Inicio") },
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

/**
 * Banner que se muestra cuando la boda esta en RETURNED_WITH_NOTES.
 * Carga la anotacion pendiente del backend (texto, precio anterior y
 * nuevo, snapshot del estado anterior) y permite a la novia abrir un
 * dialog con el comparativo completo.
 *
 * Al aceptar -> backend mueve la boda a SUBMITTED (regresa al coro).
 * Al rechazar -> backend revierte los cambios al snapshot y la boda
 * vuelve a DRAFT.
 */
@Composable
private fun ReturnedWithNotesBanner(
    weddingId: String,
    onResponded: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apiClient = remember { com.pacemdeus.bodas.data.network.ApiClient.get(context) }

    var anotacion by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showComparativo by remember { mutableStateOf(false) }
    var isResponding by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(weddingId) {
        isLoading = true
        apiClient.getAnotacionPendiente(weddingId) { result ->
            isLoading = false
            anotacion = if (result is com.pacemdeus.bodas.data.network.ApiResult.Success)
                result.data else null
        }
    }

    com.pacemdeus.bodas.ui.components.PacemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "El coro propuso cambios",
                color = Brown,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))

        if (isLoading) {
            CircularProgressIndicator(
                color = Gold, strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
            return@PacemCard
        }

        val a = anotacion
        if (a == null) {
            Text(
                "No pudimos cargar la propuesta del coro. Intenta de nuevo.",
                color = Sand, fontSize = 12.sp
            )
            return@PacemCard
        }

        val nota = a.optString("texto_nota", "")
        val precioAntes = a.optDouble("precio_anterior", 0.0)
        val precioNuevo = a.optDouble("precio_nuevo", 0.0)

        Text(
            "\u201C$nota\u201D",
            color = Brown, fontSize = 13.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
        Spacer(Modifier.height(8.dp))

        if (precioAntes != precioNuevo) {
            Row {
                Text("Precio anterior: ", color = Sand, fontSize = 12.sp)
                Text(
                    "S/. ${"%.2f".format(precioAntes)}",
                    color = Brown, fontSize = 12.sp
                )
            }
            Row {
                Text("Precio propuesto: ", color = Sand, fontSize = 12.sp)
                Text(
                    "S/. ${"%.2f".format(precioNuevo)}",
                    color = Gold, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        com.pacemdeus.bodas.ui.components.OutlineGoldButton(
            text = "Ver detalle del comparativo",
            onClick = { showComparativo = true }
        )
        Spacer(Modifier.height(6.dp))

        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isResponding) return@Button
                    isResponding = true
                    apiClient.responderAnotacion(weddingId, aceptar = true) { result ->
                        isResponding = false
                        when (result) {
                            is com.pacemdeus.bodas.data.network.ApiResult.Success -> onResponded()
                            is com.pacemdeus.bodas.data.network.ApiResult.Error -> errorMsg = result.message
                            else -> {}
                        }
                    }
                },
                enabled = !isResponding,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Success,
                    contentColor = Color.White
                )
            ) { Text("Aceptar cambios", fontWeight = FontWeight.SemiBold) }

            Button(
                onClick = {
                    if (isResponding) return@Button
                    isResponding = true
                    apiClient.responderAnotacion(weddingId, aceptar = false) { result ->
                        isResponding = false
                        when (result) {
                            is com.pacemdeus.bodas.data.network.ApiResult.Success -> onResponded()
                            is com.pacemdeus.bodas.data.network.ApiResult.Error -> errorMsg = result.message
                            else -> {}
                        }
                    }
                },
                enabled = !isResponding,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = Color.White
                )
            ) { Text("Rechazar", fontWeight = FontWeight.SemiBold) }
        }

        if (errorMsg != null) {
            Spacer(Modifier.height(4.dp))
            Text(errorMsg ?: "", color = Danger, fontSize = 11.sp)
        }
    }

    // Dialog con el comparativo antes/despues
    if (showComparativo && anotacion != null) {
        ComparativoDialog(
            anotacion = anotacion!!,
            onDismiss = { showComparativo = false }
        )
    }
}

@Composable
private fun ComparativoDialog(
    anotacion: org.json.JSONObject,
    onDismiss: () -> Unit
) {
    val snapshot = anotacion.optJSONObject("snapshot_antes")
    val campos = anotacion.optString("campos_modificados", "")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Comparativo de cambios", color = Brown,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (campos.isNotBlank()) {
                    Text(
                        "Campos modificados: $campos",
                        color = Sand, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Text("Antes:", color = Brown,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                if (snapshot != null) {
                    ComparativoRow("Local", snapshot.optString("venue_name", "—"))
                    ComparativoRow("Direccion", snapshot.optString("venue_address", "—"))
                    val instAntes = snapshot.optJSONArray("instrumentos")
                    val instCount = instAntes?.length() ?: 0
                    ComparativoRow("Instrumentos extra", instCount.toString())
                    val setlistAntes = snapshot.optJSONArray("setlist")
                    val setlistCount = setlistAntes?.length() ?: 0
                    ComparativoRow("Cantos en setlist", setlistCount.toString())
                    ComparativoRow(
                        "Precio total",
                        "S/. ${"%.2f".format(snapshot.optDouble("precio_total", 0.0))}"
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Ahora (propuesta del coro):", color = Brown,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Toca \u201CAceptar cambios\u201D para revisar el detalle " +
                        "actualizado de tu boda en las pestanas del menu. Si " +
                        "rechazas, tu version anterior se restaura.",
                    color = Brown, fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Gold, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Cream
    )
}

@Composable
private fun ComparativoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", color = Sand, fontSize = 12.sp)
        Text(value, color = Brown, fontSize = 12.sp,
            fontWeight = FontWeight.Medium)
    }
}

/**
 * Botón compacto "Editar" / "Cambiar X" que va inline debajo del contenido
 * de una tarjeta. Reemplaza la fila de ActionListItem con icono grande +
 * subtítulo que ocupaba mucho espacio en la home original.
 *
 * Se alinea a la derecha (justify-end) con icono + texto en gold.
 */
@Composable
private fun InlineEditButton(
    label: String = "Editar",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = Gold,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}
