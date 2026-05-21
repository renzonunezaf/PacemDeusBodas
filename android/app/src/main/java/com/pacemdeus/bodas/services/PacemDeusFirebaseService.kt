package com.pacemdeus.bodas.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pacemdeus.bodas.MainActivity
import com.pacemdeus.bodas.R
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.session.SessionManager

// Servicio FCM que recibe push notifications del backend y las muestra
// en la bandeja de notificaciones del sistema.
//
// Casos de uso:
//   - Notificar al admin cuando la novia envia su boda para aprobacion
//   - Notificar a la novia cuando el admin aprueba o devuelve
//
// Activado en v07 con google-services.json del proyecto Firebase
// "pacem-deus-bodas". El backend envia los pushes via Firebase API V1
// usando un service account JSON cargado en el layer del Lambda.

private const val TAG = "FCMService"
private const val CHANNEL_ID = "pacem_deus_general"

class PacemDeusFirebaseService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Soportamos ambos formatos: notification + data, o data-only.
        val titulo = message.notification?.title
            ?: message.data["titulo"]
            ?: "Pacem Deus Bodas"
        val cuerpo = message.notification?.body
            ?: message.data["mensaje"]
            ?: "Tienes una notificacion nueva"
        // id_boda viene en el data payload del backend (notifications.py).
        // Si esta presente, lo metemos como extra en el Intent para que
        // MainActivity haga deep link al detalle correspondiente.
        val idBoda = message.data["id_boda"]

        Log.d(TAG, "Push recibido: $titulo - $cuerpo (id_boda=$idBoda)")
        mostrarNotificacion(this, titulo, cuerpo, idBoda)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM: ${token.take(20)}...")

        val session = SessionManager.get(applicationContext)
        session.saveFcmToken(token)

        // Si hay sesion abierta, registrar inmediatamente en el backend.
        // Si no la hay, queda pendiente: LoginScreen lo registra al
        // proximo login exitoso.
        if (session.getToken() != null) {
            ApiClient.get(applicationContext).registerFcmToken(token) {
                // Best effort: si falla, el proximo login lo reintenta.
            }
        }
    }
}

/**
 * Crea el NotificationChannel requerido por Android 8+. Se llama una
 * sola vez al arrancar la app desde PacemDeusApplication.
 */
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Notificaciones generales",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos sobre tu boda y actualizaciones del coro"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

/**
 * Muestra una notificacion en la bandeja del sistema. Al tocarla, abre
 * MainActivity. Si `idBoda` viene informado, lo pasa como extra para que
 * MainActivity haga deep link al detalle de la boda en vez de aterrizar
 * en el home del rol.
 *
 * Reutilizada por FCM y por el polling de fallback.
 */
fun mostrarNotificacion(
    context: Context,
    titulo: String,
    cuerpo: String,
    idBoda: String? = null
) {
    val openIntent = Intent(context, MainActivity::class.java).apply {
        // FLAG_ACTIVITY_NEW_TASK requerido al iniciar desde contexto no-Activity.
        // FLAG_ACTIVITY_SINGLE_TOP + launchMode="singleTop" en el manifest hacen
        // que si MainActivity ya esta corriendo, Android llame a onNewIntent en
        // vez de re-crear la activity (preserva el estado del usuario).
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (idBoda != null) {
            putExtra(MainActivity.EXTRA_WEDDING_ID, idBoda)
        }
    }
    val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    else
        PendingIntent.FLAG_UPDATE_CURRENT

    // requestCode unico por id_boda asegura que multiples PendingIntents
    // para distintas bodas no se machen entre si (con mismo requestCode,
    // FLAG_UPDATE_CURRENT sobreescribiria los extras del anterior).
    val requestCode = idBoda?.hashCode() ?: 0
    val pendingIntent = PendingIntent.getActivity(context, requestCode, openIntent, pendingFlags)

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_pacem_deus)
        .setColor(0xFFB8995E.toInt())              // Gold de la paleta
        .setColorized(true)
        .setContentTitle(titulo)
        .setContentText(cuerpo)
        .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    // Notification id: si hay id_boda lo usamos como hash, asi multiples
    // push de la misma boda se reemplazan en vez de apilarse en la bandeja.
    val notificationId = idBoda?.hashCode() ?: System.currentTimeMillis().toInt()
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(notificationId, builder.build())
}
