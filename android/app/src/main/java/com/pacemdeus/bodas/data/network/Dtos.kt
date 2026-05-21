package com.pacemdeus.bodas.data.network

import com.pacemdeus.bodas.data.CoupleProfile
import com.pacemdeus.bodas.data.Instrument
import com.pacemdeus.bodas.data.LiturgicalMoment
import com.pacemdeus.bodas.data.PlannerProfile
import com.pacemdeus.bodas.data.PlannerSummary
import com.pacemdeus.bodas.data.PriceQuote
import com.pacemdeus.bodas.data.QuoteInstrument
import com.pacemdeus.bodas.data.SetlistItem
import com.pacemdeus.bodas.data.Song
import com.pacemdeus.bodas.data.User
import com.pacemdeus.bodas.data.UserRole
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingPhoto
import com.pacemdeus.bodas.data.WeddingStatus
import org.json.JSONArray
import org.json.JSONObject

// DTOs (Data Transfer Objects). Mapean los JSON que devuelve el backend
// a objetos de dominio que la UI puede consumir.
//
// CONVENCION DEL BACKEND:
//   - Request body: camelCase (fechaBoda, nombreLocal, idMomento, etc.)
//   - Response body: snake_case (id_boda, fecha_boda, nombre_local, etc.)
//
// Este mismatch entre request y response es intencional del backend
// (request siguiendo convencion frontend JS, response siguiendo
// convencion SQL Server). Aqui en Dtos centralizamos la traduccion.

object Dtos {

    // ─── AUTH ────────────────────────────────────────────────────────

    /** Respuesta de POST /auth/login y POST /auth/registrar. */
    data class AuthResponse(
        val token: String,
        val usuario: User,
        val coupleProfile: CoupleProfile?,
        val plannerProfile: PlannerProfile?,
        val weddingId: String?
    ) {
        fun toUserSession() = UserSession(
            user = usuario,
            coupleProfile = coupleProfile,
            plannerProfile = plannerProfile,
            weddingId = weddingId
        )
    }

    /**
     * Parsea la respuesta de auth.
     *
     * Estructura del backend:
     *   { "token": "...",
     *     "usuario": { "id_usuario": INT, "email": "...", "rol": "..." },
     *     "perfil":  { ... }  // contenido depende del rol
     *   }
     */
    fun parseAuthResponse(json: JSONObject): AuthResponse {
        val token = json.getString("token")
        val userJson = json.getJSONObject("usuario")

        val userId = userJson.getInt("id_usuario").toString()

        val user = User(
            id = userId,
            email = userJson.getString("email"),
            password = "",
            role = parseRole(userJson.getString("rol"))
        )

        val perfilJson = json.optJSONObject("perfil")
        var couple: CoupleProfile? = null
        var planner: PlannerProfile? = null

        if (perfilJson != null) {
            when (user.role) {
                UserRole.COUPLE -> {
                    couple = CoupleProfile(
                        id = perfilJson.optInt("id_novios", -1)
                            .takeIf { it >= 0 }?.toString() ?: "",
                        userId = userId,
                        groomName = perfilJson.optString("nombre_novio", ""),
                        brideName = perfilJson.optString("nombre_novia", ""),
                        groomDni = perfilJson.optString("documento_novio", ""),
                        brideDni = perfilJson.optString("documento_novia", ""),
                        phone = perfilJson.optString("telefono", "")
                    )
                }
                UserRole.WEDDING_PLANNER -> {
                    planner = PlannerProfile(
                        id = perfilJson.optInt("id_planner", -1)
                            .takeIf { it >= 0 }?.toString() ?: "",
                        userId = userId,
                        name = perfilJson.optString("nombre", ""),
                        company = perfilJson.safeOptString("empresa"),
                        phone = perfilJson.optString("telefono", "")
                    )
                }
                else -> { /* ADMIN no tiene perfil */ }
            }
        }

        return AuthResponse(token, user, couple, planner, weddingId = null)
    }

    // ─── FOTOS DEL LOCAL ─────────────────────────────────────────────

    /**
     * Backend: GET /bodas/{id}/fotos
     * Response: { "id_boda": N, "total": M, "fotos": [
     *   { "id_foto": X, "url": "https://...", "orden": K, "fecha_subida": "..." }
     * ] }
     */
    fun parseFotos(json: JSONObject): List<WeddingPhoto> {
        val arr = json.optJSONArray("fotos") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WeddingPhoto(
                id = o.getInt("id_foto").toString(),
                url = o.getString("url"),
                order = o.optInt("orden", 0),
                uploadedAt = o.safeOptString("fecha_subida"),
                caption = o.safeOptString("caption"),
                authorName = o.safeOptString("autor_nombre"),
                authorRole = o.safeOptString("autor_rol"),
                authorUserId = if (o.isNull("creado_por_id_usuario")) null
                               else o.optInt("creado_por_id_usuario").toString()
            )
        }
    }

    // ─── CATALOGO ────────────────────────────────────────────────────

    /**
     * Backend: GET /instrumentos
     * Response (modelo v2): { "instrumentos": [
     *   { id_instrumento, slug, nombre, icono, es_voz, canta_ingles, orden,
     *     incluido_en_paquete_base }
     * ] }
     *
     * `incluido_en_paquete_base` viene del modelo de pricing v2: true para
     * piano y voz_femenina, que estan incluidos en el paquete base S/. 650.
     * El backend ya no devuelve precio_lima / precio_fuera por instrumento
     * (el precio es uniforme y viene del config global, escalado por factor
     * de distancia en la cotizacion).
     */
    fun parseInstrumentos(json: JSONObject): List<Instrument> {
        val arr = json.getJSONArray("instrumentos")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Instrument(
                id = o.getString("slug"),
                name = o.getString("nombre"),
                sortOrder = o.optInt("orden", 0),
                includedInBasePackage = o.optBoolean("incluido_en_paquete_base", false)
            )
        }
    }

    /**
     * Backend: GET /momentos?fecha=YYYY-MM-DD (opcional)
     * Response: { "temporada": {...} | null,
     *             "momentos": [ { id_momento, slug, nombre, descripcion,
     *                             orden, max_canciones, habilitado, razon_deshabilitado, ... } ] }
     *
     * Cuando viene la fecha, el backend aplica las restricciones del tiempo
     * liturgico vigente: Gloria/Aleluya en Cuaresma vienen habilitado=false
     * con razon_deshabilitado="No se canta en Tiempo de Cuaresma".
     */
    fun parseMomentos(json: JSONObject): List<LiturgicalMoment> {
        val arr = json.getJSONArray("momentos")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LiturgicalMoment(
                id = o.getInt("id_momento").toString(),
                slug = o.optString("slug", ""),
                name = o.getString("nombre"),
                description = o.optString("descripcion", ""),
                displayOrder = o.optInt("orden", 0),
                maxSongs = o.optInt("max_canciones", 1),
                enabled = o.optBoolean("habilitado", true),
                disabledReason = o.safeOptString("razon_deshabilitado")
            )
        }
    }

    /**
     * Backend: GET /canciones?id_momento=N
     * Response: { "canciones": [ { id_cancion, titulo, autor, idioma, ... } ], "total": N }
     */
    fun parseCanciones(json: JSONObject): List<Song> {
        val arr = json.getJSONArray("canciones")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Song(
                id = o.getInt("id_cancion").toString(),
                title = o.getString("titulo"),
                author = o.safeOptString("autor") ?: "",
                language = o.safeOptString("idioma") ?: "ES"
            )
        }
    }

    // ─── BODAS ───────────────────────────────────────────────────────

    /**
     * Backend: GET /bodas
     * Response: { "bodas": [...], "total": N }
     */
    fun parseBodas(json: JSONObject): List<Wedding> {
        val arr = json.getJSONArray("bodas")
        return (0 until arr.length()).map { parseBoda(arr.getJSONObject(it)) }
    }

    /**
     * Parsea una boda. El backend devuelve los campos en snake_case:
     *   id_boda, id_novios, id_planner, fecha_boda, hora_boda,
     *   nombre_local, direccion_local, latitud, longitud, foto_local_url,
     *   estado, precio_base, precio_instrumentos, precio_movilidad, fuera_de_lima
     *
     * Publico para que el cache offline (OfflineWeddingCache) pueda
     * reusar exactamente el mismo parser que usa el codigo online.
     */
    fun parseBoda(o: JSONObject): Wedding = Wedding(
        id = o.getInt("id_boda").toString(),
        coupleId = o.optInt("id_novios", -1)
            .takeIf { it >= 0 }?.toString() ?: "",
        plannerId = o.optInt("id_planner", -1)
            .takeIf { it >= 0 }?.toString(),
        weddingDate = o.optString("fecha_boda", ""),
        weddingTime = o.optString("hora_boda", ""),
        venueName = o.optString("nombre_local", ""),
        venueAddress = o.optString("direccion_local", ""),
        venueLat = o.optDouble("latitud").takeIf { !it.isNaN() },
        venueLng = o.optDouble("longitud").takeIf { !it.isNaN() },
        venuePhotoUrl = o.safeOptString("foto_local_url"),
        status = parseStatus(o.optString("estado", "DRAFT")),
        basePrice = o.optDouble("precio_base", 0.0),
        instrumentsPrice = o.optDouble("precio_instrumentos", 0.0),
        travelPrice = o.optDouble("precio_movilidad", 0.0),
        outsideOfLima = o.optBoolean("fuera_de_lima", false),
        notes = o.safeOptString("notas"),
        coupleGroomName = o.safeOptString("nombre_novio"),
        coupleBrideName = o.safeOptString("nombre_novia"),
        plannerName = o.safeOptString("nombre_planner")
    )

    /**
     * Backend: GET /bodas/{id}
     * Response envuelve la boda: { "boda": {...}, "instrumentos": [...],
     *                              "setlist": [...], "contrato": {...},
     *                              "pagos": [...] }
     */
    fun parseBodaDetail(json: JSONObject): Wedding {
        val bodaJson = json.optJSONObject("boda") ?: json
        return parseBoda(bodaJson)
    }

    /** Parsea la lista de planners desde el endpoint /admin/planners. */
    fun parsePlanners(json: JSONObject): List<PlannerSummary> {
        val arr = json.optJSONArray("planners") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlannerSummary(
                id = o.getInt("id_planner").toString(),
                name = o.optString("nombre", ""),
                company = o.safeOptString("empresa"),
                phone = o.safeOptString("telefono")
            )
        }
    }

    /**
     * Backend: GET /bodas/{id}/setlist
     * Response: { "items": [...], "total": N }
     */
    fun parseSetlist(json: JSONObject, weddingId: String): List<SetlistItem> {
        val arr = json.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SetlistItem(
                id = o.getInt("id_setlist").toString(),
                weddingId = weddingId,
                momentId = o.getInt("id_momento").toString(),
                songId = o.getInt("id_cancion").toString(),
                displayOrder = o.optInt("orden", 0),
                // v05: titulo y autor vienen via JOIN con cancion en el SP
                title = o.safeOptString("titulo"),
                author = o.safeOptString("autor")
            )
        }
    }

    /**
     * Backend: POST /bodas/cotizar (v3, sin tocar BD).
     * Response:
     *   {
     *     "precio_base": 853.12,
     *     "precio_instrumentos": 196.88,
     *     "instrumentos_detalle": [ { slug, nombre, precio, incluido_en_base }, ... ],
     *     "precio_movilidad": 236.25,
     *     "precio_total": 1286.25,
     *     "fuera_de_lima": false,
     *     "distancia_km": 42.0,
     *     "duracion_minutos": 81,
     *     "duracion_con_trafico": 89,
     *     "factor_distancia": 1.3125
     *   }
     */
    fun parseCotizacion(json: JSONObject): PriceQuote {
        val detalleArr = json.optJSONArray("instrumentos_detalle") ?: JSONArray()
        val detalle = (0 until detalleArr.length()).map { i ->
            val o = detalleArr.getJSONObject(i)
            QuoteInstrument(
                slug = o.getString("slug"),
                name = o.getString("nombre"),
                price = o.optDouble("precio", 0.0),
                includedInBase = o.optBoolean("incluido_en_base", false)
            )
        }
        return PriceQuote(
            basePrice = json.optDouble("precio_base", 0.0),
            instrumentsPrice = json.optDouble("precio_instrumentos", 0.0),
            instrumentsDetail = detalle,
            travelPrice = json.optDouble("precio_movilidad", 0.0),
            totalPrice = json.optDouble("precio_total", 0.0),
            outsideOfLima = json.optBoolean("fuera_de_lima", false),
            distanceKm = json.optDouble("distancia_km", 0.0),
            durationMinutes = json.optInt("duracion_minutos", 0),
            durationTrafficMinutes = json.optInt("duracion_con_trafico", 0),
            distanceFactor = json.optDouble("factor_distancia", 1.0),
            isXl = json.optBoolean("grupo_xl", false),
            passengers = json.optInt("pasajeros", 0),
            mobilityDistance = json.optDouble("movilidad_distancia", 0.0),
            mobilityTraffic = json.optDouble("movilidad_trafico", 0.0)
        )
    }

    // ─── HELPERS ─────────────────────────────────────────────────────

    private fun parseRole(s: String): UserRole = when (s.uppercase()) {
        "ADMIN" -> UserRole.ADMIN
        "WEDDING_PLANNER", "PLANNER" -> UserRole.WEDDING_PLANNER
        else -> UserRole.COUPLE
    }

    private fun parseStatus(s: String): WeddingStatus = try {
        WeddingStatus.valueOf(s.uppercase())
    } catch (e: Exception) {
        WeddingStatus.DRAFT
    }

    // ─── REQUEST BUILDERS ────────────────────────────────────────────
    // Backend usa camelCase en los request bodies.

    fun buildLoginBody(email: String, password: String): JSONObject = JSONObject().apply {
        put("email", email)
        put("password", password)
    }

    /** POST /auth/registrar (COUPLE). */
    fun buildRegisterCoupleBody(
        email: String, password: String,
        groomName: String, brideName: String,
        groomDni: String, brideDni: String,
        phone: String
    ): JSONObject = JSONObject().apply {
        put("rol", "COUPLE")
        put("email", email)
        put("password", password)
        put("nombreNovio", groomName)
        put("nombreNovia", brideName)
        put("tipoDocNovio", "DNI")
        put("tipoDocNovia", "DNI")
        put("documentoNovio", groomDni)
        put("documentoNovia", brideDni)
        put("telefono", phone)
        put("comoSeEntero", "OTRO")
    }

    fun buildRegisterPlannerBody(
        email: String, password: String,
        name: String, company: String?, phone: String
    ): JSONObject = JSONObject().apply {
        put("rol", "WEDDING_PLANNER")
        put("email", email)
        put("password", password)
        put("nombre", name)
        if (company != null) put("empresa", company)
        put("telefono", phone)
    }

    /** POST /bodas (crear boda). */
    fun buildCreateBodaBody(
        weddingDate: String, weddingTime: String,
        venueName: String, venueAddress: String,
        venueLat: Double?, venueLng: Double?
    ): JSONObject = JSONObject().apply {
        put("fechaBoda", weddingDate)
        put("horaBoda", weddingTime)
        put("nombreLocal", venueName)
        put("direccionLocal", venueAddress)
        if (venueLat != null) put("latitud", venueLat)
        if (venueLng != null) put("longitud", venueLng)
    }

    /**
     * PUT /bodas/{id} (editar boda en estado DRAFT).
     * Misma forma que create, pero el backend exige latitud y longitud
     * obligatorios (no se acepta editar sin ubicacion).
     */
    fun buildUpdateBodaBody(
        weddingDate: String, weddingTime: String,
        venueName: String, venueAddress: String,
        venueLat: Double, venueLng: Double
    ): JSONObject = JSONObject().apply {
        put("fechaBoda", weddingDate)
        put("horaBoda", weddingTime)
        put("nombreLocal", venueName)
        put("direccionLocal", venueAddress)
        put("latitud", venueLat)
        put("longitud", venueLng)
    }

    /** POST /bodas/{id}/setlist. */
    fun buildSetlistAddBody(momentId: String, songId: String): JSONObject = JSONObject().apply {
        put("idMomento", momentId.toInt())
        put("idCancion", songId.toInt())
    }

    /** PUT /bodas/{id}/instrumentos. */
    fun buildInstrumentsBody(instrumentSlugs: List<String>): JSONObject = JSONObject().apply {
        put("instrumentos", JSONArray(instrumentSlugs))
    }

    /** PUT /admin/bodas/{id}/planner. */
    fun buildAssignPlannerBody(idPlanner: String?): JSONObject = JSONObject().apply {
        if (idPlanner != null) put("idPlanner", idPlanner.toInt())
        else put("idPlanner", JSONObject.NULL)
    }

    /** POST /admin/bodas/{id}/aprobar. */
    fun buildApproveBodaBody(aprobada: Boolean, notas: String?): JSONObject = JSONObject().apply {
        put("accion", if (aprobada) "aprobar" else "rechazar")
        if (notas != null) put("notas", notas)
    }

    /** POST /bodas/{id}/cancelar. */
    fun buildCancelBodaBody(motivo: String?): JSONObject = JSONObject().apply {
        if (motivo != null) put("motivo", motivo)
    }

    /**
     * POST /bodas/cotizar. Body con coordenadas + fecha/hora (para
     * Distance Matrix con trafico predictivo) + lista de slugs de
     * instrumentos. Si la lista llega vacia, el backend cotiza solo
     * base + movilidad.
     */
    fun buildCotizarBody(
        venueLat: Double, venueLng: Double,
        weddingDate: String, weddingTime: String,
        instrumentSlugs: List<String>
    ): JSONObject = JSONObject().apply {
        put("latitud", venueLat)
        put("longitud", venueLng)
        put("fechaBoda", weddingDate)
        put("horaBoda", weddingTime)
        put("instrumentos", JSONArray(instrumentSlugs))
    }
}
