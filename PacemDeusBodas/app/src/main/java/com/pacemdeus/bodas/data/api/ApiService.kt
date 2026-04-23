package com.pacemdeus.bodas.data.api

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Definición de Endpoints REST
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Interface Retrofit con todos los endpoints que consume la app.
// Los métodos son suspend (coroutines) y retornan Response<T>.
// ═══════════════════════════════════════════════════════════════

import com.pacemdeus.bodas.data.api.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ─── Autenticación ─────────────────────────────────────

    /** Iniciar sesión con email y contraseña. Retorna JWT. */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    /** Registrar novio/a. Crea usuario + perfil de pareja. */
    @POST("auth/register")
    suspend fun registerCouple(@Body request: RegisterCoupleRequest): Response<AuthResponse>

    /** Registrar wedding planner. Crea usuario + perfil. */
    @POST("auth/register")
    suspend fun registerPlanner(@Body request: RegisterPlannerRequest): Response<AuthResponse>

    /** Obtener perfil del usuario autenticado. */
    @GET("auth/me")
    suspend fun getProfile(): Response<UserProfile>

    // ─── Eventos (bodas) ───────────────────────────────────

    /** Listar todos los eventos. Filtrado por rol en backend. */
    @GET("weddings")
    suspend fun getWeddings(): Response<List<Wedding>>

    /** Crear evento (solo couple, llega en estado DRAFT). */
    @POST("weddings")
    suspend fun createWedding(@Body request: WeddingUpsertRequest): Response<CreateWeddingResponse>

    /** Obtener detalle de un evento específico. */
    @GET("weddings/{id}")
    suspend fun getWedding(@Path("id") id: String): Response<Wedding>

    /** Editar fecha, hora o lugar del evento. Couple solo en DRAFT. */
    @PATCH("weddings/{id}")
    suspend fun updateWedding(
        @Path("id") id: String,
        @Body request: WeddingUpsertRequest
    ): Response<MessageResponse>

    /** Enviar ensamble al coro para aprobación (DRAFT → SUBMITTED). */
    @POST("weddings/{id}/submit")
    suspend fun submitWedding(@Path("id") id: String): Response<MessageResponse>

    /** Solicitar cancelación del evento. */
    @POST("weddings/{id}/cancel")
    suspend fun cancelWedding(
        @Path("id") id: String,
        @Body request: CancelRequest
    ): Response<MessageResponse>

    /** Aprobar o devolver un evento. Solo ADMIN. */
    @POST("weddings/{id}/approve")
    suspend fun approveWedding(
        @Path("id") id: String,
        @Body request: ApproveRequest
    ): Response<MessageResponse>

    /** Subir foto del local en base64. Solo ADMIN. */
    @POST("weddings/{id}/photo")
    suspend fun uploadPhoto(
        @Path("id") id: String,
        @Body request: PhotoUploadRequest
    ): Response<MessageResponse>

    /** Asignar wedding planner a un evento. Solo ADMIN. */
    @PUT("weddings/{id}/planner")
    suspend fun assignPlanner(
        @Path("id") id: String,
        @Body request: AssignPlannerRequest
    ): Response<MessageResponse>

    /** Reemplazar la lista de instrumentos contratados. Recalcula precios. */
    @POST("weddings/{id}/instruments")
    suspend fun setInstruments(
        @Path("id") id: String,
        @Body request: InstrumentsRequest
    ): Response<MessageResponse>

    /** Datos del contrato para mostrar en pantalla. */
    @GET("weddings/{id}/contract")
    suspend fun getContract(@Path("id") id: String): Response<ContractData>

    /** Listar wedding planners registrados. Solo ADMIN. */
    @GET("wedding-planners")
    suspend fun getWeddingPlanners(): Response<List<WeddingPlannerItem>>

    // ─── Catálogo musical ──────────────────────────────────

    /** Obtener los momentos litúrgicos de la ceremonia. */
    @GET("moments")
    suspend fun getMoments(): Response<List<LiturgicalMoment>>

    /** Obtener canciones, opcionalmente filtradas por momento. */
    @GET("songs")
    suspend fun getSongs(@Query("momentId") momentId: String? = null): Response<List<Song>>

    /** Obtener instrumentos musicales disponibles. */
    @GET("instruments")
    suspend fun getInstruments(): Response<List<Instrument>>

    // ─── Setlist ───────────────────────────────────────────

    /** Obtener el setlist completo de un evento. */
    @GET("weddings/{id}/setlist")
    suspend fun getSetlist(@Path("id") weddingId: String): Response<List<SetlistItem>>

    /** Agregar una canción al setlist de un evento. */
    @POST("weddings/{id}/setlist")
    suspend fun addToSetlist(
        @Path("id") weddingId: String,
        @Body request: AddToSetlistRequest
    ): Response<MessageResponse>

    /** Quitar una canción del setlist de un evento. */
    @DELETE("weddings/{id}/setlist/{itemId}")
    suspend fun removeFromSetlist(
        @Path("id") weddingId: String,
        @Path("itemId") itemId: String
    ): Response<MessageResponse>

    // ─── Wedding Planner ───────────────────────────────────

    /** Obtener eventos asignados al planner autenticado. */
    @GET("planner/weddings")
    suspend fun getPlannerWeddings(): Response<List<Wedding>>
}
