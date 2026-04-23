package com.pacemdeus.bodas.data.api.models

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Modelos de Datos (Request/Response)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Data classes para serialización JSON via Gson/Retrofit.
// Tanto requests como responses usan camelCase de forma consistente.
// El backend convierte snake_case de PostgreSQL a camelCase en la
// capa de serialización (shared/db.py → success()).
// ═══════════════════════════════════════════════════════════════

// ─── Requests ──────────────────────────────────────────────

/** Credenciales para iniciar sesión */
data class LoginRequest(val email: String, val password: String)

/** Datos de registro para una pareja de novios */
data class RegisterCoupleRequest(
    val email: String,
    val password: String,
    val groomName: String,
    val brideName: String,
    val groomDni: String,
    val brideDni: String,
    val phone: String,
    val registerAs: String = "COUPLE"
)

/** Datos de registro para un wedding planner */
data class RegisterPlannerRequest(
    val email: String,
    val password: String,
    val name: String,
    val company: String?,
    val phone: String,
    val registerAs: String = "WEDDING_PLANNER"
)

/** Datos para crear o editar el evento del couple (DRAFT) */
data class WeddingUpsertRequest(
    val weddingDate: String,        // "YYYY-MM-DD"
    val weddingTime: String,        // "HH:mm"
    val venueName: String,
    val venueAddress: String,
    val venueLat: Double? = null,
    val venueLng: Double? = null
)

/** Motivo opcional al solicitar cancelación del evento */
data class CancelRequest(val reason: String? = null)

/** Acción de aprobación: "approve" o "reject" */
data class ApproveRequest(val action: String)

/** Foto del local en base64 con su tipo MIME */
data class PhotoUploadRequest(val photoData: String, val mimeType: String)

/** ID del planner a asignar a un evento */
data class AssignPlannerRequest(val plannerId: String)

/** Lista de instrumentos contratados */
data class InstrumentsRequest(val instrumentIds: List<String>)

/** Canción y momento para agregar al setlist */
data class AddToSetlistRequest(val songId: String, val momentId: String)

// ─── Responses ─────────────────────────────────────────────

/** Respuesta de login/registro con token JWT y perfil */
data class AuthResponse(val token: String, val user: UserProfile)

/** Perfil del usuario con datos según su rol */
data class UserProfile(
    val id: String,
    val email: String,
    val role: String,
    val couple: CoupleProfile? = null,
    val weddingPlanner: PlannerProfile? = null
)

/** Perfil de la pareja de novios */
data class CoupleProfile(
    val id: String,
    val groomName: String,
    val brideName: String,
    val phone: String,
    val weddings: List<WeddingMini>? = null
)

/** Perfil del wedding planner */
data class PlannerProfile(
    val id: String,
    val name: String,
    val company: String?,
    val phone: String
)

/** Resumen mínimo de una boda (usado en el perfil de pareja) */
data class WeddingMini(val id: String, val status: String, val weddingDate: String?)

/** Evento de boda con todos sus datos */
data class Wedding(
    val id: String,
    val status: String,
    val groomName: String,
    val brideName: String,
    val weddingDate: String?,
    val weddingTime: String?,
    val venueName: String?,
    val venueAddress: String?,
    val venueLat: Double?,
    val venueLng: Double?,
    val totalPrice: Double?,
    val plannerName: String?,
    val venuePhotoUrl: String? = null
)

/** Momento litúrgico de la ceremonia (ej: Entrada, Ofertorio) */
data class LiturgicalMoment(
    val id: String,
    val slug: String,
    val name: String,
    val description: String?,
    val icon: String?,
    val displayOrder: Int,
    val maxSongs: Int
)

/** Canción del catálogo musical */
data class Song(val id: String, val title: String, val author: String?)

/** Item del setlist: canción asignada a un momento en una boda */
data class SetlistItem(
    val id: String,
    val displayOrder: Int,
    val songId: String?,
    val songTitle: String,
    val songAuthor: String?,
    val momentId: String?,
    val momentSlug: String,
    val momentName: String,
    val momentOrder: Int
)

/** Instrumento musical disponible con precio */
data class Instrument(
    val id: String,
    val slug: String,
    val name: String,
    val icon: String?,
    val priceLima: Double
)

/** Wedding planner para listado (incluye cantidad de eventos) */
data class WeddingPlannerItem(
    val id: String,
    val name: String,
    val company: String?,
    val weddingCount: Int
)

/** Respuesta genérica con mensaje */
data class MessageResponse(val message: String)

/** Respuesta del endpoint que crea una boda */
data class CreateWeddingResponse(val message: String, val weddingId: String)

/** Instrumento incluido dentro del contrato */
data class ContractInstrument(val name: String, val priceLima: Double)

/** Datos del contrato para mostrar en la pantalla del couple */
data class ContractData(
    val weddingId: String,
    val groomName: String,
    val brideName: String,
    val groomDni: String?,
    val brideDni: String?,
    val phone: String?,
    val weddingDate: String,
    val weddingTime: String,
    val venueName: String,
    val venueAddress: String,
    val basePrice: Double,
    val instrumentsPrice: Double,
    val totalPrice: Double,
    val status: String,
    val instruments: List<ContractInstrument>
)
