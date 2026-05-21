package com.pacemdeus.bodas.data.validation

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Validaciones de fecha de evento. La regla del negocio: la fecha de la
// boda debe ser igual o posterior a HOY (no se aceptan fechas pasadas).
//
// Trabajamos en zona Lima (UTC-5). El DatePicker entrega millis en UTC,
// asi que conviene convertir antes de comparar.

object DateValidator {

    private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * True si los millis representan una fecha hoy o futura (en zona Lima).
     * `null` se considera invalido por defecto.
     */
    fun isFutureOrToday(dateMillis: Long?): Boolean {
        if (dateMillis == null) return false

        // Trabajar con dia completo: comparamos solo el dia, no la hora.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = dateMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        return cal.timeInMillis >= today.timeInMillis
    }

    /**
     * Mensaje de error legible para mostrar en supportingText. Devuelve
     * null cuando la fecha es valida.
     */
    fun errorMessage(dateMillis: Long?): String? {
        if (dateMillis == null) return "Selecciona la fecha del evento"
        if (!isFutureOrToday(dateMillis)) return "La fecha debe ser hoy o posterior"
        return null
    }

    /** Formatea la fecha como ISO YYYY-MM-DD (la que espera el backend). */
    fun toIsoString(dateMillis: Long): String = ISO_FMT.format(Date(dateMillis))

    /** Parsea un ISO YYYY-MM-DD a millis UTC. Null si el string es invalido. */
    fun fromIsoString(iso: String): Long? = try {
        ISO_FMT.parse(iso)?.time
    } catch (e: Exception) {
        null
    }

    /** Millis del inicio del dia hoy en UTC. Util como minimo del DatePicker. */
    fun todayUtcMillis(): Long {
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        return today.timeInMillis
    }
}
