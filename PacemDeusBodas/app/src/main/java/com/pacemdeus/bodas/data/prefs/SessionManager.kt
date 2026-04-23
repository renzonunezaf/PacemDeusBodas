package com.pacemdeus.bodas.data.prefs

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Gestor de Sesión
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Gestiona la sesión del usuario usando SharedPreferences.
// Almacena el token JWT, rol, datos del perfil y el ID de la
// boda activa (para usuarios con rol COUPLE).
// ═══════════════════════════════════════════════════════════════

import android.content.Context
import android.content.SharedPreferences
import com.pacemdeus.bodas.PacemDeusApp

object SessionManager {

    // Nombre del archivo de preferencias
    private const val PREFS_NAME = "pacem_deus_session"

    // Claves de almacenamiento
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_ROLE = "role"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_WEDDING_ID = "wedding_id"

    /** Referencia lazy a SharedPreferences */
    private val prefs: SharedPreferences by lazy {
        PacemDeusApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ─── Token JWT ─────────────────────────────────────────

    /** Guarda el token JWT recibido del backend */
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /** Retorna el token JWT almacenado, o null si no hay sesión */
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** Verifica si existe una sesión activa (hay token guardado) */
    fun isLoggedIn(): Boolean = getToken() != null

    // ─── Datos del usuario ─────────────────────────────────

    /** Guarda los datos básicos del usuario tras login o registro */
    fun saveUserData(userId: String, email: String, role: String, displayName: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putString(KEY_ROLE, role)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    /** Guarda el ID de la boda activa (solo para rol COUPLE) */
    fun saveWeddingId(id: String) {
        prefs.edit().putString(KEY_WEDDING_ID, id).apply()
    }

    // Getters de datos del usuario
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getRole(): String? = prefs.getString(KEY_ROLE, null)
    fun getDisplayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)
    fun getWeddingId(): String? = prefs.getString(KEY_WEDDING_ID, null)

    // ─── Cerrar sesión ─────────────────────────────────────

    /** Limpia todos los datos de sesión del dispositivo */
    fun logout() {
        prefs.edit().clear().apply()
    }
}
