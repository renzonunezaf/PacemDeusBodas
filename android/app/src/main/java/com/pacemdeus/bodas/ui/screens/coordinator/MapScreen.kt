package com.pacemdeus.bodas.ui.screens.coordinator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.util.isMapsApiKeyConfigured
import com.pacemdeus.bodas.ui.util.openInMaps
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla de mapa de eventos (HU-04 con Google Maps SDK real).
//
// Carga del backend todas las bodas (apiClient.listBodas) y muestra
// en un GoogleMap composable las que tengan coordenadas registradas,
// como marcadores tocables. Al tocar un marcador (o su info window)
// se navega al detalle del evento.
//
// Centro inicial del mapa: el promedio de coordenadas de las bodas
// con ubicacion, o Lima centro si no hay ninguna.
//
// FALLBACK: si la Maps API Key no esta configurada en el manifest
// (manifestPlaceholders["MAPS_API_KEY"] vacio), el SDK no carga el
// mapa correctamente. En ese caso se muestra una lista de venues
// con boton para abrir cada uno en la app de mapas del sistema via
// Intent ACTION_VIEW + URI geo:. Esto permite que la app sea util
// incluso si el desarrollador no configuro la API key.

// Coordenadas centro de Lima como camara por defecto del mapa
private const val LIMA_LAT = -12.046374
private const val LIMA_LNG = -77.042793
private const val DEFAULT_ZOOM = 11f

// ─── Paleta y labels por estado de boda ─────────────────────────────
// Cada estado tiene un color visualmente distintivo en el mapa:
//   - DRAFT:               gris medio oscuro (la novia esta armando)
//   - SUBMITTED:           azul (esperando revision del coro)
//   - APPROVED:            verde (admin aprobo, falta contrato)
//   - CONTRACTED:          rojo (contrato firmado, evento confirmado)
//   - RETURNED_WITH_NOTES: ambar (coro propuso cambios, accion pareja)
//   - COMPLETED:           gris claro (evento ya paso, sin accion)
// CANCELLATION_REQUESTED no se muestra (el backend ya lo filtra).
private val ESTADO_COLOR_DRAFT      = Color(0xFF6B6B6B)
private val ESTADO_COLOR_SUBMITTED  = Color(0xFF2563EB)
private val ESTADO_COLOR_APPROVED   = Color(0xFF22C55E)
private val ESTADO_COLOR_CONTRACTED = Color(0xFFDC2626)
private val ESTADO_COLOR_RETURNED   = Color(0xFFF59E0B)
private val ESTADO_COLOR_COMPLETED  = Color(0xFFD1D5DB)
private val ESTADO_COLOR_FALLBACK   = Color(0xFF9CA3AF)

private fun colorParaEstado(estado: String): Color = when (estado) {
    "DRAFT"               -> ESTADO_COLOR_DRAFT
    "SUBMITTED"           -> ESTADO_COLOR_SUBMITTED
    "APPROVED"            -> ESTADO_COLOR_APPROVED
    "CONTRACTED"          -> ESTADO_COLOR_CONTRACTED
    "RETURNED_WITH_NOTES" -> ESTADO_COLOR_RETURNED
    "COMPLETED"           -> ESTADO_COLOR_COMPLETED
    else                  -> ESTADO_COLOR_FALLBACK
}

private fun labelParaEstado(estado: String): String = when (estado) {
    "DRAFT"               -> "Borrador"
    "SUBMITTED"           -> "Por aprobar"
    "APPROVED"            -> "Aprobado"
    "CONTRACTED"          -> "Contratado"
    "RETURNED_WITH_NOTES" -> "Devuelto para revision"
    "COMPLETED"           -> "Completado"
    else                  -> estado
}

/**
 * Genera un BitmapDescriptor circular (sombra + borde blanco + fill
 * coloreado) para usar como icono del marcador. Se llama solo 6 veces
 * (uno por estado) y se cachea en remember, no por boda.
 *
 * IMPORTANTE: Maps SDK debe estar inicializado antes de invocar a
 * BitmapDescriptorFactory.fromBitmap. Por eso el caller hace
 * MapsInitializer.initialize(context) inmediatamente antes.
 */
private fun crearMarcadorColoreado(colorArgb: Int, density: Float): BitmapDescriptor {
    val sizePx = (36f * density).toInt()                 // 36dp total
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = sizePx / 2f - 2f * density              // deja aire al borde

    // Sombra: circulo translucido offset 1dp hacia abajo
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    shadowPaint.color = 0x33000000
    canvas.drawCircle(cx, cy + density, radius, shadowPaint)

    // Borde blanco para contraste sobre cualquier color de mapa
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    borderPaint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(cx, cy, radius, borderPaint)

    // Fill coloreado segun estado
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    fillPaint.color = colorArgb
    canvas.drawCircle(cx, cy, radius - 2.5f * density, fillPaint)

    return BitmapDescriptorFactory.fromBitmap(bmp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenHome: () -> Unit = {},
    onOpenApprove: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    // Mes visible: por defecto el actual. El selector arriba del mapa
    // permite navegar mes-a-mes y ver solo los eventos de ese mes.
    var mesActual by remember {
        mutableStateOf(com.pacemdeus.bodas.ui.components.YearMonthSimple.now())
    }
    var bodasMes by remember { mutableStateOf<List<MapBoda>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val mapsConfigurado = remember { isMapsApiKeyConfigured(context) }

    // Permiso de ubicacion en runtime para que Maps SDK sesga las busquedas
    // hacia la ubicacion real del dispositivo (Lima en el emulador configurado).
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Recarga al cambiar de mes
    LaunchedEffect(mesActual) {
        isLoading = true
        apiClient.getMapaBodasMes(mesActual.year, mesActual.month) { result ->
            isLoading = false
            when (result) {
                is ApiResult.Success -> {
                    bodasMes = parseMapBodas(result.data)
                    errorMessage = null
                }
                is ApiResult.Error -> {
                    bodasMes = emptyList()
                    errorMessage = result.message
                }
                else -> {}
            }
        }
    }

    com.pacemdeus.bodas.ui.components.PacemDrawerScaffold(
        title = "Mapa de eventos",
        drawerItems = listOf(
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Eventos",
                icon = Icons.Default.EventNote,
                selected = false,
                onClick = onOpenHome
            ),
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Mapa",
                icon = Icons.Default.Map,
                selected = true,
                onClick = {}
            ),
            com.pacemdeus.bodas.ui.components.PacemDrawerItem(
                label = "Por aprobar",
                icon = Icons.Default.AssignmentTurnedIn,
                selected = false,
                onClick = onOpenApprove
            )
        ),
        onLogout = onLogout
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Selector de mes
            MesNavegador(
                mes = mesActual,
                cantidadEventos = bodasMes.size,
                onAnterior = { mesActual = mesActual.minusMonths(1) },
                onSiguiente = { mesActual = mesActual.plusMonths(1) }
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold)
                }
                return@PacemDrawerScaffold
            }

            if (bodasMes.isEmpty()) {
                EmptyState(errorMessage ?: "Sin eventos en este mes")
                return@PacemDrawerScaffold
            }

            if (mapsConfigurado) {
                MapaConMarcadores(
                    modifier = Modifier.fillMaxSize(),
                    bodas = bodasMes,
                    hasLocationPermission = hasLocationPermission,
                    onOpenDetail = onOpenDetail
                )
            } else {
                ListadoFallback(
                    modifier = Modifier.fillMaxSize(),
                    bodas = bodasMes,
                    onOpenDetail = onOpenDetail,
                    errorMessage = errorMessage
                )
            }
        }
    }
}

/**
 * Header con flechas para cambiar mes y contador de eventos visibles.
 */
@Composable
private fun MesNavegador(
    mes: com.pacemdeus.bodas.ui.components.YearMonthSimple,
    cantidadEventos: Int,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit
) {
    val nombreMes = listOf(
        "Enero","Febrero","Marzo","Abril","Mayo","Junio",
        "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"
    )[mes.month - 1]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = onAnterior) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = "Mes anterior",
                tint = Gold
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$nombreMes ${mes.year}",
                color = Brown, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Text(
                "$cantidadEventos evento${if (cantidadEventos != 1) "s" else ""}",
                color = Sand, fontSize = 11.sp
            )
        }
        androidx.compose.material3.IconButton(onClick = onSiguiente) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Mes siguiente",
                tint = Gold
            )
        }
    }
}

/**
 * Modelo ligero para los pins del mapa, derivado del endpoint nuevo
 * GET /mapa/bodas. Tiene hora y movilidad para mostrar en el snippet.
 */
data class MapBoda(
    val id: String,
    val fecha: String,
    val hora: String,
    val pareja: String,
    val nombreLocal: String,
    val direccionLocal: String,
    val lat: Double,
    val lng: Double,
    val movilidad: Double,
    val estado: String
)

private fun parseMapBodas(json: org.json.JSONObject): List<MapBoda> {
    val out = mutableListOf<MapBoda>()
    val arr = json.optJSONArray("bodas") ?: return out
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(MapBoda(
            id = o.optInt("id_boda").toString(),
            fecha = o.optString("fecha", ""),
            hora = o.optString("hora", ""),
            pareja = o.optString("pareja", ""),
            nombreLocal = o.optString("nombre_local", ""),
            direccionLocal = o.optString("direccion_local", ""),
            lat = o.optDouble("latitud", 0.0),
            lng = o.optDouble("longitud", 0.0),
            movilidad = o.optDouble("precio_movilidad", 0.0),
            estado = o.optString("estado", "")
        ))
    }
    return out
}

/** Devuelve "Movilidad: S/. XX.XX" o "Sin movilidad" si es 0. */
private fun formatMovilidad(monto: Double): String {
    return if (monto > 0) "Movilidad: S/. ${"%.2f".format(monto)}"
           else "Sin movilidad"
}

/**
 * Vista principal con el GoogleMap composable y un marcador por cada
 * boda con coordenadas. El centro del mapa es el promedio de las
 * coordenadas de las bodas para que todas queden visibles.
 */
@Composable
private fun MapaConMarcadores(
    modifier: Modifier = Modifier,
    bodas: List<MapBoda>,
    hasLocationPermission: Boolean,
    onOpenDetail: (String) -> Unit
) {
    val context = LocalContext.current
    // Centroide del conjunto de bodas del mes, o Lima centro si vacio
    val centro = remember(bodas) {
        if (bodas.isEmpty()) LatLng(LIMA_LAT, LIMA_LNG)
        else {
            val lat = bodas.map { it.lat }.average()
            val lng = bodas.map { it.lng }.average()
            LatLng(lat, lng)
        }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(centro, DEFAULT_ZOOM)
    }
    // Reposicionar la camara cuando cambia el mes (cambia el conjunto)
    LaunchedEffect(centro) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(centro, DEFAULT_ZOOM)
    }

    // Cache de iconos: un BitmapDescriptor por estado, no por boda.
    // Solo hay 6 estados visibles, asi que se generan 6 bitmaps una vez
    // y se reutilizan para todos los marcadores del mes.
    //
    // IMPORTANTE: MapsInitializer.initialize(context) debe correr antes
    // de BitmapDescriptorFactory.fromBitmap, porque el GoogleMap composable
    // se monta DESPUES de que este remember{} corre. Sin esta llamada el
    // factory todavia no esta listo y la primera fromBitmap lanza
    // NullPointerException (IBitmapDescriptorFactory is not initialized).
    val density = LocalDensity.current.density
    val markerIcons = remember(density) {
        @Suppress("DEPRECATION")
        MapsInitializer.initialize(context)
        listOf(
            "DRAFT", "SUBMITTED", "APPROVED",
            "CONTRACTED", "RETURNED_WITH_NOTES", "COMPLETED"
        ).associateWith { estado ->
            crearMarcadorColoreado(colorParaEstado(estado).toArgb(), density)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = MapType.NORMAL,
            // isMyLocationEnabled activa la capa de ubicacion del SDK,
            // lo que sesga busquedas y autocomplete hacia la ubicacion real
            // del dispositivo (Lima en el emulador con location configurado).
            isMyLocationEnabled = hasLocationPermission
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            compassEnabled = true,
            myLocationButtonEnabled = hasLocationPermission
        )
    ) {
        for (b in bodas) {
            val color = colorParaEstado(b.estado)
            val icon = markerIcons[b.estado] ?: markerIcons["DRAFT"]!!
            MarkerInfoWindow(
                state = MarkerState(position = LatLng(b.lat, b.lng)),
                icon = icon,
                onInfoWindowClick = { onOpenDetail(b.id) }
            ) {
                BodaInfoCard(boda = b, estadoColor = color)
            }
        }
    }
}

/**
 * Contenido del info window que aparece al tocar un marcador. Muestra
 * la pareja, la hora, el local y un encabezado con bolita de color y
 * el label legible del estado. Toda el card es tappable (el handler
 * onInfoWindowClick se setea en el marker padre).
 *
 * Nota: maps-compose captura este contenido como bitmap al mostrarlo,
 * asi que cualquier animacion o estado dinamico dentro no se actualizara
 * hasta que la ventana se vuelva a abrir. Para nuestro contenido
 * estatico es lo esperado.
 */
@Composable
private fun BodaInfoCard(boda: MapBoda, estadoColor: Color) {
    Column(
        modifier = Modifier
            .background(Cream, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(min = 200.dp, max = 280.dp)
    ) {
        // Header con bolita y label del estado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(estadoColor, CircleShape)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                labelParaEstado(boda.estado),
                color = Brown,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            boda.pareja,
            color = Brown,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${boda.hora}  ·  ${boda.nombreLocal}",
            color = Sand,
            fontSize = 11.sp,
            maxLines = 2
        )
        if (boda.movilidad > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                formatMovilidad(boda.movilidad),
                color = Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ListadoFallback(
    modifier: Modifier = Modifier,
    bodas: List<MapBoda>,
    onOpenDetail: (String) -> Unit,
    errorMessage: String?
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        SectionLabel("Eventos del mes")
        Text(
            "Maps SDK no esta configurado. Toca cualquier ubicacion para " +
            "abrirla en la app de mapas del sistema.",
            color = Sand,
            fontSize = 12.sp
        )
        Spacer(Modifier.padding(vertical = 8.dp))

        for (b in bodas) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDetail(b.id) }
                    .padding(vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Indicador circular con el color del estado
                    val color = colorParaEstado(b.estado)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(color, CircleShape)
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // Header con bolita pequena + label del estado
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(Modifier.size(5.dp))
                            Text(
                                labelParaEstado(b.estado),
                                color = Brown,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            b.nombreLocal,
                            color = Brown,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${b.pareja}  -  ${b.hora}",
                            color = Brown, fontSize = 12.sp
                        )
                        Text(b.direccionLocal, color = Sand, fontSize = 11.sp)
                        Text(
                            formatMovilidad(b.movilidad),
                            color = Gold, fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(GoldSoft, RoundedCornerShape(6.dp))
                            .clickable {
                                openInMaps(
                                    context,
                                    b.lat, b.lng, b.nombreLocal
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Abrir", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────
// Las funciones isMapsApiKeyConfigured(), buildGeoUri() y openInMaps()
// viven en `ui/util/MapsUtil.kt` para que las pueda usar cualquier
// pantalla sin crear dependencia transversal.
