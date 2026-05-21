package com.pacemdeus.bodas.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.R
import com.pacemdeus.bodas.data.UserRole
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.data.session.SessionManager
import kotlinx.coroutines.launch
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.BrownLight
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Divider
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldDark
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.NavBg
import com.pacemdeus.bodas.ui.theme.Sand

// Componentes reutilizables. Centralizan el estilo de la app para no
// repetirlo en cada pantalla. Todos respetan los colores de marca (Gold,
// Brown, Cream) sin redefinirlos en cada lugar.

// La paleta de la app vive ahora en ui/theme/Color.kt. Aqui solo
// la importamos para los componentes reutilizables.

// ─── BARRA SUPERIOR ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacemTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    // Identidad del usuario logueado, mostrada como texto plano en gold
    // a la derecha del titulo. Sin capsula porque pegada con fondo de
    // color queda con vibra de "componente IA generico"; en tipografia
    // discreta y bien espaciada se ve mucho mas profesional.
    val context = LocalContext.current
    val badge = remember(context) {
        SessionManager.get(context).loadSession()?.shortBadge().orEmpty()
    }

    TopAppBar(
        title = {
            Text(
                title,
                color = Brown,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            // Prioridad: si hay onBack, mostramos flecha; si no y hay
            // onMenu, mostramos hamburger; si ninguno, sin icono.
            // No deben coexistir en una misma pantalla.
            when {
                onBack != null -> {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atras",
                            tint = Brown
                        )
                    }
                }
                onMenu != null -> {
                    IconButton(onClick = onMenu) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Brown
                        )
                    }
                }
            }
        },
        actions = {
            if (badge.isNotBlank()) {
                Text(
                    text = badge,
                    color = Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            if (onLogout != null) {
                IconButton(onClick = onLogout) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "Cerrar sesion",
                        tint = Brown
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
    )
}

// ─── BOTONES PRINCIPALES ───────────────────────────────────

@Composable
fun GoldButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color.White,
            disabledContainerColor = Gold.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OutlineGoldButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
    ) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}

/**
 * Variante de GoldButton con un icono a la izquierda del texto. Para
 * acciones donde queremos refuerzo visual de lo que hace el boton
 * (llamar, abrir maps, ver fotos, etc.).
 */
@Composable
fun GoldButtonWithIcon(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color.White,
            disabledContainerColor = Gold.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Variante de OutlineGoldButton con icono dorado a la izquierda. */
@Composable
fun OutlineGoldButtonWithIcon(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Medium)
    }
}

// ─── TARJETAS ──────────────────────────────────────────────

@Composable
fun PacemCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

// ─── ETIQUETA DE SECCION ───────────────────────────────────

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Gold,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/**
 * Variante con icono a la izquierda. Renderiza el icono en gold soft
 * para que no compita visualmente con el texto. Tamano fijo 18dp.
 */
@Composable
fun SectionLabel(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            color = Gold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// ─── INDICADOR DE ESTADO DE BODA ───────────────────────────
// Chip compacto: fondo suave del color del estado + texto del mismo
// color saturado. Permite distinguir el estado de un vistazo sin
// romper la paleta gold/brown de la app.

@Composable
fun StatusBadge(status: WeddingStatus) {
    val dotColor = when (status) {
        WeddingStatus.DRAFT                  -> Color(0xFFC9A227) // ambar
        WeddingStatus.SUBMITTED              -> Color(0xFF1F6FB2) // azul
        WeddingStatus.APPROVED               -> Color(0xFF2E8B3D) // verde
        WeddingStatus.CONTRACTED             -> Color(0xFF6B3FA0) // violeta
        WeddingStatus.CANCELLATION_REQUESTED -> Color(0xFFB23A3A) // rojo apagado
        WeddingStatus.COMPLETED              -> Color(0xFF777777) // gris
        WeddingStatus.RETURNED_WITH_NOTES    -> Color(0xFFD68A1A) // naranja
        WeddingStatus.CANCELLED              -> Color(0xFF555555) // gris oscuro
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            status.displayName(),
            color = Brown,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── TARJETA DE METRICA ────────────────────────────────────

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                label.uppercase(),
                color = Sand,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ─── ESTADOS DE CARGA Y VACIO ──────────────────────────────

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize().background(Cream),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Gold)
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = Sand,
            fontSize = 14.sp
        )
    }
}

// ─── FONDO PANTALLA COMPLETA ───────────────────────────────

@Composable
fun ScreenBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Cream)) {
        content()
    }
}

// ─── ESTILO DE TEXTFIELDS ──────────────────────────────────

/**
 * Colores en tono dorado para los OutlinedTextField. Centraliza el
 * estilo para que todos los inputs de formularios (login, registro,
 * crear boda, etc.) se vean iguales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun goldTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold,
    unfocusedBorderColor = Sand,
    focusedLabelColor = Gold,
    cursorColor = Gold
)

// DRAWER (MENU LATERAL HAMBURGER)
// Reemplaza el bottom navigation que tenian los home screens. La
// navegacion principal vive en este drawer: un solo lugar para todos
// los destinos del rol logueado, mas el logout.

/**
 * Item del menu lateral. Cada home screen construye su propia lista
 * y la pasa a PacemDrawerScaffold.
 */
data class PacemDrawerItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Contenido del drawer: header con imagen del coro + degradado oscuro
 * que aloja titulo y subtitulo identitarios, lista de items de
 * navegacion al rol, y al fondo el logout.
 *
 * La imagen del header cambia segun el rol del usuario logueado:
 *   - COUPLE          -> drawer_couple (novios casandose)
 *   - ADMIN / PLANNER -> drawer_admin (identidad del coro)
 */
@Composable
fun PacemDrawerContent(
    items: List<PacemDrawerItem>,
    onItemClick: (Int) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val session = remember(context) { SessionManager.get(context).loadSession() }
    val role = session?.user?.role

    val roleTitle = when (role) {
        UserRole.ADMIN           -> "Coro Pacem Deus"
        UserRole.COUPLE          -> "Coro Pacem Deus"
        UserRole.WEDDING_PLANNER -> "Coro Pacem Deus"
        else                     -> "Coro Pacem Deus"
    }
    val roleSubtitle = when (role) {
        UserRole.ADMIN           -> "Coordinador General"
        UserRole.COUPLE          -> session?.coupleProfile?.displayName().orEmpty()
        UserRole.WEDDING_PLANNER -> session?.plannerProfile?.name.orEmpty()
        else                     -> ""
    }
    // Solo el rol COUPLE conserva el header de imagen-fondo + overlay
    // + texto encima (con la foto romantica de los novios). Para admin
    // y planner usamos el logo institucional del coro con su propio
    // tipografia, asi que no necesita overlay ni texto encima.
    val esCouple = role == UserRole.COUPLE

    ModalDrawerSheet(
        drawerContainerColor = Cream,
        drawerShape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
        modifier = Modifier.fillMaxWidth(0.82f)
    ) {
        if (esCouple) {
            // HEADER COUPLE: imagen de fondo + overlay oscuro + texto encima.
            // Conserva el design romantico para la novia.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.drawer_couple),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x33000000),
                                    Color(0xCC2F1F0F)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 22.dp, bottom = 18.dp, end = 22.dp)
                ) {
                    Text(
                        "Cantamos al Amor de los Amores",
                        color = Cream.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        roleTitle,
                        color = Cream,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    if (roleSubtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            roleSubtitle,
                            color = Cream.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // HEADER ADMIN / PLANNER: logo institucional centrado sobre fondo
            // del mismo cafe oscuro del logo, sin overlay ni texto encima
            // (el logo ya tiene su propia tipografia "Pacem Deus / Voces
            // para Dios"). Subtitle del rol va debajo del logo dentro del
            // mismo header para mantener la identidad del usuario visible.
            //
            // ContentScale.Fit asegura que la tipografia del logo nunca
            // se corte; el padding lateral le da aire. El color de fondo
            // matchea el cafe del logo asi que la transicion es invisible.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF1F140A)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pacem_logo),
                    contentDescription = "Coro Pacem Deus",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentScale = ContentScale.Fit
                )
                if (roleSubtitle.isNotBlank()) {
                    Text(
                        roleSubtitle,
                        color = Cream.copy(alpha = 0.90f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ITEMS de navegacion
        for ((idx, item) in items.withIndex()) {
            NavigationDrawerItem(
                label = {
                    Text(
                        item.label,
                        fontSize = 14.sp,
                        fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                },
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                selected = item.selected,
                onClick = { onItemClick(idx) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = GoldSoft,
                    selectedIconColor = Gold,
                    selectedTextColor = Gold,
                    unselectedContainerColor = Color.Transparent,
                    unselectedIconColor = Brown,
                    unselectedTextColor = Brown
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // Empuja el logout al fondo del drawer
        Spacer(Modifier.weight(1f))

        // Divisor sutil antes del logout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(1.dp)
                .background(Divider)
        )
        Spacer(Modifier.height(6.dp))

        // LOGOUT
        NavigationDrawerItem(
            label = {
                Text(
                    "Cerrar sesion",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            icon = {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            selected = false,
            onClick = onLogout,
            colors = NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = Color.Transparent,
                unselectedIconColor = Brown,
                unselectedTextColor = Brown
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        Spacer(Modifier.height(14.dp))
    }
}

/**
 * Scaffold helper que ya viene envuelto en un ModalNavigationDrawer.
 * Los home screens lo usan en lugar de Scaffold normal: pasan los
 * items del menu y el contenido, y se encargan SOLO de su contenido
 * propio (sin manejar drawer, topbar ni navigation entre tabs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacemDrawerScaffold(
    title: String,
    drawerItems: List<PacemDrawerItem>,
    onLogout: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PacemDrawerContent(
                items = drawerItems,
                onItemClick = { idx ->
                    // Cerrar el drawer y disparar la accion del item.
                    // El close es asincrono pero el onClick se invoca
                    // inmediatamente, asi que la pantalla destino se
                    // monta mientras el drawer hace su animacion.
                    scope.launch { drawerState.close() }
                    drawerItems[idx].onClick()
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    onLogout()
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                PacemTopBar(
                    title = title,
                    onMenu = { scope.launch { drawerState.open() } }
                )
            },
            containerColor = Cream
        ) { padding ->
            content(padding)
        }
    }
}
