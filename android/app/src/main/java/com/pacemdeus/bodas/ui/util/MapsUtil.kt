package com.pacemdeus.bodas.ui.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.net.toUri

// Utilidades compartidas para todas las pantallas que usan Google Maps.
// Centralizamos aqui:
//   - Deteccion de API key configurada (para mostrar mapa real vs fallback)
//   - Helpers de Intent geo: para abrir la app de mapas del sistema
//
// Asi no duplicamos esta logica entre MapScreen, CreateEditWeddingScreen,
// WeddingDetailScreen y PlannerDetailScreen.

private const val TAG = "MapsUtil"

/**
 * Detecta si la Google Maps API key esta configurada en el manifest.
 *
 * En tiempo de build, Gradle reemplaza el placeholder ${MAPS_API_KEY}
 * del manifest con el valor leido de `local.properties`. Si la propiedad
 * no esta presente, queda como string vacio. Aqui chequeamos:
 *   - No null ni vacio
 *   - No empieza con "${" (placeholder sin resolver)
 *   - No es el string literal de placeholder usado en otros proyectos
 */
fun isMapsApiKeyConfigured(context: Context): Boolean {
    return try {
        val app = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val key = app.metaData?.getString("com.google.android.geo.API_KEY")
        !key.isNullOrBlank() && !key.startsWith("\${") && key != "YOUR_MAPS_API_KEY"
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer Maps API key del manifest", e)
        false
    }
}

/** Construye la URI geo: que abre la app de mapas del sistema. */
fun buildGeoUri(lat: Double, lng: Double, label: String): String {
    val safe = label.replace(" ", "+")
    return "geo:$lat,$lng?q=$lat,$lng($safe)"
}

/**
 * Lanza Intent ACTION_VIEW con un URI geo: para abrir la app de mapas
 * del telefono (Google Maps si esta instalado, otra alternativa si no).
 */
fun openInMaps(context: Context, lat: Double, lng: Double, label: String) {
    val intent = Intent(Intent.ACTION_VIEW, buildGeoUri(lat, lng, label).toUri())
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
