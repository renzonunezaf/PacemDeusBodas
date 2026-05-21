package com.pacemdeus.bodas.data.network

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.pacemdeus.bodas.data.Instrument
import com.pacemdeus.bodas.data.LiturgicalMoment
import com.pacemdeus.bodas.data.Notification
import com.pacemdeus.bodas.data.NotificationPollResult
import com.pacemdeus.bodas.data.PlannerSummary
import com.pacemdeus.bodas.data.PriceQuote
import com.pacemdeus.bodas.data.SetlistItem
import com.pacemdeus.bodas.data.Song
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingPhoto
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.data.local.OfflineWeddingCache
import com.pacemdeus.bodas.data.session.SessionManager
import org.json.JSONArray
import org.json.JSONObject

// Cliente unico para hablar con el backend AWS. Singleton (instanciado
// una sola vez en la app) que envuelve Volley y expone una funcion por
// cada endpoint del API.

class ApiClient private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val queue: RequestQueue = Volley.newRequestQueue(appContext)
    private val session: SessionManager = SessionManager.get(appContext)

    // ═══════════════════════════════════════════════════════════════
    // AUTH
    // ═══════════════════════════════════════════════════════════════

    fun login(email: String, password: String, callback: (ApiResult<UserSession>) -> Unit) {
        val body = Dtos.buildLoginBody(email, password)
        post("/auth/login", body, requiresAuth = false) { result ->
            handleAuthResult(result, callback)
        }
    }

    fun registerCouple(
        email: String, password: String,
        groomName: String, brideName: String,
        groomDni: String, brideDni: String,
        phone: String,
        callback: (ApiResult<UserSession>) -> Unit
    ) {
        val body = Dtos.buildRegisterCoupleBody(
            email, password, groomName, brideName, groomDni, brideDni, phone
        )
        post("/auth/registrar", body, requiresAuth = false) { result ->
            handleAuthResult(result, callback)
        }
    }

    fun registerPlanner(
        email: String, password: String,
        name: String, company: String?, phone: String,
        callback: (ApiResult<UserSession>) -> Unit
    ) {
        val body = Dtos.buildRegisterPlannerBody(email, password, name, company, phone)
        post("/auth/registrar", body, requiresAuth = false) { result ->
            handleAuthResult(result, callback)
        }
    }

    private fun handleAuthResult(
        result: ApiResult<JSONObject>,
        callback: (ApiResult<UserSession>) -> Unit
    ) {
        when (result) {
            is ApiResult.Success -> try {
                val authResponse = Dtos.parseAuthResponse(result.data)
                val userSession = authResponse.toUserSession()
                session.saveSession(userSession, authResponse.token)
                callback(ApiResult.Success(userSession))
            } catch (e: Exception) {
                callback(ApiResult.Error("Respuesta del servidor invalida: ${e.message}"))
            }
            is ApiResult.Error -> callback(result)
            else -> callback(ApiResult.Error("Estado inesperado"))
        }
    }

    fun me(callback: (ApiResult<UserSession>) -> Unit) {
        get("/auth/me") { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val auth = Dtos.parseAuthResponse(result.data)
                    callback(ApiResult.Success(auth.toUserSession()))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al leer perfil"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CATALOGO
    // ═══════════════════════════════════════════════════════════════

    fun listInstrumentos(callback: (ApiResult<List<Instrument>>) -> Unit) {
        get("/instrumentos") { wrap(it, Dtos::parseInstrumentos, callback) }
    }

    /**
     * Lista de momentos liturgicos. Cuando se pasa fecha, el backend
     * aplica las restricciones del tiempo liturgico vigente (Cuaresma
     * desactiva Gloria/Aleluya, Adviento desactiva Gloria, etc.). Si
     * no se pasa fecha, todos los momentos vienen habilitado=true.
     */
    fun listMomentos(fecha: String? = null, callback: (ApiResult<List<LiturgicalMoment>>) -> Unit) {
        val path = if (fecha != null) "/momentos?fecha=$fecha" else "/momentos"
        get(path) { wrap(it, Dtos::parseMomentos, callback) }
    }

    fun listCanciones(
        idMomento: String? = null,
        criterio: String? = null,
        idioma: String? = null,
        callback: (ApiResult<List<Song>>) -> Unit
    ) {
        val params = mutableListOf<String>()
        idMomento?.let { params.add("id_momento=$it") }
        criterio?.let { params.add("criterio=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        idioma?.let { params.add("idioma=$it") }
        val path = "/canciones" + if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        get(path) { wrap(it, Dtos::parseCanciones, callback) }
    }

    // ═══════════════════════════════════════════════════════════════
    // BODAS
    // ═══════════════════════════════════════════════════════════════

    fun listBodas(callback: (ApiResult<List<Wedding>>) -> Unit) {
        get("/bodas") { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val bodas = Dtos.parseBodas(result.data)

                    // Cacheo silencioso para soporte offline (HU-06).
                    // Guardamos el JSON crudo de la boda activa del couple
                    // (la que NO esta en estado terminal CANCELLED/COMPLETED).
                    // Asi, cuando la novia abra la app sin red, podemos
                    // hidratar la home y el setlist con esta copia local.
                    cacheActiveWedding(result.data, bodas)

                    callback(ApiResult.Success(bodas))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al procesar respuesta: ${e.message}"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    /**
     * Encuentra la boda activa del couple en el response y persiste su
     * JSON crudo en el OfflineWeddingCache. Best effort: si algo falla
     * no rompe el flujo de la respuesta, simplemente no hay cache para
     * la proxima sesion offline.
     */
    private fun cacheActiveWedding(responseJson: JSONObject, bodas: List<Wedding>) {
        try {
            val cache = OfflineWeddingCache.get(appContext)
            val active = bodas.firstOrNull {
                it.status != WeddingStatus.CANCELLED &&
                it.status != WeddingStatus.COMPLETED &&
                it.status != WeddingStatus.CANCELLATION_REQUESTED
            }
            if (active == null) {
                // No hay bodas activas: limpiamos cualquier cache viejo.
                cache.saveActiveWedding(null)
                return
            }
            // Recuperamos el JSONObject crudo del array original buscando
            // por id_boda. Asi guardamos lo que vino del backend tal cual,
            // sin perder campos que el parser no extraiga aun.
            val arr = responseJson.optJSONArray("bodas") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optInt("id_boda").toString() == active.id) {
                    cache.saveActiveWedding(o.toString())
                    break
                }
            }
        } catch (e: Exception) {
            // Cacheo es best effort, no rompe el flujo principal.
        }
    }

    fun getBoda(idBoda: String, callback: (ApiResult<Wedding>) -> Unit) {
        get("/bodas/$idBoda") { wrap(it, Dtos::parseBodaDetail, callback) }
    }

    fun createBoda(
        weddingDate: String, weddingTime: String,
        venueName: String, venueAddress: String,
        venueLat: Double?, venueLng: Double?,
        callback: (ApiResult<String>) -> Unit
    ) {
        val body = Dtos.buildCreateBodaBody(
            weddingDate, weddingTime, venueName, venueAddress, venueLat, venueLng
        )
        post("/bodas", body) { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val idBoda = result.data.getInt("id_boda").toString()
                    callback(ApiResult.Success(idBoda))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al crear el evento"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    /**
     * PUT /bodas/{id}. Solo funciona si la boda esta en estado DRAFT.
     * El backend recalcula automaticamente la cotizacion con los nuevos
     * datos antes de persistir.
     */
    fun updateBoda(
        idBoda: String,
        weddingDate: String, weddingTime: String,
        venueName: String, venueAddress: String,
        venueLat: Double, venueLng: Double,
        callback: (ApiResult<Unit>) -> Unit
    ) {
        val body = Dtos.buildUpdateBodaBody(
            weddingDate, weddingTime, venueName, venueAddress, venueLat, venueLng
        )
        put("/bodas/$idBoda", body) { wrap(it, { Unit }, callback) }
    }

    fun enviarBoda(idBoda: String, callback: (ApiResult<Unit>) -> Unit) {
        post("/bodas/$idBoda/enviar", JSONObject()) { wrap(it, { Unit }, callback) }
    }

    fun cancelarBoda(idBoda: String, motivo: String?, callback: (ApiResult<Unit>) -> Unit) {
        val body = Dtos.buildCancelBodaBody(motivo)
        post("/bodas/$idBoda/cancelar", body) { wrap(it, { Unit }, callback) }
    }

    fun updateInstrumentos(idBoda: String, instrumentSlugs: List<String>, callback: (ApiResult<Unit>) -> Unit) {
        val body = Dtos.buildInstrumentsBody(instrumentSlugs)
        put("/bodas/$idBoda/instrumentos", body) { wrap(it, { Unit }, callback) }
    }

    /**
     * POST /bodas/{id}/desenviar. Solo COUPLE. Solo desde SUBMITTED.
     * Revierte el envio al admin si el admin todavia no aprobo/rechazo.
     */
    fun desenviarBoda(idBoda: String, callback: (ApiResult<Unit>) -> Unit) {
        post("/bodas/$idBoda/desenviar", JSONObject()) { wrap(it, { Unit }, callback) }
    }

    /**
     * GET /planners. Endpoint publico (autenticado cualquier rol).
     * Lo usa la novia para listar los 3 wedding planners disponibles.
     */
    fun listPlannersPublic(callback: (ApiResult<List<PlannerSummary>>) -> Unit) {
        get("/planners") { wrap(it, Dtos::parsePlanners, callback) }
    }

    /**
     * POST /bodas/{id}/planner. Solo COUPLE. Solo desde DRAFT.
     * La novia asigna un planner a su boda (o lo cambia mientras es DRAFT).
     */
    fun couplePickPlanner(idBoda: String, idPlanner: String, callback: (ApiResult<Unit>) -> Unit) {
        val body = JSONObject().apply { put("idPlanner", idPlanner.toInt()) }
        post("/bodas/$idBoda/planner", body) { wrap(it, { Unit }, callback) }
    }

    // ─── FOTOS DEL LOCAL (multi, hasta 5) ────────────────────────────

    /** GET /bodas/{id}/fotos. */
    fun listarFotos(idBoda: String, callback: (ApiResult<List<WeddingPhoto>>) -> Unit) {
        get("/bodas/$idBoda/fotos") { wrap(it, Dtos::parseFotos, callback) }
    }

    /**
     * POST /bodas/{id}/fotos. Agrega una foto al evento con caption
     * opcional. El backend registra al usuario autenticado como autor.
     * Falla con 400 si el evento ya tiene 5 fotos.
     */
    fun agregarFoto(
        idBoda: String,
        imageBytes: ByteArray,
        mimeType: String,
        caption: String? = null,
        callback: (ApiResult<WeddingPhoto>) -> Unit
    ) {
        val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("imagenBase64", base64)
            put("tipoContenido", mimeType)
            if (!caption.isNullOrBlank()) put("caption", caption.trim())
        }
        post("/bodas/$idBoda/fotos", body) { result ->
            when (result) {
                is ApiResult.Success -> try {
                    // El backend devuelve la foto recien creada con autor
                    // resuelto, lo guardamos directo para que la UI no
                    // tenga que refrescar la lista entera.
                    val photo = WeddingPhoto(
                        id = result.data.getInt("id_foto").toString(),
                        url = result.data.getString("url"),
                        order = result.data.optInt("orden", 0),
                        uploadedAt = null,
                        caption = result.data.safeOptString("caption"),
                        authorName = result.data.safeOptString("autor_nombre"),
                        authorRole = result.data.safeOptString("autor_rol"),
                        authorUserId = null   // se resolvera en proxima carga
                    )
                    callback(ApiResult.Success(photo))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al procesar la foto subida"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    /** PUT /bodas/{id}/fotos/{id_foto}/caption. Solo lo puede editar el autor. */
    fun editarCaptionFoto(
        idBoda: String,
        idFoto: String,
        caption: String?,
        callback: (ApiResult<Unit>) -> Unit
    ) {
        val body = JSONObject().apply {
            if (caption.isNullOrBlank()) put("caption", JSONObject.NULL)
            else put("caption", caption.trim())
        }
        put("/bodas/$idBoda/fotos/$idFoto/caption", body) { wrap(it, { Unit }, callback) }
    }

    /** DELETE /bodas/{id}/fotos/{id_foto}. */
    fun eliminarFoto(idBoda: String, idFoto: String, callback: (ApiResult<Unit>) -> Unit) {
        delete("/bodas/$idBoda/fotos/$idFoto") { wrap(it, { Unit }, callback) }
    }

    /**
     * GET /disponibilidad/{anio}/{mes}. Devuelve para cada dia del mes
     * con bodas CONTRACTED: el estado (full/partial) y la lista de
     * bodas. Permite pintar el calendario con rojo/ambar.
     *
     * @param excludeWeddingId Si la novia editando, excluye su propia
     *                        boda para que no se cuente como conflicto
     *                        consigo misma.
     */
    fun getDisponibilidadMes(
        year: Int,
        month: Int,
        excludeWeddingId: String? = null,
        callback: (ApiResult<com.pacemdeus.bodas.data.MonthAvailability>) -> Unit
    ) {
        val path = if (excludeWeddingId != null) {
            "/disponibilidad/$year/$month?excluir_boda=$excludeWeddingId"
        } else {
            "/disponibilidad/$year/$month"
        }
        get(path) { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val dayStates = mutableMapOf<String, com.pacemdeus.bodas.data.DayAvailability>()
                    val dayBookings = mutableMapOf<String, List<com.pacemdeus.bodas.data.DayBooking>>()
                    val arr = result.data.optJSONArray("dias_con_bodas")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val fecha = o.getString("fecha")
                            val estado = when (o.optString("estado")) {
                                "full"     -> com.pacemdeus.bodas.data.DayAvailability.FULL
                                "partial"  -> com.pacemdeus.bodas.data.DayAvailability.PARTIAL
                                "occupied" -> com.pacemdeus.bodas.data.DayAvailability.OCCUPIED
                                else       -> com.pacemdeus.bodas.data.DayAvailability.FREE
                            }
                            dayStates[fecha] = estado

                            val bodasArr = o.optJSONArray("bodas")
                            val bookings = mutableListOf<com.pacemdeus.bodas.data.DayBooking>()
                            if (bodasArr != null) {
                                for (j in 0 until bodasArr.length()) {
                                    val b = bodasArr.getJSONObject(j)
                                    bookings.add(com.pacemdeus.bodas.data.DayBooking(
                                        weddingId = b.optInt("id_boda").toString(),
                                        time = b.optString("hora", ""),
                                        couple = b.optString("pareja", ""),
                                        status = b.optString("estado", "")
                                    ))
                                }
                            }
                            dayBookings[fecha] = bookings
                        }
                    }
                    callback(ApiResult.Success(com.pacemdeus.bodas.data.MonthAvailability(
                        year = year, month = month,
                        dayStates = dayStates,
                        dayBookings = dayBookings
                    )))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al leer disponibilidad: ${e.message}"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    /**
     * POST /bodas/validar-conflicto. Pre-valida una fecha+hora antes de
     * guardar para mostrar mensajes claros al usuario.
     */
    fun validarConflicto(
        fecha: String,
        hora: String,
        latitud: Double? = null,
        longitud: Double? = null,
        excludeWeddingId: String? = null,
        callback: (ApiResult<com.pacemdeus.bodas.data.ConflictCheck>) -> Unit
    ) {
        val body = JSONObject().apply {
            put("fecha", fecha)
            put("hora", hora)
            if (latitud != null) put("latitud", latitud)
            if (longitud != null) put("longitud", longitud)
            if (excludeWeddingId != null) {
                put("id_boda_excluir", excludeWeddingId.toInt())
            }
        }
        post("/bodas/validar-conflicto", body) { result ->
            when (result) {
                is ApiResult.Success -> try {
                    callback(ApiResult.Success(com.pacemdeus.bodas.data.ConflictCheck(
                        conflict = result.data.optBoolean("conflicto", false),
                        reason = result.data.optString("razon", ""),
                        availableHours = result.data.optString("horas_disponibles", "")
                    )))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al validar: ${e.message}"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    /**
     * Devuelve los instrumentos contratados de una boda (lista de slugs).
     * Como el backend devuelve el detalle dentro del wrap de getBoda,
     * aqui hacemos la llamada y extraemos solo los slugs.
     */
    fun getBodaInstrumentos(idBoda: String, callback: (ApiResult<List<String>>) -> Unit) {
        get("/bodas/$idBoda") { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val arr = result.data.optJSONArray("instrumentos")
                    val slugs = if (arr == null) emptyList()
                    else (0 until arr.length()).map { arr.getJSONObject(it).getString("slug") }
                    callback(ApiResult.Success(slugs))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al leer instrumentos"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    fun getPrecio(idBoda: String, callback: (ApiResult<JSONObject>) -> Unit) {
        get("/bodas/$idBoda/precio") { callback(it) }
    }

    /**
     * POST /bodas/cotizar. Cotizacion en vivo sin tocar BD: el cliente
     * la usa mientras el usuario edita la boda (ubicacion / instrumentos)
     * para mostrar el precio total en tiempo real.
     *
     * El backend acepta lista de instrumentos vacia: en ese caso solo
     * cotiza base + movilidad. Para el flujo couple lo habitual es
     * enviar al menos piano + voz_femenina (que son obligatorios).
     */
    fun cotizar(
        venueLat: Double,
        venueLng: Double,
        weddingDate: String,
        weddingTime: String,
        instrumentSlugs: List<String>,
        callback: (ApiResult<PriceQuote>) -> Unit
    ) {
        val body = Dtos.buildCotizarBody(
            venueLat, venueLng, weddingDate, weddingTime, instrumentSlugs
        )
        post("/bodas/cotizar", body) { wrap(it, Dtos::parseCotizacion, callback) }
    }

    // ─── SETLIST ─────────────────────────────────────────────────────

    fun listSetlist(idBoda: String, callback: (ApiResult<List<SetlistItem>>) -> Unit) {
        get("/bodas/$idBoda/setlist") { result ->
            when (result) {
                is ApiResult.Success -> try {
                    callback(ApiResult.Success(Dtos.parseSetlist(result.data, idBoda)))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al leer setlist"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    /**
     * POST /bodas/{id}/setlist. Devuelve el SetlistItem recien creado
     * para que la pantalla pueda agregarlo a su state local sin refrescar
     * toda la lista (evita perder el scroll position).
     */
    fun addSetlistItem(
        idBoda: String,
        idMomento: String,
        idCancion: String,
        callback: (ApiResult<SetlistItem>) -> Unit
    ) {
        val body = Dtos.buildSetlistAddBody(idMomento, idCancion)
        post("/bodas/$idBoda/setlist", body) { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val item = SetlistItem(
                        id = result.data.getInt("id_setlist").toString(),
                        weddingId = idBoda,
                        momentId = idMomento,
                        songId = idCancion,
                        displayOrder = result.data.optInt("orden", 0),
                        // v05: el SP usp_setlist_obtener devuelve titulo
                        // y autor via JOIN con cancion
                        title = result.data.safeOptString("titulo"),
                        author = result.data.safeOptString("autor")
                    )
                    callback(ApiResult.Success(item))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al leer el item agregado"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    fun removeSetlistItem(idBoda: String, idSetlist: String, callback: (ApiResult<Unit>) -> Unit) {
        delete("/bodas/$idBoda/setlist/$idSetlist") { wrap(it, { Unit }, callback) }
    }

    // ─── CONTRATO ────────────────────────────────────────────────────

    fun getContrato(idBoda: String, callback: (ApiResult<JSONObject>) -> Unit) {
        get("/bodas/$idBoda/contrato") { callback(it) }
    }

    /**
     * GET /bodas/{id}/contrato/pdf. Devuelve el PDF del contrato en
     * base64 + el filename sugerido. El cliente decodifica y guarda
     * para luego compartir via Intent.ACTION_SEND.
     */
    data class ContractPdf(val filename: String, val bytes: ByteArray)

    fun getContratoPdf(idBoda: String, callback: (ApiResult<ContractPdf>) -> Unit) {
        get("/bodas/$idBoda/contrato/pdf") { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val filename = result.data.optString("filename", "Contrato.pdf")
                    val b64 = result.data.getString("pdf_base64")
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    callback(ApiResult.Success(ContractPdf(filename, bytes)))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al decodificar el PDF"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    /**
     * GET /bodas/{id}/setlist/pdf. Devuelve el setlist como PDF en
     * base64. Mismo flujo de descarga + share que el contrato; reusa
     * la data class ContractPdf para evitar duplicar tipos.
     */
    fun getSetlistPdf(idBoda: String, callback: (ApiResult<ContractPdf>) -> Unit) {
        get("/bodas/$idBoda/setlist/pdf") { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val filename = result.data.optString("filename", "Setlist.pdf")
                    val b64 = result.data.getString("pdf_base64")
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    callback(ApiResult.Success(ContractPdf(filename, bytes)))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al decodificar el PDF"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    fun firmarContrato(idBoda: String, firma: String, callback: (ApiResult<Unit>) -> Unit) {
        val body = JSONObject().apply { put("firma", firma) }
        post("/bodas/$idBoda/contrato/firmar", body) { wrap(it, { Unit }, callback) }
    }

    // ═══════════════════════════════════════════════════════════════
    // PLANNER
    // ═══════════════════════════════════════════════════════════════

    fun listPlannerBodas(callback: (ApiResult<List<Wedding>>) -> Unit) {
        get("/planner/bodas") { wrap(it, Dtos::parseBodas, callback) }
    }

    // ═══════════════════════════════════════════════════════════════
    // ADMIN
    // ═══════════════════════════════════════════════════════════════

    fun getPricing(callback: (ApiResult<JSONObject>) -> Unit) {
        get("/admin/pricing") { callback(it) }
    }

    fun listPlanners(callback: (ApiResult<List<PlannerSummary>>) -> Unit) {
        get("/admin/planners") { wrap(it, Dtos::parsePlanners, callback) }
    }

    fun assignPlanner(idBoda: String, idPlanner: String, callback: (ApiResult<Unit>) -> Unit) {
        val body = Dtos.buildAssignPlannerBody(idPlanner)
        put("/admin/bodas/$idBoda/planner", body) { wrap(it, { Unit }, callback) }
    }

    /**
     * POST /admin/bodas/{id}/aprobar.
     * Backend espera {accion: "aprobar"|"rechazar", notas: "..."}.
     * Al aprobar: estado pasa a APPROVED.
     * Al rechazar: estado vuelve a DRAFT con las notas como observaciones.
     */
    fun aprobarBoda(idBoda: String, aprobada: Boolean, comentario: String?, callback: (ApiResult<Unit>) -> Unit) {
        val body = Dtos.buildApproveBodaBody(aprobada, comentario)
        post("/admin/bodas/$idBoda/aprobar", body) { wrap(it, { Unit }, callback) }
    }

    /**
     * POST /admin/bodas/{id}/cancelacion-aprobar
     * Solo ADMIN. Acepta la solicitud de cancelacion que la novia hizo
     * desde su pantalla. Mueve el evento a CANCELLED y libera la fecha
     * y hora. Solo aplica si el evento esta en CANCELLATION_REQUESTED.
     */
    fun aprobarCancelacion(idBoda: String, callback: (ApiResult<Unit>) -> Unit) {
        val body = JSONObject()
        post("/admin/bodas/$idBoda/cancelacion-aprobar", body) { wrap(it, { Unit }, callback) }
    }

    /**
     * POST /bodas/{id}/devolver-con-anotaciones
     * Solo ADMIN. Crea una anotacion con el texto libre del coordinador
     * y cambia el estado de la boda a RETURNED_WITH_NOTES. La pareja
     * la vera con un comparativo antes/despues y podra aceptar o
     * rechazar.
     */
    fun devolverConAnotaciones(
        idBoda: String,
        textoNota: String,
        camposModificados: List<String> = emptyList(),
        callback: (ApiResult<Unit>) -> Unit
    ) {
        val body = JSONObject().apply {
            put("texto_nota", textoNota)
            if (camposModificados.isNotEmpty()) {
                put("campos_modificados", org.json.JSONArray(camposModificados))
            }
        }
        post("/bodas/$idBoda/devolver-con-anotaciones", body) { wrap(it, { Unit }, callback) }
    }

    /**
     * GET /bodas/{id}/anotaciones/pendiente
     * Devuelve la ultima anotacion en estado PENDIENTE con snapshot
     * del estado anterior, para que la novia vea el comparativo.
     */
    fun getAnotacionPendiente(
        idBoda: String,
        callback: (ApiResult<JSONObject>) -> Unit
    ) {
        get("/bodas/$idBoda/anotaciones/pendiente") { callback(it) }
    }

    /**
     * POST /bodas/{id}/anotaciones/responder
     * Solo COUPLE. aceptar=true -> estado SUBMITTED (la boda regresa
     * al coro). aceptar=false -> revierte snapshot y estado DRAFT.
     */
    fun responderAnotacion(
        idBoda: String,
        aceptar: Boolean,
        callback: (ApiResult<Unit>) -> Unit
    ) {
        val body = JSONObject().apply { put("aceptar", aceptar) }
        post("/bodas/$idBoda/anotaciones/responder", body) { wrap(it, { Unit }, callback) }
    }

    /**
     * GET /mapa/bodas?anio=YYYY&mes=MM
     * Devuelve bodas con coordenadas para pintar markers en el mapa
     * del coordinador, filtradas por mes.
     */
    fun getMapaBodasMes(
        year: Int,
        month: Int,
        callback: (ApiResult<JSONObject>) -> Unit
    ) {
        get("/mapa/bodas?anio=$year&mes=$month") { callback(it) }
    }

    fun listPagos(idBoda: String, callback: (ApiResult<JSONObject>) -> Unit) {
        get("/admin/bodas/$idBoda/pagos") { callback(it) }
    }

    fun crearPago(idBoda: String, monto: Double, metodo: String, callback: (ApiResult<Unit>) -> Unit) {
        val body = JSONObject().apply {
            put("monto", monto)
            put("metodo", metodo)
        }
        post("/admin/bodas/$idBoda/pagos", body) { wrap(it, { Unit }, callback) }
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICACIONES (polling)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Trae las notificaciones del usuario autenticado desde `since`.
     * Si `since` es null, devuelve todas las no leidas.
     *
     * El backend devuelve `server_time` que conviene usar como cursor
     * en el siguiente poll para evitar duplicados o gaps por drift de
     * reloj entre cliente y server.
     */
    fun pollNotifications(
        since: String?,
        callback: (ApiResult<NotificationPollResult>) -> Unit
    ) {
        val sinceParam = if (since != null) "?since=${java.net.URLEncoder.encode(since, "UTF-8")}" else ""
        get("/notifications/poll$sinceParam") { result ->
            when (result) {
                is ApiResult.Success -> try {
                    val arr = result.data.optJSONArray("items") ?: JSONArray()
                    val items = (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        Notification(
                            id = o.getLong("id_notificacion"),
                            tipo = o.getString("tipo"),
                            title = o.getString("titulo"),
                            message = o.getString("mensaje"),
                            weddingId = o.safeOptString("id_boda")?.takeIf { it != "null" }
                                ?: o.optInt("id_boda", -1).takeIf { it > 0 }?.toString(),
                            createdAt = o.getString("creado_en"),
                            readAt = o.safeOptString("leido_en")
                        )
                    }
                    callback(ApiResult.Success(
                        NotificationPollResult(
                            items = items,
                            serverTime = result.data.getString("server_time")
                        )
                    ))
                } catch (e: Exception) {
                    callback(ApiResult.Error("Error al leer notificaciones: ${e.message}"))
                }
                is ApiResult.Error -> callback(result)
                else -> callback(ApiResult.Error("Estado inesperado"))
            }
        }
    }

    /**
     * Marca una notificacion como leida. Llamar despues de mostrar la
     * alerta en la bandeja del sistema para que no se vuelva a disparar
     * en el siguiente poll.
     */
    fun markNotificationRead(idNotification: Long, callback: (ApiResult<Unit>) -> Unit) {
        post("/notifications/$idNotification/leer", JSONObject()) { wrap(it, { Unit }, callback) }
    }

    /**
     * Registra el FCM token del dispositivo en el backend. Se llama:
     *   - desde PacemDeusFirebaseService.onNewToken cuando hay sesion
     *   - desde LoginScreen tras un login exitoso (registra el token
     *     que el SDK ya genero antes del login)
     *
     * Es idempotente: el endpoint hace upsert por (id_usuario, token).
     */
    fun registerFcmToken(token: String, callback: (ApiResult<Unit>) -> Unit) {
        val body = JSONObject().apply { put("fcm_token", token) }
        put("/auth/fcm-token", body) { wrap(it, { Unit }, callback) }
    }

    // ═══════════════════════════════════════════════════════════════
    // INFRAESTRUCTURA HTTP
    // ═══════════════════════════════════════════════════════════════

    private fun get(path: String, callback: (ApiResult<JSONObject>) -> Unit) {
        executeRequest(Request.Method.GET, path, null, requiresAuth = true, callback)
    }

    private fun post(path: String, body: JSONObject, requiresAuth: Boolean = true, callback: (ApiResult<JSONObject>) -> Unit) {
        executeRequest(Request.Method.POST, path, body, requiresAuth, callback)
    }

    private fun put(path: String, body: JSONObject, callback: (ApiResult<JSONObject>) -> Unit) {
        executeRequest(Request.Method.PUT, path, body, requiresAuth = true, callback)
    }

    private fun delete(path: String, callback: (ApiResult<JSONObject>) -> Unit) {
        executeRequest(Request.Method.DELETE, path, null, requiresAuth = true, callback)
    }

    private fun executeRequest(
        method: Int,
        path: String,
        body: JSONObject?,
        requiresAuth: Boolean,
        callback: (ApiResult<JSONObject>) -> Unit
    ) {
        val url = ApiConfig.BASE_URL + path

        val request = object : JsonObjectRequest(
            method,
            url,
            body,
            Response.Listener<JSONObject> { json ->
                callback(ApiResult.Success(json))
            },
            Response.ErrorListener { error ->
                callback(ApiResult.Error(extractErrorMessage(error), extractStatusCode(error)))
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = mutableMapOf<String, String>()
                headers[ApiConfig.HEADER_CONTENT_TYPE] = ApiConfig.CONTENT_TYPE_JSON
                if (requiresAuth) {
                    session.getToken()?.let { token ->
                        headers[ApiConfig.HEADER_AUTHORIZATION] = ApiConfig.AUTH_PREFIX + token
                    }
                }
                return headers
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            ApiConfig.TIMEOUT_MS,
            ApiConfig.MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private fun extractErrorMessage(error: VolleyError): String {
        val networkResponse: NetworkResponse? = error.networkResponse
        if (networkResponse?.data != null) {
            try {
                val bodyStr = String(networkResponse.data, Charsets.UTF_8)
                val json = JSONObject(bodyStr)
                if (json.has("error")) return json.getString("error")
                if (json.has("message")) return json.getString("message")
            } catch (e: Exception) {
                // No es JSON, ignorar
            }
        }
        return when (networkResponse?.statusCode) {
            401 -> "Sesion expirada, vuelve a iniciar sesion"
            403 -> "No tienes permiso para esta accion"
            404 -> "Recurso no encontrado"
            500, 502, 503 -> "Error del servidor, intenta de nuevo en unos segundos"
            else -> error.message ?: "Error de conexion"
        }
    }

    private fun extractStatusCode(error: VolleyError): Int? =
        error.networkResponse?.statusCode

    private fun <T> wrap(
        result: ApiResult<JSONObject>,
        parser: (JSONObject) -> T,
        callback: (ApiResult<T>) -> Unit
    ) {
        when (result) {
            is ApiResult.Success -> try {
                callback(ApiResult.Success(parser(result.data)))
            } catch (e: Exception) {
                callback(ApiResult.Error("Error al procesar respuesta: ${e.message}"))
            }
            is ApiResult.Error -> callback(result)
            else -> callback(ApiResult.Error("Estado inesperado"))
        }
    }

    companion object {
        @Volatile private var INSTANCE: ApiClient? = null

        fun get(context: Context): ApiClient =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient(context.applicationContext).also { INSTANCE = it }
            }
    }
}
