package com.pacemdeus.bodas.data.local

import android.content.Context
import android.content.SharedPreferences
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.network.Dtos
import org.json.JSONObject

// Cache local de la boda activa del couple para soporte offline (HU-06).
//
// Por que en SharedPreferences y no en SQLite:
//   El SetlistDatabase ya cachea el detalle del setlist (cantos + momentos).
//   La Wedding es UN solo objeto por couple (la activa). No tiene sentido
//   meterla en SQLite con su propia tabla para una sola fila. SharedPrefs
//   guarda el JSON crudo del backend y al recuperarlo se reusa el mismo
//   parser Dtos.parseBoda que se usa con la respuesta de la red, asi no
//   hay riesgo de drift entre el parseo online y el offline.
//
// Persistimos el JSON crudo (no el objeto serializado) porque:
//   1. Reusa el parser existente sin escribir un serializer inverso.
//   2. Si la clase Wedding cambia, el JSON guardado sigue siendo legible.
//   3. Es lo que ya viaja por la red, asi que es el formato natural.
//
// Lifecycle:
//   - Se llena automaticamente desde ApiClient.listBodas cuando hay red.
//   - SetlistScreen y CoupleHomeScreen lo consultan como fallback offline.
//   - Se borra al cerrar sesion (junto con el resto del SessionManager).

class OfflineWeddingCache private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Guarda el JSON crudo de la boda activa del couple.
     * Acepta null para limpiar (cuando la boda fue eliminada o el usuario
     * cerro sesion).
     */
    fun saveActiveWedding(weddingJson: String?) {
        prefs.edit().apply {
            if (weddingJson == null) {
                remove(KEY_WEDDING_JSON)
                remove(KEY_LAST_CACHED_AT)
            } else {
                putString(KEY_WEDDING_JSON, weddingJson)
                putLong(KEY_LAST_CACHED_AT, System.currentTimeMillis())
            }
            apply()
        }
    }

    /**
     * Devuelve la boda cacheada o null si no hay nada o el JSON quedo
     * corrupto. La pantalla cliente debe llamar a esto SOLO cuando la
     * peticion online fallo, no como reemplazo de la red.
     */
    fun loadActiveWedding(): Wedding? {
        val raw = prefs.getString(KEY_WEDDING_JSON, null) ?: return null
        return try {
            Dtos.parseBoda(JSONObject(raw))
        } catch (e: Exception) {
            // JSON corrupto: limpiamos y devolvemos null para no romper
            // la pantalla. La siguiente sincronizacion online lo regenera.
            prefs.edit().remove(KEY_WEDDING_JSON).apply()
            null
        }
    }

    /** Timestamp epoch del ultimo cacheo, para mostrar "actualizado hace X". */
    fun lastCachedAt(): Long? {
        val ts = prefs.getLong(KEY_LAST_CACHED_AT, -1)
        return if (ts > 0) ts else null
    }

    /** Limpia el cache. Llamar al cerrar sesion. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "pacem_deus_offline_cache"
        private const val KEY_WEDDING_JSON = "active_wedding_json"
        private const val KEY_LAST_CACHED_AT = "active_wedding_cached_at"

        @Volatile private var INSTANCE: OfflineWeddingCache? = null

        fun get(context: Context): OfflineWeddingCache =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineWeddingCache(context).also { INSTANCE = it }
            }
    }
}
