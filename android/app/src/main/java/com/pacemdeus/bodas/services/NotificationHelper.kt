package com.pacemdeus.bodas.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pacemdeus.bodas.R

/**
 * Helper para emitir notificaciones locales al detectar cambios de estado
 * de una boda durante el polling foreground del cliente.
 *
 * No usa Firebase ni FCM (deliberadamente, sin dependencia externa). El
 * mecanismo es:
 *   1. El cliente hace polling cada N segundos en CoupleHomeScreen.
 *   2. Compara el estado actual con el ultimo conocido (en memoria).
 *   3. Si detecto un cambio relevante, llama a notifyStatusChange aqui.
 *   4. Aparece una notificacion del sistema con el icono y la marca del
 *      coro.
 *
 * Esto da una experiencia "tiempo real" mientras la app de la novia
 * este abierta en cualquier pantalla. Para tiempo real cuando la app
 * esta cerrada hace falta FCM, no cubierto aqui.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "boda_updates"
    private const val CHANNEL_NAME = "Actualizaciones de la boda"
    private const val CHANNEL_DESC =
        "Notificaciones cuando el coro actualiza el estado de tu boda"

    /**
     * Crea el canal de notificaciones. Idempotente: si ya existe, Android
     * ignora la operacion. Debe llamarse al arrancar la app (MainActivity
     * onCreate) para que las notificaciones funcionen en Android 8+.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Compara el estado anterior con el nuevo y, si el cambio es uno de
     * los que nos interesa notificar (aprobacion, devolucion, firma),
     * emite la notificacion correspondiente.
     *
     * @param oldStatus el estado conocido previamente. Null si es la
     *        primera vez que vemos esta boda en la sesion (no notifica).
     * @param newStatus el estado actual recien obtenido del backend.
     * @param weddingNick una etiqueta corta para identificar la boda en
     *        la notificacion (ej. "tu boda" si solo hay una).
     */
    fun notifyStatusChange(
        context: Context,
        oldStatus: String?,
        newStatus: String,
        weddingNick: String = "tu boda"
    ) {
        // Primera vez que vemos la boda: solo guardamos el estado, no
        // notificamos (si no, al abrir la app sonarian notificaciones
        // viejas).
        if (oldStatus == null || oldStatus == newStatus) return

        val (title, message) = mensajePorTransicion(oldStatus, newStatus, weddingNick)
            ?: return  // transicion no interesante (ej. DRAFT -> SUBMITTED)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pacem_deus)
            .setColor(0xFFB8995E.toInt())              // Gold de la paleta
            .setColorized(true)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = NotificationManagerCompat.from(context)
        try {
            // Usamos un id basado en el newStatus para que la misma
            // transicion no apile multiples notificaciones identicas.
            nm.notify(newStatus.hashCode(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denegado por el usuario. No podemos
            // hacer nada mas, la notificacion se pierde silenciosamente.
        }
    }

    /**
     * Mapea cada transicion de estado a un mensaje legible para la
     * pareja. Devuelve null para las transiciones que no merecen
     * notificacion (ej. DRAFT -> SUBMITTED es accion de la propia pareja
     * y no necesita avisarse a si misma).
     */
    private fun mensajePorTransicion(
        oldStatus: String,
        newStatus: String,
        weddingNick: String
    ): Pair<String, String>? {
        return when {
            // El coro aprobo la boda
            newStatus == "APPROVED" && oldStatus == "SUBMITTED" ->
                "Tu boda fue aprobada" to
                "El Coro Pacem Deus aprobo $weddingNick. Ya puedes revisar el contrato."

            // El coro devolvio con anotaciones
            newStatus == "RETURNED_WITH_NOTES" ->
                "El coro propuso cambios" to
                "El Coro Pacem Deus dejo anotaciones en $weddingNick. Entra a revisarlas."

            // Contrato firmado por el coro
            newStatus == "CONTRACTED" && oldStatus == "APPROVED" ->
                "Contrato firmado" to
                "El contrato musical de $weddingNick fue firmado. Todo listo."

            else -> null
        }
    }
}
