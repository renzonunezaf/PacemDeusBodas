package com.pacemdeus.bodas.data

// Datos demo precargados en memoria. Replican el seed real del backend
// del v3.0.2 (admins, planners, parejas, instrumentos, momentos, canciones).
// Al no haber persistencia entre sesiones, el estado se reinicia al
// reabrir la app, pero durante una sesion los cambios in-memory funcionan.

object DemoData {

    /** Contraseña compartida por todas las cuentas demo. */
    const val SHARED_PASSWORD = "PacemDeus2026!"

    /** Telefono del coro al que el planner llama desde el detalle. */
    const val CHOIR_PHONE = "+51987654321"

    // ─── USUARIOS ──────────────────────────────────────────

    val users = listOf(
        // Admins
        User("u-a1", "renzonunez.af@gmail.com", SHARED_PASSWORD, UserRole.ADMIN),
        User("u-a2", "aldo@pacemdeus.com",      SHARED_PASSWORD, UserRole.ADMIN),
        // Wedding planners
        User("u-w1", "wedding1@correo.com",     SHARED_PASSWORD, UserRole.WEDDING_PLANNER),
        User("u-w2", "wedding2@correo.com",     SHARED_PASSWORD, UserRole.WEDDING_PLANNER),
        User("u-w3", "wedding3@correo.com",     SHARED_PASSWORD, UserRole.WEDDING_PLANNER),
        // Parejas
        User("u-c1", "novia1@correo.com",       SHARED_PASSWORD, UserRole.COUPLE),
        User("u-c2", "novia2@correo.com",       SHARED_PASSWORD, UserRole.COUPLE)
    )

    // ─── PERFILES DE PAREJAS ───────────────────────────────

    val couples = listOf(
        CoupleProfile(
            id = "cpl-1",
            userId = "u-c1",
            groomName = "Carlos Mendoza",
            brideName = "Ana Lucia Torres",
            groomDni = "71234567",
            brideDni = "71234568",
            phone = "+51 999 111 111"
        ),
        CoupleProfile(
            id = "cpl-2",
            userId = "u-c2",
            groomName = "Diego Herrera",
            brideName = "Sofia Ramirez",
            groomDni = "72345678",
            brideDni = "72345679",
            phone = "+51 999 222 222"
        )
    )

    // ─── PERFILES DE WEDDING PLANNERS ──────────────────────

    val planners = listOf(
        PlannerProfile("plr-1", "u-w1", "Valeria Campos",  "Love & Co. Events", "+51 987 111 111"),
        PlannerProfile("plr-2", "u-w2", "Daniela Rios",    "D'Rosas Wedding",   "+51 987 222 222"),
        PlannerProfile("plr-3", "u-w3", "Fernanda Lopez",  null,                "+51 987 333 333")
    )

    // ─── BODAS INICIALES ───────────────────────────────────

    val initialWeddings = listOf(
        Wedding(
            id = "wed-1",
            coupleId = "cpl-1",
            plannerId = "plr-1",
            weddingDate = "2026-06-20",
            weddingTime = "11:00",
            venueName = "Parroquia Virgen de Fatima",
            venueAddress = "Av. Armendariz 350, Miraflores, Lima",
            venueLat = -12.1215,
            venueLng = -77.0340,
            venuePhotoTaken = false,
            status = WeddingStatus.DRAFT,
            basePrice = 1800.0,
            instrumentsPrice = 0.0,
            notes = null
        ),
        Wedding(
            id = "wed-2",
            coupleId = "cpl-2",
            plannerId = "plr-2",
            weddingDate = "2026-07-18",
            weddingTime = "16:00",
            venueName = "Iglesia San Pedro",
            venueAddress = "Jr. Ucayali 363, Cercado de Lima",
            venueLat = -12.0464,
            venueLng = -77.0307,
            venuePhotoTaken = true,
            status = WeddingStatus.SUBMITTED,
            basePrice = 1800.0,
            instrumentsPrice = 300.0,
            notes = null
        )
    )

    // ─── INSTRUMENTOS DEL CORO ─────────────────────────────

    val instruments = listOf(
        Instrument("ins-1", "piano",         "Pianista",        150.0, 1),
        Instrument("ins-2", "voz_femenina",  "Voz Femenina",    150.0, 2),
        Instrument("ins-3", "voz_masculina", "Voz Masculina",   150.0, 3),
        Instrument("ins-4", "violin_1",      "Violin I",        180.0, 4),
        Instrument("ins-5", "violin_2",      "Violin II",       180.0, 5),
        Instrument("ins-6", "cello",         "Violoncello",     200.0, 6),
        Instrument("ins-7", "flauta",        "Flauta Traversa", 150.0, 7),
        Instrument("ins-8", "soprano",       "Soprano",         200.0, 8),
        Instrument("ins-9", "tenor",         "Tenor",           200.0, 9)
    )

    // ─── MOMENTOS LITURGICOS (14 en total) ─────────────────

    val moments = listOf(
        LiturgicalMoment("mom-1",  "entrada",        "Entrada",            "Procesional de ingreso de la novia",  1, 2),
        LiturgicalMoment("mom-2",  "kyrie",          "Kyrie",              "Acto penitencial",                    2, 1),
        LiturgicalMoment("mom-3",  "gloria",         "Gloria",             "Himno de alabanza",                   3, 1),
        LiturgicalMoment("mom-4",  "salmo",          "Salmo",              "Salmo responsorial",                  4, 1),
        LiturgicalMoment("mom-5",  "aleluya",        "Aleluya",            "Aclamacion al evangelio",             5, 1),
        LiturgicalMoment("mom-6",  "ofertorio",      "Ofertorio",          "Presentacion de ofrendas",            6, 2),
        LiturgicalMoment("mom-7",  "santo",          "Santo",              "Aclamacion eucaristica",              7, 1),
        LiturgicalMoment("mom-8",  "paz",            "Paz",                "Rito de la paz",                      8, 1),
        LiturgicalMoment("mom-9",  "cordero",        "Cordero",            "Cordero de Dios",                     9, 1),
        LiturgicalMoment("mom-10", "comunion",       "Comunion",           "Canto de comunion",                  10, 2),
        LiturgicalMoment("mom-11", "accion_gracias", "Accion de Gracias",  "Canto de accion de gracias",         11, 1),
        LiturgicalMoment("mom-12", "virgen",         "Virgen",             "Canto a la Virgen Maria",            12, 1),
        LiturgicalMoment("mom-13", "fotografias",    "Fotografias",        "Cantos durante las fotografias",     13, 4),
        LiturgicalMoment("mom-14", "salida",         "Salida",             "Marcha de salida",                   14, 1)
    )

    // ─── CATALOGO DE CANCIONES ─────────────────────────────

    val songs = listOf(
        Song("s-1",  "Canon en D",                   "Pachelbel",        "INST", setOf("entrada")),
        Song("s-2",  "Trumpet Voluntary",            "Clarke",           "INST", setOf("entrada", "salida")),
        Song("s-3",  "Marcha Nupcial",               "Mendelssohn",      "INST", setOf("entrada", "salida")),
        Song("s-4",  "Ave Maria",                    "Schubert",         "LA",   setOf("ofertorio", "virgen", "comunion")),
        Song("s-5",  "Ave Maria",                    "Bach/Gounod",      "LA",   setOf("ofertorio", "virgen")),
        Song("s-6",  "Señor, ten piedad",            "Liturgia",         "ES",   setOf("kyrie")),
        Song("s-7",  "Gloria a Dios",                "M. Frisina",       "ES",   setOf("gloria")),
        Song("s-8",  "El Señor es mi pastor",        "Salmo 23",         "ES",   setOf("salmo")),
        Song("s-9",  "Aleluya",                      "Haendel",          "LA",   setOf("aleluya")),
        Song("s-10", "Pescador de hombres",          "C. Gabarain",      "ES",   setOf("ofertorio")),
        Song("s-11", "Santo, Santo, Santo",          "Liturgia",         "ES",   setOf("santo")),
        Song("s-12", "Cordero de Dios",              "Liturgia",         "ES",   setOf("cordero")),
        Song("s-13", "La paz este con nosotros",     "Liturgia",         "ES",   setOf("paz")),
        Song("s-14", "Pan de Vida",                  "B. Farrell",       "ES",   setOf("comunion")),
        Song("s-15", "Alma misionera",               "Liturgia",         "ES",   setOf("accion_gracias")),
        Song("s-16", "Dios te salve Maria",          "Tradicional",      "ES",   setOf("virgen")),
        Song("s-17", "A thousand years",             "C. Perri",         "EN",   setOf("entrada", "fotografias")),
        Song("s-18", "Perfect",                      "Ed Sheeran",       "EN",   setOf("fotografias", "ofertorio")),
        Song("s-19", "Thinking Out Loud",            "Ed Sheeran",       "EN",   setOf("fotografias")),
        Song("s-20", "All of Me",                    "John Legend",      "EN",   setOf("fotografias", "comunion")),
        Song("s-21", "Hallelujah",                   "L. Cohen",         "EN",   setOf("ofertorio", "fotografias")),
        Song("s-22", "Can't Help Falling in Love",   "Elvis Presley",    "EN",   setOf("fotografias")),
        Song("s-23", "Marcha triunfal",              "Verdi",            "INST", setOf("salida")),
        Song("s-24", "Spring",                       "Vivaldi",          "INST", setOf("fotografias", "entrada")),
        Song("s-25", "Jesus de Nazareth",            "Gounod",           "ES",   setOf("comunion", "accion_gracias"))
    )

    // ─── SETLIST INICIAL ───────────────────────────────────
    // La boda 2 (Diego & Sofia, en SUBMITTED) ya tiene 4 cantos de muestra
    // para que la pantalla de aprobar no se vea vacia.

    val initialSetlist = listOf(
        SetlistItem("sli-1", "wed-2", "mom-1",  "s-1",  1),  // Entrada: Canon en D
        SetlistItem("sli-2", "wed-2", "mom-6",  "s-10", 1),  // Ofertorio: Pescador de hombres
        SetlistItem("sli-3", "wed-2", "mom-10", "s-14", 1),  // Comunion: Pan de Vida
        SetlistItem("sli-4", "wed-2", "mom-14", "s-3",  1)   // Salida: Marcha Nupcial
    )

    // ─── INSTRUMENTOS POR BODA ─────────────────────────────
    // La boda 2 ya tiene piano + voz femenina contratados (300 = 150+150).

    val initialWeddingInstruments: Map<String, Set<String>> = mapOf(
        "wed-1" to emptySet(),
        "wed-2" to setOf("ins-1", "ins-2")
    )
}
