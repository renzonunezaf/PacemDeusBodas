package com.pacemdeus.bodas.ui.screens.couple.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pacemdeus.bodas.data.geocoding.LimaGeoConfig
import com.pacemdeus.bodas.data.geocoding.VenueGeocodeResult
import com.pacemdeus.bodas.data.geocoding.VenueGeocoder
import com.pacemdeus.bodas.ui.components.goldTextFieldColors
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand
import com.pacemdeus.bodas.ui.util.isMapsApiKeyConfigured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Estado externo del picker de ubicacion. El parent (CreateEditWeddingScreen)
// mantiene este estado y lo pasa down, para que pueda reaccionar a cambios
// (recalcular cotizacion, etc.).

data class LocationPickerState(
    val venueName: String = "",
    val venueAddress: String = "",
    /**
     * Texto que el usuario escribe SOLO para encontrar la ubicacion
     * en el mapa. No se guarda en BD. Cuando el usuario edita una
     * boda existente, este campo arranca con la direccion guardada
     * (para mostrar el pin ya posicionado) pero puede cambiarse para
     * reubicar sin afectar la direccion oficial.
     */
    val searchQuery: String = "",
    val pinLat: Double? = null,
    val pinLng: Double? = null,
    val locationLabel: String? = null,
    val isGeocoding: Boolean = false,
    val errorMessage: String? = null
) {
    val hasValidPin: Boolean get() = pinLat != null && pinLng != null
}

// Debounce de la geolocalizacion: tiempo desde el ultimo cambio en el
// campo de direccion antes de disparar el Geocoder.
private const val GEOCODE_DEBOUNCE_MS = 800L

/**
 * Bloque "Lugar de la ceremonia": nombre + direccion + estado del
 * geocoding + mapa interactivo.
 *
 * Encapsula:
 *   - Los dos OutlinedTextField (nombre, direccion)
 *   - El Geocoder con debounce que se dispara cuando la direccion cambia
 *   - El feedback visual (loading, found, error)
 *   - El mapa con el pin draggable + tap-to-move
 *
 * El parent solo recibe el `state` y le pasa actualizaciones via `onChange`.
 *
 * @param state Estado actual (manejado por el parent via mutableStateOf).
 * @param enabled Si false, los inputs se desactivan (modo guardando).
 * @param onChange Callback con el nuevo estado completo.
 */
@Composable
fun VenueLocationPicker(
    state: LocationPickerState,
    enabled: Boolean,
    onChange: (LocationPickerState) -> Unit
) {
    val context = LocalContext.current
    val mapsConfigurado = remember { isMapsApiKeyConfigured(context) }

    // Permiso de ubicacion en runtime. Cuando esta concedido, la capa
    // de myLocation del Maps SDK se activa (punto azul + boton de
    // recentrar). Esto da contexto visual al usuario aunque el geocoder
    // ya esta sesgado a Lima via bounding box en VenueGeocoder.
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

    // ─── 1. Nombre del local (manual) ──────────────────────
    OutlinedTextField(
        value = state.venueName,
        onValueChange = { onChange(state.copy(venueName = it, errorMessage = null)) },
        label = { Text("Nombre del local") },
        placeholder = { Text("Ej. Parroquia Sagrada Familia") },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = goldTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))

    // ─── 2. Direccion (manual) ─────────────────────────────
    // No se geolocaliza; lo que entra aqui es lo que aparece en el
    // contrato tal cual. Si el usuario quiere autocompletado, el
    // campo de busqueda de abajo se encarga.
    OutlinedTextField(
        value = state.venueAddress,
        onValueChange = { onChange(state.copy(venueAddress = it, errorMessage = null)) },
        label = { Text("Direccion") },
        placeholder = { Text("Ej. Av. Brasil 2790, Magdalena del Mar") },
        supportingText = {
            Text(
                "La direccion que escribas aqui aparecera tal cual en el contrato.",
                color = Sand, fontSize = 11.sp
            )
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = goldTextFieldColors()
    )
    Spacer(Modifier.height(18.dp))

    // ─── 3. Ubicacion en el mapa (busqueda + pin) ──────────
    // Este bloque es SOLO para fijar las coordenadas que el coro usa
    // para calcular movilidad. La direccion oficial sigue siendo el
    // campo manual de arriba.
    Text(
        "Ubicacion exacta en el mapa",
        color = Brown,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(2.dp))
    Text(
        "Busca el lugar para colocar el pin. Solo se usa para calcular la " +
            "movilidad del coro; no aparece en el contrato.",
        color = Sand, fontSize = 11.sp
    )
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = { onChange(state.copy(searchQuery = it, errorMessage = null)) },
        label = { Text("Buscar en el mapa") },
        placeholder = { Text("Ej. Hacienda Tres Canas Pachacamac") },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = goldTextFieldColors()
    )

    // ─── Geocoding con debounce sobre searchQuery ──────────
    LaunchedEffect(state.searchQuery) {
        val q = state.searchQuery.trim()
        if (q.length < 5) {
            if (state.hasValidPin || state.errorMessage != null) {
                onChange(state.copy(
                    pinLat = null,
                    pinLng = null,
                    locationLabel = null,
                    errorMessage = null,
                    isGeocoding = false
                ))
            }
            return@LaunchedEffect
        }

        delay(GEOCODE_DEBOUNCE_MS)

        onChange(state.copy(isGeocoding = true, errorMessage = null))

        val result = withContext(Dispatchers.IO) {
            VenueGeocoder.geocode(context, q)
        }

        val newState = when (result) {
            is VenueGeocodeResult.Found -> state.copy(
                pinLat = result.lat,
                pinLng = result.lng,
                locationLabel = result.label,
                isGeocoding = false,
                errorMessage = null
            )
            is VenueGeocodeResult.Rejected -> state.copy(
                pinLat = null,
                pinLng = null,
                locationLabel = null,
                isGeocoding = false,
                errorMessage = result.reason
            )
            is VenueGeocodeResult.Failed -> state.copy(
                isGeocoding = false,
                errorMessage = result.cause
            )
        }
        onChange(newState)
    }

    // ─── Feedback visual segun el estado ───────────────────
    when {
        state.isGeocoding -> {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = Gold,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Buscando ubicacion...", color = Sand, fontSize = 12.sp)
            }
        }
        state.hasValidPin -> {
            Spacer(Modifier.height(12.dp))
            FoundLocationHeader(state.locationLabel ?: "Ubicacion encontrada")
            Spacer(Modifier.height(10.dp))
            if (mapsConfigurado) {
                InteractiveMap(
                    initialLat = state.pinLat!!,
                    initialLng = state.pinLng!!,
                    hasLocationPermission = hasLocationPermission,
                    onPinMoved = { newLat, newLng ->
                        if (VenueGeocoder.isInsideLima(newLat, newLng)) {
                            onChange(state.copy(
                                pinLat = newLat,
                                pinLng = newLng,
                                errorMessage = null
                            ))
                        } else {
                            onChange(state.copy(
                                pinLat = null,
                                pinLng = null,
                                errorMessage = "El pin esta fuera de Lima Metropolitana."
                            ))
                        }
                    }
                )
            } else {
                FallbackCoordinatesView(state.pinLat!!, state.pinLng!!)
            }
        }
        state.errorMessage != null -> {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** Banda con icono de check + nombre del area encontrada. */
@Composable
private fun FoundLocationHeader(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                color = Brown,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Toca el mapa o arrastra el pin para ajustarlo",
                color = Sand,
                fontSize = 11.sp
            )
        }
    }
}

/** Vista alternativa cuando no hay MAPS_API_KEY configurada. */
@Composable
private fun FallbackCoordinatesView(lat: Double, lng: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(GoldSoft, RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Coordenadas: %.5f , %.5f".format(lat, lng),
            color = Brown, fontSize = 12.sp
        )
    }
}

/**
 * Mapa con un marker draggable que permite al usuario ajustar la ubicacion
 * exacta del local. Tambien acepta tap-to-move para reposicionar el pin
 * con un solo toque.
 */
@Composable
private fun InteractiveMap(
    initialLat: Double,
    initialLng: Double,
    hasLocationPermission: Boolean,
    onPinMoved: (Double, Double) -> Unit
) {
    val markerState = remember(initialLat, initialLng) {
        MarkerState(position = LatLng(initialLat, initialLng))
    }

    val cameraPositionState = rememberCameraPositionState(
        key = "$initialLat,$initialLng"
    ) {
        position = CameraPosition.fromLatLngZoom(
            LatLng(initialLat, initialLng),
            LimaGeoConfig.FOUND_MAP_ZOOM
        )
    }

    LaunchedEffect(markerState.position) {
        val pos = markerState.position
        onPinMoved(pos.latitude, pos.longitude)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .border(1.dp, Gold, RoundedCornerShape(12.dp))
            .background(Cream, RoundedCornerShape(12.dp))
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                minZoomPreference = 8f,
                maxZoomPreference = 20f,
                // Activa la capa de ubicacion del SDK cuando hay permiso.
                // Da contexto visual al usuario (punto azul) y permite que
                // el Maps SDK use la ubicacion del dispositivo internamente.
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = false,
                myLocationButtonEnabled = hasLocationPermission,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false
            ),
            onMapClick = { latLng -> markerState.position = latLng }
        ) {
            Marker(
                state = markerState,
                draggable = true,
                title = "Local de la boda",
                snippet = "Manten presionado y arrastra para ajustar"
            )
        }
    }
}
