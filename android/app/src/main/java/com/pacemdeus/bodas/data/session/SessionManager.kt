package com.pacemdeus.bodas.data.session

import android.content.Context
import android.content.SharedPreferences
import com.pacemdeus.bodas.data.CoupleProfile
import com.pacemdeus.bodas.data.PlannerProfile
import com.pacemdeus.bodas.data.User
import com.pacemdeus.bodas.data.UserRole
import com.pacemdeus.bodas.data.UserSession

// Manejo de la sesion del usuario. Persiste el JWT y los datos basicos
// del usuario en SharedPreferences para que la app recuerde quien esta
// logueado entre cierres.
//
// SharedPreferences es el storage standard que enseña el profesor en el
// curso para datos pequenos clave-valor. Cumple el requisito de inserts/
// selects/deletes a nivel de sesion sin necesidad de SQLite (que se
// reserva para HU-06 setlist offline).

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // ─── JWT ──────────────────────────────────────────────────────

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun hasToken(): Boolean = !getToken().isNullOrBlank()

    // ─── USER SESSION ─────────────────────────────────────────────

    fun saveSession(session: UserSession, token: String) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, session.user.id)
            putString(KEY_USER_EMAIL, session.user.email)
            putString(KEY_USER_ROLE, session.user.role.name)
            putString(KEY_WEDDING_ID, session.weddingId)

            // Couple profile (si el usuario es novio/a)
            session.coupleProfile?.let { c ->
                putString(KEY_COUPLE_ID, c.id)
                putString(KEY_COUPLE_GROOM, c.groomName)
                putString(KEY_COUPLE_BRIDE, c.brideName)
                putString(KEY_COUPLE_GROOM_DNI, c.groomDni)
                putString(KEY_COUPLE_BRIDE_DNI, c.brideDni)
                putString(KEY_COUPLE_PHONE, c.phone)
            }

            // Planner profile (si el usuario es wedding planner)
            session.plannerProfile?.let { p ->
                putString(KEY_PLANNER_ID, p.id)
                putString(KEY_PLANNER_NAME, p.name)
                putString(KEY_PLANNER_COMPANY, p.company)
                putString(KEY_PLANNER_PHONE, p.phone)
            }

            apply()
        }
    }

    fun loadSession(): UserSession? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        val roleName = prefs.getString(KEY_USER_ROLE, null) ?: return null

        val role = try { UserRole.valueOf(roleName) } catch (e: Exception) { return null }

        val user = User(id = userId, email = email, password = "", role = role)

        val couple = prefs.getString(KEY_COUPLE_ID, null)?.let { coupleId ->
            CoupleProfile(
                id = coupleId,
                userId = userId,
                groomName = prefs.getString(KEY_COUPLE_GROOM, "") ?: "",
                brideName = prefs.getString(KEY_COUPLE_BRIDE, "") ?: "",
                groomDni = prefs.getString(KEY_COUPLE_GROOM_DNI, "") ?: "",
                brideDni = prefs.getString(KEY_COUPLE_BRIDE_DNI, "") ?: "",
                phone = prefs.getString(KEY_COUPLE_PHONE, "") ?: ""
            )
        }

        val planner = prefs.getString(KEY_PLANNER_ID, null)?.let { plannerId ->
            PlannerProfile(
                id = plannerId,
                userId = userId,
                name = prefs.getString(KEY_PLANNER_NAME, "") ?: "",
                company = prefs.getString(KEY_PLANNER_COMPANY, null),
                phone = prefs.getString(KEY_PLANNER_PHONE, "") ?: ""
            )
        }

        return UserSession(
            user = user,
            coupleProfile = couple,
            plannerProfile = planner,
            weddingId = prefs.getString(KEY_WEDDING_ID, null)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ─── FCM TOKEN ────────────────────────────────────────────────
    // Token de Firebase Cloud Messaging para notificaciones push.
    // Se guarda aqui para que el backend pueda asociarlo al usuario.

    fun saveFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    companion object {
        private const val PREFS_NAME = "pacem_deus_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_WEDDING_ID = "wedding_id"
        private const val KEY_COUPLE_ID = "couple_id"
        private const val KEY_COUPLE_GROOM = "couple_groom"
        private const val KEY_COUPLE_BRIDE = "couple_bride"
        private const val KEY_COUPLE_GROOM_DNI = "couple_groom_dni"
        private const val KEY_COUPLE_BRIDE_DNI = "couple_bride_dni"
        private const val KEY_COUPLE_PHONE = "couple_phone"
        private const val KEY_PLANNER_ID = "planner_id"
        private const val KEY_PLANNER_NAME = "planner_name"
        private const val KEY_PLANNER_COMPANY = "planner_company"
        private const val KEY_PLANNER_PHONE = "planner_phone"
        private const val KEY_FCM_TOKEN = "fcm_token"

        // Singleton access. Las pantallas no instancian SessionManager
        // cada vez; usan SessionManager.get(context).
        @Volatile private var INSTANCE: SessionManager? = null

        fun get(context: Context): SessionManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
