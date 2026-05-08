package com.pacemdeus.bodas.data

// Modelos de dominio de la app. Son data classes puras de Kotlin: no
// dependen de Retrofit, Room ni ninguna libreria externa, ya que en
// esta etapa del curso aun no se han enseñado esas integraciones.
// La capa de datos opera completamente en memoria.

/** Roles disponibles en el sistema. */
enum class UserRole { COUPLE, ADMIN, WEDDING_PLANNER }

/**
 * Estados por los que pasa una boda a lo largo de su ciclo de vida.
 * El couple inicia en DRAFT, envia al coro (SUBMITTED), el admin aprueba
 * (APPROVED) y posteriormente se firma el contrato (CONTRACTED).
 */
enum class WeddingStatus {
    DRAFT, SUBMITTED, APPROVED, CONTRACTED, CANCELLATION_REQUESTED, COMPLETED;

    /** Etiqueta legible en español para mostrar en pantalla. */
    fun displayName(): String = when (this) {
        DRAFT -> "Borrador"
        SUBMITTED -> "Enviado al coro"
        APPROVED -> "Aprobado"
        CONTRACTED -> "Contratado"
        CANCELLATION_REQUESTED -> "Cancelacion solicitada"
        COMPLETED -> "Completado"
    }
}

/** Cuenta de usuario. La relacion 1:1 con su perfil se hace por el id. */
data class User(
    val id: String,
    val email: String,
    val password: String,
    val role: UserRole
)

/** Perfil de la pareja de novios asociado a un User con role=COUPLE. */
data class CoupleProfile(
    val id: String,
    val userId: String,
    val groomName: String,
    val brideName: String,
    val groomDni: String,
    val brideDni: String,
    val phone: String
) {
    /** Nombre formateado para mostrar como "Carlos & Ana Lucia". */
    fun displayName(): String = "$groomName & $brideName"
}

/** Perfil del wedding planner asociado a un User con role=WEDDING_PLANNER. */
data class PlannerProfile(
    val id: String,
    val userId: String,
    val name: String,
    val company: String?,
    val phone: String
)

/**
 * Boda: entidad central. Referencia a una pareja y opcionalmente a un planner.
 * El estado controla que campos son editables en cada momento.
 */
data class Wedding(
    val id: String,
    val coupleId: String,
    val plannerId: String?,
    val weddingDate: String,        // formato "YYYY-MM-DD"
    val weddingTime: String,        // formato "HH:mm"
    val venueName: String,
    val venueAddress: String,
    val venueLat: Double?,
    val venueLng: Double?,
    val venuePhotoTaken: Boolean,   // simulado: solo guardamos si se "tomo" la foto
    val status: WeddingStatus,
    val basePrice: Double,
    val instrumentsPrice: Double,
    val notes: String?
) {
    val totalPrice: Double get() = basePrice + instrumentsPrice
}

/** Instrumento o voz contratable, con su precio individual. */
data class Instrument(
    val id: String,
    val slug: String,
    val name: String,
    val priceLima: Double,
    val sortOrder: Int
)

/**
 * Momento liturgico de la ceremonia (Entrada, Comunion, Salida, etc.).
 * maxSongs controla cuantos cantos admite ese momento (la mayoria 1,
 * Fotografias hasta 4, Entrada y Comunion hasta 2).
 */
data class LiturgicalMoment(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val displayOrder: Int,
    val maxSongs: Int
)

/** Cancion del catalogo, con los momentos en los que se puede usar. */
data class Song(
    val id: String,
    val title: String,
    val author: String,
    val language: String,           // "ES", "LA", "EN", "INST"
    val allowedMomentSlugs: Set<String>
)

/** Item del setlist: una cancion asignada a un momento de una boda. */
data class SetlistItem(
    val id: String,
    val weddingId: String,
    val momentId: String,
    val songId: String,
    val displayOrder: Int
)

/**
 * Sesion del usuario actualmente autenticado. Contiene los datos derivados
 * que se necesitan en pantalla (nombre a mostrar, id de su boda activa, etc.).
 */
data class UserSession(
    val user: User,
    val coupleProfile: CoupleProfile?,
    val plannerProfile: PlannerProfile?,
    val weddingId: String?
) {
    /** Nombre a mostrar en el saludo segun el rol. */
    fun displayName(): String = when {
        coupleProfile != null -> coupleProfile.displayName()
        plannerProfile != null -> plannerProfile.name
        else -> user.email
    }
}
