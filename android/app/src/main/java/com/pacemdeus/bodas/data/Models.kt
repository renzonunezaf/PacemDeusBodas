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
 *
 * Nota: el estado "Observado" no es un valor del enum sino una variante
 * visual de DRAFT cuando el admin devolvio el evento con notas. Para
 * detectarlo se usa `Wedding.isObservado` (que combina status + notes).
 */
enum class WeddingStatus {
    DRAFT, SUBMITTED, APPROVED, CONTRACTED, CANCELLATION_REQUESTED,
    COMPLETED, RETURNED_WITH_NOTES, CANCELLED;

    /** Etiqueta legible en español para mostrar en pantalla. */
    fun displayName(): String = when (this) {
        DRAFT -> "Borrador"
        SUBMITTED -> "Enviado al coro"
        APPROVED -> "Aprobado"
        CONTRACTED -> "Contratado"
        CANCELLATION_REQUESTED -> "Cancelacion solicitada"
        COMPLETED -> "Completado"
        RETURNED_WITH_NOTES -> "Con anotaciones del coro"
        CANCELLED -> "Cancelado"
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
 * Foto del local cargada al evento. Cada evento puede tener hasta 5 fotos.
 * El orden lo asigna el backend al insertar (max + 1).
 *
 * Caption y autor: el caption es un comentario opcional sobre la foto,
 * y authorName es el nombre legible de quien subio la foto (resuelto en
 * backend segun el rol: nombres de la pareja, del planner o "Coro Pacem
 * Deus"). authorUserId permite que la UI sepa si el usuario actual es el
 * autor (y por tanto puede editar el caption).
 *
 * El backend devuelve URL https publica de S3; el cliente la usa para
 * mostrar con coil.compose.AsyncImage sin necesidad de tokens.
 */
data class WeddingPhoto(
    val id: String,
    val url: String,
    val order: Int,
    val uploadedAt: String?,
    val caption: String? = null,
    val authorName: String? = null,
    val authorRole: String? = null,
    val authorUserId: String? = null
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
    val venuePhotoUrl: String?,     // URL HTTPS de la foto del local en S3, null si aun no se tomo
    val status: WeddingStatus,
    val basePrice: Double,
    val instrumentsPrice: Double,
    val travelPrice: Double = 0.0,  // precio_movilidad: cargo extra si la boda es fuera de Lima
    val outsideOfLima: Boolean = false, // fuera_de_lima: la boda es en provincia
    val notes: String?,
    // Campos joineables que el backend puede incluir cuando el endpoint
    // devuelve datos del couple/planner asociados. Nullables porque no
    // todos los endpoints los traen (ej. listBodas para couple no los necesita).
    val coupleGroomName: String? = null,
    val coupleBrideName: String? = null,
    val plannerName: String? = null
) {
    /**
     * Precio total mostrado al usuario. Preferimos el precio_total que
     * calcula el backend (consulta Distance Matrix, aplica reglas de
     * Lima vs fuera de Lima, etc.) en lugar de sumar localmente.
     */
    val totalPrice: Double get() = basePrice + instrumentsPrice + travelPrice

    /**
     * "Observado" = DRAFT con notas del admin. Es una variante visual de
     * DRAFT que indica que el coro reviso el evento y lo devolvio con
     * comentarios para corregir.
     */
    val isObservado: Boolean
        get() = status == WeddingStatus.DRAFT && !notes.isNullOrBlank()

    /** True cuando la novia puede editar todos los datos del evento. */
    val isEditable: Boolean
        get() = status == WeddingStatus.DRAFT

    /**
     * True si la novia puede deshacer el envio (volver de SUBMITTED a DRAFT).
     * Aplica solo cuando esta SUBMITTED y el admin no la ha tocado aun.
     */
    val canUnsubmit: Boolean
        get() = status == WeddingStatus.SUBMITTED

    /** True si la novia puede solicitar cancelacion (APPROVED o CONTRACTED). */
    val canRequestCancellation: Boolean
        get() = status == WeddingStatus.APPROVED || status == WeddingStatus.CONTRACTED

    /** Etiqueta de estado lista para mostrar (distingue Observado de Borrador). */
    fun statusDisplayName(): String =
        if (isObservado) "Observado por el coro" else status.displayName()

    /** Etiqueta legible de la pareja para mostrar en listas. */
    fun coupleLabel(): String {
        val groom = coupleGroomName?.trim()
        val bride = coupleBrideName?.trim()
        return when {
            !groom.isNullOrBlank() && !bride.isNullOrBlank() -> "$groom & $bride"
            !groom.isNullOrBlank() -> groom
            !bride.isNullOrBlank() -> bride
            else -> "Pareja #$coupleId"
        }
    }
}

/**
 * Wedding planner minimal usado para el dropdown del coordinador al
 * asignar plannings a una boda. Viene del endpoint listPlanners.
 */
data class PlannerSummary(
    val id: String,
    val name: String,
    val company: String?,
    val phone: String?
)

/**
 * Instrumento o voz contratable.
 *
 * Importante: el backend identifica los instrumentos por su `slug`
 * (string como "piano", "voz_femenina") al asignarlos a una boda, no
 * por el id numerico. Por eso usamos `id = slug` para mantener una sola
 * llave de identidad en todo el cliente y enviar la lista correcta al
 * endpoint PUT /bodas/{id}/instrumentos.
 *
 * `includedInBasePackage` viene del nuevo modelo de pricing v2: los
 * instrumentos marcados true (piano y voz_femenina) NO se cobran como
 * line item, vienen incluidos en el paquete base S/. 650. En la UI se
 * muestran con candado y badge "Incluido en paquete" sin precio.
 *
 * El precio individual del instrumento ya no se trae aqui: viene del
 * endpoint POST /bodas/cotizar segun la distancia.
 */
data class Instrument(
    val id: String,                          // = slug; identidad usada por el backend
    val name: String,
    val sortOrder: Int,
    val includedInBasePackage: Boolean = false
)

/**
 * Momento liturgico de la ceremonia (Entrada, Comunion, Salida, etc.).
 *
 * - maxSongs controla cuantos cantos admite el momento.
 * - enabled: false cuando el tiempo liturgico vigente prohibe ese momento
 *   (ej. Gloria/Aleluya en Cuaresma).
 * - disabledReason: texto legible explicando por que esta deshabilitado.
 *
 * El backend devuelve estos dos campos solo cuando se llama a
 * GET /momentos?fecha=YYYY-MM-DD; si la llamada va sin fecha, todos los
 * momentos vienen habilitados.
 */
data class LiturgicalMoment(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val displayOrder: Int,
    val maxSongs: Int,
    val enabled: Boolean = true,
    val disabledReason: String? = null
)

/**
 * Cancion del catalogo.
 *
 * Ya no llevamos `allowedMomentSlugs`: el backend no incluye en cada
 * cancion la lista de momentos permitidos. En su lugar, el cliente
 * pide canciones filtradas por momento via `GET /canciones?id_momento=N`,
 * que es el patron disenado por el backend.
 */
data class Song(
    val id: String,
    val title: String,
    val author: String,
    val language: String            // "ES", "LA", "EN", "INST"
)

/** Item del setlist: una cancion asignada a un momento de una boda. */
data class SetlistItem(
    val id: String,
    val weddingId: String,
    val momentId: String,
    val songId: String,
    val displayOrder: Int,
    // v05: titulo y autor incluidos directo desde el backend (el SP
    // usp_setlist_listar y usp_setlist_obtener los devuelven via JOIN
    // con cancion). Esto elimina la dependencia del songCache local
    // que tardaba en cargarse y mostraba "(canto #ID)" mientras tanto.
    val title: String? = null,
    val author: String? = null
)

/**
 * Cotizacion en vivo devuelta por POST /bodas/cotizar.
 *
 * El backend la calcula sin tocar BD (excepto para leer la config de
 * precios y el catalogo de nombres de instrumentos). El cliente la
 * pide mientras el usuario edita la boda, para mostrar el precio en
 * tiempo real conforme cambian la ubicacion y los instrumentos.
 *
 * Modelo v2 (mayo 2026):
 * - El `basePrice` viene multiplicado por el factor de distancia y ya
 *   incluye director + piano + voz femenina (no se desagrega para el
 *   usuario final).
 * - `instrumentsPrice` es la suma de los instrumentos OPCIONALES; el
 *   detalle por instrumento esta en `instrumentsDetail`.
 * - `travelPrice` solo > 0 cuando la distancia supera el umbral
 *   (20 km por defecto).
 * - `distanceFactor` es el multiplicador del tramo de km vigente.
 *
 * Todas las amounts vienen en soles (S/.) y son Double.
 */
data class PriceQuote(
    val basePrice: Double,                      // precio_base
    val instrumentsPrice: Double,               // precio_instrumentos
    val instrumentsDetail: List<QuoteInstrument>,
    val travelPrice: Double,                    // precio_movilidad
    val totalPrice: Double,                     // precio_total
    val outsideOfLima: Boolean,                 // fuera_de_lima
    val distanceKm: Double,                     // distancia_km
    val durationMinutes: Int,                   // duracion_minutos
    val durationTrafficMinutes: Int,            // duracion_con_trafico
    val distanceFactor: Double,                 // factor_distancia
    // Acelerador XL: cuando el numero de pasajeros (integrantes +
    // director) supera el umbral configurado, la movilidad se multiplica
    // por el factor XL (1.20 por default). Lo mostramos como badge en el
    // UI para que la novia sepa por que subio el precio al agregar el
    // 4to musico.
    val isXl: Boolean = false,                  // grupo_xl
    val passengers: Int = 0,                    // pasajeros
    // Desglose de movilidad (checkpoint v03). `travelPrice` es la suma
    // final (con XL y centro historico aplicados), estos dos campos son
    // el desglose previo: cuanto del precio viene por distancia y cuanto
    // por trafico extra sobre la duracion normal. Se muestran en UI para
    // que la novia entienda por que sube/baja el precio segun el horario.
    val mobilityDistance: Double = 0.0,         // movilidad_distancia
    val mobilityTraffic: Double = 0.0           // movilidad_trafico
)

/**
 * Detalle individual de un instrumento dentro de una cotizacion.
 * `includedInBase = true` significa que el instrumento esta cubierto por
 * el paquete base y su `price` siempre es 0. En la UI lo mostramos como
 * "Incluido en paquete base" sin valor monetario.
 */
data class QuoteInstrument(
    val slug: String,
    val name: String,
    val price: Double,
    val includedInBase: Boolean = false
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

    /**
     * Etiqueta corta para mostrar en la barra superior, identificando
     * de un vistazo a quien esta logueado. Reglas:
     *   - ADMIN  -> "admin"
     *   - COUPLE -> iniciales del novio y la novia, ej. "MG & JF"
     *              (primera letra del primer y segundo nombre de cada uno)
     *   - PLANNER -> iniciales del nombre completo del planner en mayuscula
     */
    fun shortBadge(): String {
        return when {
            user.role == UserRole.ADMIN -> "admin"
            coupleProfile != null -> {
                val gi = initials(coupleProfile.groomName)
                val bi = initials(coupleProfile.brideName)
                if (gi.isNotBlank() && bi.isNotBlank()) "$gi & $bi"
                else (gi + bi).ifBlank { "Novios" }
            }
            plannerProfile != null -> initials(plannerProfile.name).ifBlank { "Planner" }
            else -> ""
        }
    }
}

/**
 * Devuelve las iniciales del nombre dado: primera letra de las primeras
 * dos palabras no vacias, en mayuscula. Ej. "Maria Gracia Lopez" -> "MG",
 * "Juan" -> "J".
 */
private fun initials(fullName: String): String {
    return fullName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
}

/**
 * Una boda CONTRACTED de un dia, como la devuelve el endpoint
 * GET /disponibilidad/{anio}/{mes}. Sirve para mostrar en el bottom
 * sheet cuando la novia tap un dia ya ocupado.
 */
data class DayBooking(
    val weddingId: String,
    val time: String,     // "HH:MM"
    val couple: String,   // "Carlos y Maria"
    val status: String = "" // estado de la boda: DRAFT/SUBMITTED/APPROVED/CONTRACTED/...
)

/**
 * Estado de un dia en el calendario de seleccion. Determina el color
 * con el que se pinta y si se puede seleccionar.
 */
enum class DayAvailability {
    FREE,      // sin bodas CONTRACTED -> verde/normal, seleccionable
    PARTIAL,   // 1 boda CONTRACTED, queda ventana >=5h libre -> ambar, seleccionable
    FULL,      // 2 bodas o ventana insuficiente -> rojo, NO seleccionable
    OCCUPIED   // modo admin: dia con bodas (de cualquier estado), sombreado neutro
}

/**
 * Mapa de dia -> estado de disponibilidad para un mes consultado.
 * key: "YYYY-MM-DD"
 */
data class MonthAvailability(
    val year: Int,
    val month: Int,                                  // 1-12
    val dayStates: Map<String, DayAvailability>,    // solo dias con conflictos
    val dayBookings: Map<String, List<DayBooking>>  // solo dias con bodas CONTRACTED
)

/**
 * Respuesta del endpoint POST /bodas/validar-conflicto. Si conflict
 * es true, razon contiene un mensaje legible para mostrar al usuario,
 * y horasDisponibles puede contener un CSV con horarios libres ese dia.
 */
data class ConflictCheck(
    val conflict: Boolean,
    val reason: String,
    val availableHours: String
)


/**
 * Notificacion enviada via polling al usuario autenticado. El sistema
 * usa polling (sin Firebase): la app llama GET /notifications/poll cada
 * 10 segundos y dispara una notificacion local del sistema operativo
 * para cada item nuevo recibido.
 */
data class Notification(
    val id: Long,
    val tipo: String,                // "BODA_SUBMITTED", "BODA_APPROVED", etc.
    val title: String,
    val message: String,
    val weddingId: String?,          // null si la notificacion no esta asociada a una boda
    val createdAt: String,           // ISO 8601 con offset
    val readAt: String?              // ISO 8601 si fue marcada leida; null si pendiente
)


/**
 * Resultado de un poll de notificaciones. Devuelve los items nuevos y el
 * timestamp del servidor para usar en el siguiente poll.
 */
data class NotificationPollResult(
    val items: List<Notification>,
    val serverTime: String
)
