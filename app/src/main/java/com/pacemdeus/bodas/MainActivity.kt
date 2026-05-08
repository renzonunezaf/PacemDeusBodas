package com.pacemdeus.bodas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.pacemdeus.bodas.data.DemoData
import com.pacemdeus.bodas.data.SetlistItem
import com.pacemdeus.bodas.data.UserRole
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.ui.screens.SplashScreen
import com.pacemdeus.bodas.ui.screens.auth.LoginScreen
import com.pacemdeus.bodas.ui.screens.auth.RegisterScreen
import com.pacemdeus.bodas.ui.screens.coordinator.ApproveScreen
import com.pacemdeus.bodas.ui.screens.coordinator.AssignPlannerScreen
import com.pacemdeus.bodas.ui.screens.coordinator.CameraScreen
import com.pacemdeus.bodas.ui.screens.coordinator.CoordinatorHomeScreen
import com.pacemdeus.bodas.ui.screens.coordinator.MapScreen
import com.pacemdeus.bodas.ui.screens.coordinator.WeddingDetailScreen
import com.pacemdeus.bodas.ui.screens.couple.AssemblyScreen
import com.pacemdeus.bodas.ui.screens.couple.ContractScreen
import com.pacemdeus.bodas.ui.screens.couple.CoupleHomeScreen
import com.pacemdeus.bodas.ui.screens.couple.CreateEditWeddingScreen
import com.pacemdeus.bodas.ui.screens.couple.InstrumentsScreen
import com.pacemdeus.bodas.ui.screens.couple.SetlistScreen
import com.pacemdeus.bodas.ui.screens.planner.PlannerDashboardScreen
import com.pacemdeus.bodas.ui.screens.planner.PlannerDetailScreen

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas - punto de entrada de la app
// ═══════════════════════════════════════════════════════════════
//
// Sigue el patron del profesor (TiendaApp): una sola Activity,
// sealed class Screen como ruta, var currentScreen + when para
// renderizar la pantalla activa. Todo el estado de dominio
// (sesion, lista de bodas, setlists, instrumentos seleccionados)
// se iza al composable App() y se pasa a las pantallas como
// parametros de funcion.
//
// No se usan ViewModel, Navigation Compose, LazyColumn, Room ni
// Retrofit, ya que estas APIs aun no se han enseñado en el curso.
// ═══════════════════════════════════════════════════════════════

/**
 * Rutas posibles de la app. Las pantallas que reciben parametros
 * (id de boda, id de planner) se modelan como data class para
 * llevar esos valores en el propio Screen.
 */
sealed class Screen {
    object Splash               : Screen()
    object Login                : Screen()
    object Register             : Screen()

    object CoupleHome           : Screen()
    data class CreateEditWedding(val weddingId: String?) : Screen()
    object Assembly             : Screen()
    object Setlist              : Screen()
    object Instruments          : Screen()
    data class Contract(val weddingId: String) : Screen()

    object CoordinatorHome      : Screen()
    object Map                  : Screen()
    object Approve              : Screen()
    data class WeddingDetail(val weddingId: String) : Screen()
    data class Camera(val weddingId: String) : Screen()
    data class AssignPlanner(val weddingId: String) : Screen()

    object PlannerDashboard     : Screen()
    data class PlannerDetail(val weddingId: String) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PacemDeusBodas()
            }
        }
    }
}

/**
 * Composable raiz de la aplicacion. Contiene todo el estado mutable
 * y enruta a la pantalla activa segun currentScreen. Las pantallas
 * leen y modifican este estado a traves de los lambdas que reciben.
 */
@Composable
fun PacemDeusBodas() {

    // ─── Navegacion ────────────────────────────────────────
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }

    // ─── Sesion del usuario actualmente autenticado ────────
    var session by remember { mutableStateOf<UserSession?>(null) }

    // ─── Coleccion de bodas (mutable: se editan en memoria) ─
    val weddings = remember { DemoData.initialWeddings.toMutableStateList() }

    // ─── Setlist global (todas las bodas, agrupable por id) ─
    val setlist = remember { DemoData.initialSetlist.toMutableStateList() }

    // ─── Instrumentos contratados por boda ─────────────────
    val instrumentsByWedding = remember {
        mutableStateMapOf<String, Set<String>>().apply {
            putAll(DemoData.initialWeddingInstruments)
        }
    }

    // ─── Helpers de mutacion compartidos por las pantallas ─

    /** Reemplaza una boda en la lista por una version modificada. */
    fun updateWedding(updated: Wedding) {
        val idx = weddings.indexOfFirst { it.id == updated.id }
        if (idx >= 0) weddings[idx] = updated
    }

    /** Devuelve la boda asociada a la sesion del couple actual, o null. */
    fun activeCoupleWedding(): Wedding? {
        val coupleId = session?.coupleProfile?.id ?: return null
        return weddings.firstOrNull { it.coupleId == coupleId }
    }

    /** Despues de hacer login, redirige a la home segun el rol. */
    fun goHomeForRole() {
        currentScreen = when (session?.user?.role) {
            UserRole.COUPLE          -> Screen.CoupleHome
            UserRole.ADMIN           -> Screen.CoordinatorHome
            UserRole.WEDDING_PLANNER -> Screen.PlannerDashboard
            null                     -> Screen.Login
        }
    }

    /** Cierra la sesion y vuelve a Login. */
    fun logout() {
        session = null
        currentScreen = Screen.Login
    }

    // ─── Enrutado ──────────────────────────────────────────

    // Salvaguarda: las pantallas autenticadas requieren sesion. Si por
    // alguna razon llegamos a una de ellas con session = null, redirigimos
    // a Login. Capturamos session en un val local para que el smart-cast
    // de Kotlin haga `currentSession` no nullable en las ramas autenticadas.
    val currentSession = session
    val isPublicScreen = currentScreen is Screen.Splash ||
            currentScreen is Screen.Login ||
            currentScreen is Screen.Register
    if (!isPublicScreen && currentSession == null) {
        LaunchedEffect(Unit) { currentScreen = Screen.Login }
        return
    }

    when (val screen = currentScreen) {

        is Screen.Splash -> SplashScreen(
            onTimeout = { currentScreen = Screen.Login }
        )

        is Screen.Login -> LoginScreen(
            onAuthenticated = { newSession ->
                session = newSession
                goHomeForRole()
            },
            onGoToRegister = { currentScreen = Screen.Register }
        )

        is Screen.Register -> RegisterScreen(
            onBack = { currentScreen = Screen.Login },
            onRegistered = { newSession ->
                session = newSession
                goHomeForRole()
            }
        )

        // ─── Couple ────────────────────────────────────────

        is Screen.CoupleHome -> currentSession?.let { s ->
            val wed = activeCoupleWedding()
            CoupleHomeScreen(
                session = s,
                wedding = wed,
                setlistCount = wed?.let { w -> setlist.count { it.weddingId == w.id } } ?: 0,
                onCreateWedding = { currentScreen = Screen.CreateEditWedding(null) },
                onEditWedding = { currentScreen = Screen.CreateEditWedding(it) },
                onOpenAssembly = { currentScreen = Screen.Assembly },
                onOpenSetlist  = { currentScreen = Screen.Setlist },
                onOpenInstruments = { currentScreen = Screen.Instruments },
                onOpenContract = { currentScreen = Screen.Contract(it) },
                onSubmit = { id ->
                    weddings.firstOrNull { it.id == id }?.let {
                        updateWedding(it.copy(status = WeddingStatus.SUBMITTED))
                    }
                },
                onRequestCancel = { id ->
                    weddings.firstOrNull { it.id == id }?.let {
                        updateWedding(it.copy(status = WeddingStatus.CANCELLATION_REQUESTED))
                    }
                },
                onLogout = { logout() }
            )
        }

        is Screen.CreateEditWedding -> currentSession?.let { s ->
            CreateEditWeddingScreen(
                session = s,
                existing = screen.weddingId?.let { id -> weddings.firstOrNull { it.id == id } },
                onBack = { currentScreen = Screen.CoupleHome },
                onSave = { saved ->
                    val idx = weddings.indexOfFirst { it.id == saved.id }
                    if (idx >= 0) weddings[idx] = saved else weddings.add(saved)
                    currentScreen = Screen.CoupleHome
                }
            )
        }

        is Screen.Assembly -> {
            val wed = activeCoupleWedding()
            AssemblyScreen(
                wedding = wed,
                setlist = setlist.filter { it.weddingId == (wed?.id ?: "") },
                onBack = { currentScreen = Screen.CoupleHome },
                onOpenHome = { currentScreen = Screen.CoupleHome },
                onOpenSetlist = { currentScreen = Screen.Setlist },
                onOpenInstruments = { currentScreen = Screen.Instruments },
                onAddSong = { weddingId, momentId, songId ->
                    val nextOrder = setlist
                        .count { it.weddingId == weddingId && it.momentId == momentId } + 1
                    setlist.add(
                        SetlistItem(
                            id = "sli-${System.currentTimeMillis()}",
                            weddingId = weddingId,
                            momentId = momentId,
                            songId = songId,
                            displayOrder = nextOrder
                        )
                    )
                },
                onRemoveSong = { itemId ->
                    setlist.removeAll { it.id == itemId }
                }
            )
        }

        is Screen.Setlist -> {
            val wed = activeCoupleWedding()
            SetlistScreen(
                wedding = wed,
                items = setlist.filter { it.weddingId == (wed?.id ?: "") },
                onBack = { currentScreen = Screen.CoupleHome },
                onOpenHome = { currentScreen = Screen.CoupleHome },
                onOpenAssembly = { currentScreen = Screen.Assembly },
                onRemove = { itemId -> setlist.removeAll { it.id == itemId } }
            )
        }

        is Screen.Instruments -> {
            val wed = activeCoupleWedding()
            InstrumentsScreen(
                wedding = wed,
                selected = wed?.let { instrumentsByWedding[it.id] } ?: emptySet(),
                onBack = { currentScreen = Screen.CoupleHome },
                onSave = { weddingId, selectedIds, totalExtra ->
                    instrumentsByWedding[weddingId] = selectedIds
                    weddings.firstOrNull { it.id == weddingId }?.let {
                        updateWedding(it.copy(instrumentsPrice = totalExtra))
                    }
                    currentScreen = Screen.CoupleHome
                }
            )
        }

        is Screen.Contract -> {
            val wed = weddings.firstOrNull { it.id == screen.weddingId }
            ContractScreen(
                wedding = wed,
                instrumentIds = wed?.let { instrumentsByWedding[it.id] } ?: emptySet(),
                onBack = {
                    currentScreen = when (session?.user?.role) {
                        UserRole.ADMIN           -> Screen.WeddingDetail(screen.weddingId)
                        UserRole.WEDDING_PLANNER -> Screen.PlannerDetail(screen.weddingId)
                        else                     -> Screen.CoupleHome
                    }
                }
            )
        }

        // ─── Coordinator (Admin) ──────────────────────────

        is Screen.CoordinatorHome -> CoordinatorHomeScreen(
            weddings = weddings,
            onOpenDetail = { currentScreen = Screen.WeddingDetail(it) },
            onOpenMap     = { currentScreen = Screen.Map },
            onOpenApprove = { currentScreen = Screen.Approve },
            onLogout = { logout() }
        )

        is Screen.Map -> MapScreen(
            weddings = weddings,
            onOpenHome    = { currentScreen = Screen.CoordinatorHome },
            onOpenApprove = { currentScreen = Screen.Approve },
            onOpenDetail  = { currentScreen = Screen.WeddingDetail(it) },
            onLogout = { logout() }
        )

        is Screen.Approve -> ApproveScreen(
            weddings = weddings,
            onOpenHome = { currentScreen = Screen.CoordinatorHome },
            onOpenMap  = { currentScreen = Screen.Map },
            onApprove = { id ->
                weddings.firstOrNull { it.id == id }?.let {
                    updateWedding(it.copy(status = WeddingStatus.APPROVED))
                }
            },
            onReject = { id ->
                weddings.firstOrNull { it.id == id }?.let {
                    updateWedding(it.copy(status = WeddingStatus.DRAFT))
                }
            },
            onLogout = { logout() }
        )

        is Screen.WeddingDetail -> {
            val wed = weddings.firstOrNull { it.id == screen.weddingId }
            WeddingDetailScreen(
                wedding = wed,
                instrumentIds = wed?.let { instrumentsByWedding[it.id] } ?: emptySet(),
                setlistCount = wed?.let { w -> setlist.count { it.weddingId == w.id } } ?: 0,
                onBack = { currentScreen = Screen.CoordinatorHome },
                onTakePhoto = { currentScreen = Screen.Camera(screen.weddingId) },
                onAssignPlanner = { currentScreen = Screen.AssignPlanner(screen.weddingId) },
                onOpenContract = { currentScreen = Screen.Contract(screen.weddingId) }
            )
        }

        is Screen.Camera -> CameraScreen(
            onBack = { currentScreen = Screen.WeddingDetail(screen.weddingId) },
            onSavePhoto = {
                weddings.firstOrNull { it.id == screen.weddingId }?.let {
                    updateWedding(it.copy(venuePhotoTaken = true))
                }
                currentScreen = Screen.WeddingDetail(screen.weddingId)
            }
        )

        is Screen.AssignPlanner -> AssignPlannerScreen(
            wedding = weddings.firstOrNull { it.id == screen.weddingId },
            weddingsAll = weddings,
            onBack = { currentScreen = Screen.WeddingDetail(screen.weddingId) },
            onConfirm = { plannerId ->
                weddings.firstOrNull { it.id == screen.weddingId }?.let {
                    updateWedding(it.copy(plannerId = plannerId))
                }
                currentScreen = Screen.WeddingDetail(screen.weddingId)
            }
        )

        // ─── Wedding planner ──────────────────────────────

        is Screen.PlannerDashboard -> currentSession?.let { s ->
            val plannerId = s.plannerProfile?.id
            PlannerDashboardScreen(
                session = s,
                weddings = weddings.filter { it.plannerId == plannerId },
                onOpenDetail = { currentScreen = Screen.PlannerDetail(it) },
                onLogout = { logout() }
            )
        }

        is Screen.PlannerDetail -> PlannerDetailScreen(
            wedding = weddings.firstOrNull { it.id == screen.weddingId },
            instrumentIds = instrumentsByWedding[screen.weddingId] ?: emptySet(),
            setlistCount = setlist.count { it.weddingId == screen.weddingId },
            onBack = { currentScreen = Screen.PlannerDashboard },
            onOpenContract = { currentScreen = Screen.Contract(screen.weddingId) }
        )
    }
}
