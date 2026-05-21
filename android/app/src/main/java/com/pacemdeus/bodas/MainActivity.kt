package com.pacemdeus.bodas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pacemdeus.bodas.data.UserRole
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.session.SessionManager
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
import com.pacemdeus.bodas.ui.screens.couple.GalleryScreen
import com.pacemdeus.bodas.ui.screens.couple.InstrumentsScreen
import com.pacemdeus.bodas.ui.screens.couple.PlannerPickerScreen
import com.pacemdeus.bodas.ui.screens.couple.SetlistScreen
import com.pacemdeus.bodas.ui.screens.planner.PlannerDashboardScreen
import com.pacemdeus.bodas.ui.screens.planner.PlannerDetailScreen
import com.pacemdeus.bodas.ui.theme.PacemDeusBodasTheme

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
    data class Gallery(val weddingId: String) : Screen()
    data class PlannerPick(val weddingId: String, val currentPlannerId: String?) : Screen()

    object CoordinatorHome      : Screen()
    object Map                  : Screen()
    object Approve              : Screen()
    data class WeddingDetail(val weddingId: String) : Screen()
    data class Camera(val weddingId: String) : Screen()
    data class AssignPlanner(val weddingId: String) : Screen()
    // Vistas read-only para que el admin pueda revisar el ensamble
    // (setlist + instrumentos) de una boda antes de aprobar. Reutilizan
    // las pantallas del couple pasandole weddingIdOverride.
    data class AdminSetlist(val weddingId: String) : Screen()
    data class AdminInstruments(val weddingId: String) : Screen()

    object PlannerDashboard     : Screen()
    data class PlannerDetail(val weddingId: String) : Screen()
}

class MainActivity : ComponentActivity() {

    // Launcher para POST_NOTIFICATIONS. Android 13+ exige permiso runtime
    // para mostrar notifications en la bandeja del sistema. Si el usuario
    // niega, los push siguen llegando a FCM pero no se muestran al usuario.
    // Las notificaciones internas (polling con BD) siguen visibles dentro
    // de la app igual.
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { /* no nos importa el resultado, fire-and-forget */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Canal de notificaciones del coro. Idempotente: si ya existe,
        // Android lo ignora; lo necesitamos creado para que las
        // notificaciones de cambio de estado de la boda salgan en
        // Android 8+ (requisito de la plataforma).
        com.pacemdeus.bodas.services.NotificationHelper.createChannel(this)

        // En Android 13+ (API 33+) hay que pedir el permiso runtime de
        // POST_NOTIFICATIONS o las push de FCM nunca se muestran.
        // Disparamos la solicitud aqui (una vez por instalacion) si aun
        // no fue otorgado.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermissionLauncher.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        // Deep link inicial proveniente de un tap en notificacion push
        // cuando la app estaba killed (cold start). Se acepta el id por dos
        // claves distintas segun como haya entrado:
        //   - EXTRA_WEDDING_ID ("wedding_id"): cuando el servicio FCM
        //     creo manualmente la notif (foreground via onMessageReceived).
        //   - "id_boda": clave original del data payload del backend. Cuando
        //     la app esta killed/background y el mensaje incluye un block
        //     `notification`, el FCM SDK muestra la notif del sistema solo
        //     y entrega los `data` como extras del Intent con sus claves
        //     originales — ya no pasa por nuestro mostrarNotificacion().
        pendingDeepLinkWeddingId.value = extractWeddingIdExtra(intent)

        setContent {
            PacemDeusBodasTheme {
                PacemDeusBodas()
            }
        }
    }

    /**
     * Cuando la app ya esta corriendo (en background o foreground) y el
     * usuario toca una notificacion push, Android no recrea la activity:
     * llama a onNewIntent con el nuevo Intent (gracias a launchMode
     * "singleTop" en el manifest + FLAG_ACTIVITY_SINGLE_TOP en el
     * PendingIntent). Aqui actualizamos el deep link pendiente; el
     * composable observa el cambio via Compose state y rerutea.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractWeddingIdExtra(intent)?.let { id ->
            pendingDeepLinkWeddingId.value = id
        }
    }

    companion object {
        /** Clave del extra del Intent donde viaja el id de la boda. */
        const val EXTRA_WEDDING_ID = "wedding_id"

        /**
         * Clave que usa el FCM SDK cuando entrega los `data` del push
         * directamente como Intent extras (caso app killed/background
         * con block `notification`). Coincide con el nombre del campo
         * en el data payload del backend (shared/notifications.py).
         */
        const val EXTRA_WEDDING_ID_FCM = "id_boda"

        /**
         * Lee el id de la boda del Intent intentando primero la clave
         * propia (EXTRA_WEDDING_ID, usada por mostrarNotificacion cuando
         * el servicio FCM construye la notif manualmente) y cayendo a
         * la clave del FCM SDK (EXTRA_WEDDING_ID_FCM) si la primera no
         * esta. Centralizar aqui evita duplicar la logica en onCreate
         * y onNewIntent.
         */
        fun extractWeddingIdExtra(intent: Intent?): String? {
            if (intent == null) return null
            return intent.getStringExtra(EXTRA_WEDDING_ID)
                ?: intent.getStringExtra(EXTRA_WEDDING_ID_FCM)
        }

        /**
         * Deep link pendiente expuesto a la composable. Se vive como
         * Compose state para que onNewIntent pueda actualizarlo y la UI
         * reaccione automaticamente. Companion object para permitir el
         * acceso desde el composable sin pasar el activity por parametro.
         * El composable consume el valor (y lo deja en null) cuando
         * efectivamente navega a la pantalla destino.
         */
        val pendingDeepLinkWeddingId =
            androidx.compose.runtime.mutableStateOf<String?>(null)
    }
}

@Composable
fun PacemDeusBodas() {

    val context = LocalContext.current
    val sessionManager = remember { SessionManager.get(context) }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
    var session by remember { mutableStateOf<UserSession?>(null) }

    // Al arrancar la app intentamos restaurar la sesion guardada en
    // SharedPreferences. Si existe un token valido, saltamos el login.
    LaunchedEffect(Unit) {
        sessionManager.loadSession()?.let { restored ->
            session = restored
        }
    }

    fun goHomeForRole() {
        // Si hay un deep link pendiente (tap en notificacion push con
        // id_boda), ir directamente al detalle correspondiente segun
        // el rol. Para COUPLE no aplica: la pareja ya esta en su unica
        // boda al ir al home, no necesitamos ruta especial.
        val deepLink = MainActivity.pendingDeepLinkWeddingId.value
        if (deepLink != null && session != null) {
            currentScreen = when (session?.user?.role) {
                UserRole.ADMIN           -> Screen.WeddingDetail(deepLink)
                UserRole.WEDDING_PLANNER -> Screen.PlannerDetail(deepLink)
                UserRole.COUPLE          -> Screen.CoupleHome
                null                     -> Screen.Login
            }
            // Consumir: dejar el valor en null para que el proximo
            // goHomeForRole() (ej. logout y nuevo login) no rerutee.
            MainActivity.pendingDeepLinkWeddingId.value = null
            return
        }
        currentScreen = when (session?.user?.role) {
            UserRole.COUPLE          -> Screen.CoupleHome
            UserRole.ADMIN           -> Screen.CoordinatorHome
            UserRole.WEDDING_PLANNER -> Screen.PlannerDashboard
            null                     -> Screen.Login
        }
    }

    fun logout() {
        sessionManager.clear()
        // Limpiar caches offline para que el proximo usuario que se loguee
        // en este device no vea datos de la sesion anterior (HU-06).
        com.pacemdeus.bodas.data.local.OfflineWeddingCache.get(context).clear()
        com.pacemdeus.bodas.data.local.SetlistDatabase.get(context).clearAll()
        session = null
        currentScreen = Screen.Login
    }

    // Salvaguarda: redirigir a Login si una pantalla autenticada se carga sin sesion.
    val currentSession = session
    val isPublicScreen = currentScreen is Screen.Splash ||
            currentScreen is Screen.Login ||
            currentScreen is Screen.Register
    if (!isPublicScreen && currentSession == null) {
        LaunchedEffect(Unit) { currentScreen = Screen.Login }
        return
    }

    // Reaccionar a deep links que llegan mientras la app ya esta corriendo
    // (onNewIntent actualiza MainActivity.pendingDeepLinkWeddingId). Si hay
    // sesion activa y un deep link nuevo, rerutear inmediatamente. Si no
    // hay sesion, el deep link queda en el companion object esperando al
    // proximo goHomeForRole() post-login.
    val pendingDeepLink by MainActivity.pendingDeepLinkWeddingId
    LaunchedEffect(pendingDeepLink) {
        if (pendingDeepLink != null && currentSession != null) {
            goHomeForRole()
        }
    }

    when (val screen = currentScreen) {

        is Screen.Splash -> SplashScreen(
            onTimeout = {
                if (session != null) goHomeForRole()
                else currentScreen = Screen.Login
            }
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

        is Screen.CoupleHome -> currentSession?.let { s ->
            CoupleHomeScreen(
                session = s,
                onCreateWedding = { currentScreen = Screen.CreateEditWedding(null) },
                onEditWedding = { currentScreen = Screen.CreateEditWedding(it) },
                onOpenAssembly = { currentScreen = Screen.Assembly },
                onOpenSetlist  = { currentScreen = Screen.Setlist },
                onOpenInstruments = { currentScreen = Screen.Instruments },
                onOpenContract = { currentScreen = Screen.Contract(it) },
                onOpenGallery = { currentScreen = Screen.Gallery(it) },
                onPickPlanner = { wId, plId -> currentScreen = Screen.PlannerPick(wId, plId) },
                onLogout = { logout() }
            )
        }

        is Screen.Gallery -> currentSession?.let { s ->
            GalleryScreen(
                session = s,
                weddingId = screen.weddingId,
                onBack = {
                    // Volver al home/detail correspondiente segun el rol
                    currentScreen = when (s.user.role) {
                        com.pacemdeus.bodas.data.UserRole.WEDDING_PLANNER ->
                            Screen.PlannerDetail(screen.weddingId)
                        com.pacemdeus.bodas.data.UserRole.ADMIN ->
                            Screen.WeddingDetail(screen.weddingId)
                        else ->
                            Screen.CoupleHome
                    }
                }
            )
        }

        is Screen.PlannerPick -> currentSession?.let { s ->
            PlannerPickerScreen(
                session = s,
                weddingId = screen.weddingId,
                currentPlannerId = screen.currentPlannerId,
                onBack = { currentScreen = Screen.CoupleHome },
                onPicked = { currentScreen = Screen.CoupleHome }
            )
        }

        is Screen.CreateEditWedding -> currentSession?.let { s ->
            CreateEditWeddingScreen(
                session = s,
                weddingId = screen.weddingId,
                onBack = { currentScreen = Screen.CoupleHome },
                onSaved = { currentScreen = Screen.CoupleHome }
            )
        }

        is Screen.Assembly -> currentSession?.let { s ->
            AssemblyScreen(
                session = s,
                onBack = { currentScreen = Screen.CoupleHome },
                onOpenHome = { currentScreen = Screen.CoupleHome },
                onOpenSetlist = { currentScreen = Screen.Setlist },
                onOpenInstruments = { currentScreen = Screen.Instruments }
            )
        }

        is Screen.Setlist -> currentSession?.let { s ->
            SetlistScreen(
                session = s,
                onBack = { currentScreen = Screen.CoupleHome },
                onOpenHome = { currentScreen = Screen.CoupleHome },
                onOpenAssembly = { currentScreen = Screen.Assembly }
            )
        }

        is Screen.Instruments -> currentSession?.let { s ->
            InstrumentsScreen(
                session = s,
                onBack = { currentScreen = Screen.CoupleHome },
                onSaved = { currentScreen = Screen.CoupleHome }
            )
        }

        is Screen.Contract -> currentSession?.let { s ->
            ContractScreen(
                weddingId = screen.weddingId,
                onBack = {
                    currentScreen = when (s.user.role) {
                        UserRole.ADMIN           -> Screen.WeddingDetail(screen.weddingId)
                        UserRole.WEDDING_PLANNER -> Screen.PlannerDetail(screen.weddingId)
                        else                     -> Screen.CoupleHome
                    }
                }
            )
        }

        is Screen.CoordinatorHome -> CoordinatorHomeScreen(
            onOpenDetail = { currentScreen = Screen.WeddingDetail(it) },
            onOpenMap     = { currentScreen = Screen.Map },
            onOpenApprove = { currentScreen = Screen.Approve },
            onLogout = { logout() }
        )

        is Screen.Map -> MapScreen(
            onOpenHome    = { currentScreen = Screen.CoordinatorHome },
            onOpenApprove = { currentScreen = Screen.Approve },
            onOpenDetail  = { currentScreen = Screen.WeddingDetail(it) },
            onLogout = { logout() }
        )

        is Screen.Approve -> ApproveScreen(
            onOpenHome = { currentScreen = Screen.CoordinatorHome },
            onOpenMap  = { currentScreen = Screen.Map },
            onOpenDetail = { currentScreen = Screen.WeddingDetail(it) },
            onLogout = { logout() }
        )

        is Screen.WeddingDetail -> WeddingDetailScreen(
            weddingId = screen.weddingId,
            onBack = { currentScreen = Screen.CoordinatorHome },
            onOpenGallery = { currentScreen = Screen.Gallery(screen.weddingId) },
            onOpenSetlist = { currentScreen = Screen.AdminSetlist(screen.weddingId) },
            onOpenInstruments = { currentScreen = Screen.AdminInstruments(screen.weddingId) },
            onAssignPlanner = { currentScreen = Screen.AssignPlanner(screen.weddingId) },
            onOpenContract = { currentScreen = Screen.Contract(screen.weddingId) }
        )

        is Screen.Camera -> CameraScreen(
            weddingId = screen.weddingId,
            onBack = { currentScreen = Screen.WeddingDetail(screen.weddingId) },
            onUploaded = { currentScreen = Screen.WeddingDetail(screen.weddingId) }
        )

        is Screen.AdminSetlist -> currentSession?.let { s ->
            SetlistScreen(
                session = s,
                weddingIdOverride = screen.weddingId,
                onBack = { currentScreen = Screen.WeddingDetail(screen.weddingId) }
            )
        }

        is Screen.AdminInstruments -> currentSession?.let { s ->
            InstrumentsScreen(
                session = s,
                weddingIdOverride = screen.weddingId,
                onBack = { currentScreen = Screen.WeddingDetail(screen.weddingId) }
            )
        }

        is Screen.AssignPlanner -> AssignPlannerScreen(
            weddingId = screen.weddingId,
            onBack = { currentScreen = Screen.WeddingDetail(screen.weddingId) },
            onConfirm = { currentScreen = Screen.WeddingDetail(screen.weddingId) }
        )

        is Screen.PlannerDashboard -> currentSession?.let { s ->
            PlannerDashboardScreen(
                session = s,
                onOpenDetail = { currentScreen = Screen.PlannerDetail(it) },
                onLogout = { logout() }
            )
        }

        is Screen.PlannerDetail -> PlannerDetailScreen(
            weddingId = screen.weddingId,
            onBack = { currentScreen = Screen.PlannerDashboard },
            onOpenContract = { currentScreen = Screen.Contract(screen.weddingId) },
            onOpenGallery = { currentScreen = Screen.Gallery(screen.weddingId) }
        )
    }
}
