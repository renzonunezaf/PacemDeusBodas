package com.pacemdeus.bodas.data.geocoding

// Configuracion geografica del area de operacion (Lima Metropolitana) y
// del punto base del coro Pacem Deus. Los valores deben mantenerse en
// sincronia con la fila unica de la tabla configuracion_precios del backend
// (precios y radio se editan via PUT /admin/pricing).
//
// Centralizamos aqui para que CreateEditWeddingScreen y futuros mapas no
// dupliquen constantes.

object LimaGeoConfig {

    /** Latitud del punto base del coro (Parroquia Sagrada Familia, Bellavista). */
    const val BASE_LAT = -12.0540646

    /** Longitud del punto base del coro. */
    const val BASE_LNG = -77.0933343

    /** Radio en km del area de cobertura (Lima Metropolitana). */
    const val LIMA_RADIUS_KM = 50.0

    /** Zoom inicial cuando aun no hay pin elegido (vista general). */
    const val INITIAL_MAP_ZOOM = 11f

    /** Zoom cuando ya hay pin encontrado (vista detallada). */
    const val FOUND_MAP_ZOOM = 15f

    /** Codigo de pais que aceptamos al geocodificar (PE = Peru). */
    const val COUNTRY_CODE = "PE"

    // ─── Bounding box de Lima Metropolitana ─────────────────
    // Caja envolvente que cubre el radio de 50 km desde el punto base
    // del coro. Se pasa al Geocoder.getFromLocationName() para sesgar
    // las busquedas hacia Lima y evitar que devuelva resultados de USA
    // u otros paises cuando la consulta es ambigua.
    //
    // Esquina suroeste (SW): aprox 50 km al sur y oeste del punto base.
    // Esquina noreste (NE): aprox 50 km al norte y este del punto base.
    // 1 grado de latitud ≈ 111 km. 1 grado de longitud a -12° ≈ 109 km.

    /** Latitud de la esquina suroeste del bbox. */
    const val LIMA_BBOX_SW_LAT = -12.50

    /** Longitud de la esquina suroeste del bbox. */
    const val LIMA_BBOX_SW_LNG = -77.55

    /** Latitud de la esquina noreste del bbox. */
    const val LIMA_BBOX_NE_LAT = -11.60

    /** Longitud de la esquina noreste del bbox. */
    const val LIMA_BBOX_NE_LNG = -76.63
}
