package com.pacemdeus.bodas.data.geocoding

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Encapsula el uso de Geocoder + las reglas de validacion de Lima.
// Devuelve un resultado tipado que el caller puede usar para decidir si
// muestra error, pin valido o pin fuera de Lima.

sealed class VenueGeocodeResult {
    /** Geocoding exitoso dentro de Lima Metropolitana. */
    data class Found(
        val lat: Double,
        val lng: Double,
        val label: String?
    ) : VenueGeocodeResult()

    /** Direccion no encontrada o fuera de Peru / fuera de Lima. */
    data class Rejected(val reason: String) : VenueGeocodeResult()

    /** Error tecnico (sin internet, Geocoder no disponible, etc.). */
    data class Failed(val cause: String) : VenueGeocodeResult()
}

object VenueGeocoder {

    /**
     * Geocodifica la direccion bloqueante. Llamar desde un Dispatcher.IO.
     *
     * Reglas:
     * - direccion debe tener al menos 5 caracteres
     * - debe matchear Peru
     * - debe estar dentro del radio de Lima Metropolitana
     */
    fun geocode(context: Context, rawAddress: String): VenueGeocodeResult {
        val address = rawAddress.trim()
        if (address.length < 5) {
            return VenueGeocodeResult.Failed("Direccion muy corta")
        }

        return try {
            val geocoder = Geocoder(context, Locale("es", LimaGeoConfig.COUNTRY_CODE))
            // Overload con bounding box: sesga la busqueda hacia Lima.
            // El Locale solo afecta el idioma de resultados, NO el bias
            // geografico; sin bbox el Geocoder cae a heuristicas globales
            // que tienden a devolver resultados de USA cuando la consulta
            // es ambigua (ej. solo "parroquia san jose").
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(
                address, 1,
                LimaGeoConfig.LIMA_BBOX_SW_LAT, LimaGeoConfig.LIMA_BBOX_SW_LNG,
                LimaGeoConfig.LIMA_BBOX_NE_LAT, LimaGeoConfig.LIMA_BBOX_NE_LNG
            )

            if (results.isNullOrEmpty()) {
                VenueGeocodeResult.Rejected(
                    "No se encontro la ubicacion. Intenta con una direccion mas especifica."
                )
            } else {
                val loc = results[0]
                val country = loc.countryCode ?: ""
                val distance = distanceFromBase(loc.latitude, loc.longitude)

                when {
                    country != LimaGeoConfig.COUNTRY_CODE ->
                        VenueGeocodeResult.Rejected("La ubicacion debe estar en Peru.")

                    distance > LimaGeoConfig.LIMA_RADIUS_KM ->
                        VenueGeocodeResult.Rejected(
                            "El local debe estar dentro de Lima Metropolitana " +
                                "(radio de ${LimaGeoConfig.LIMA_RADIUS_KM.toInt()} km)."
                        )

                    else -> {
                        val label = loc.locality?.takeIf { it.isNotBlank() }
                            ?: loc.subAdminArea
                            ?: loc.featureName
                        VenueGeocodeResult.Found(loc.latitude, loc.longitude, label)
                    }
                }
            }
        } catch (e: Exception) {
            VenueGeocodeResult.Failed("Error al geolocalizar: ${e.message ?: "intenta de nuevo"}")
        }
    }

    /**
     * Distancia en km desde el punto base del coro (Sagrada Familia) hasta
     * un punto arbitrario, usando la formula de Haversine.
     */
    fun distanceFromBase(lat: Double, lng: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat - LimaGeoConfig.BASE_LAT)
        val dLng = Math.toRadians(lng - LimaGeoConfig.BASE_LNG)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(LimaGeoConfig.BASE_LAT)) *
            cos(Math.toRadians(lat)) *
            sin(dLng / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /** True si el punto esta dentro del radio de Lima. */
    fun isInsideLima(lat: Double, lng: Double): Boolean =
        distanceFromBase(lat, lng) <= LimaGeoConfig.LIMA_RADIUS_KM
}
