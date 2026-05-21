package com.pacemdeus.bodas

import android.app.Application
import coil.Coil
import coil.ImageLoaderFactory
import com.pacemdeus.bodas.data.local.SetlistDatabase
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.session.SessionManager
import com.pacemdeus.bodas.services.createNotificationChannel
import com.pacemdeus.bodas.ui.util.buildPacemDeusImageLoader

// Application class de la app. Inicializa los singletons al arrancar
// (ApiClient, SessionManager, SetlistDatabase) para que esten listos
// cuando la primera pantalla los necesite. Tambien registra el canal
// de notificaciones requerido por Android 8+ y el ImageLoader global
// de Coil con cache de disco habilitada.

class PacemDeusApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Forzamos la inicializacion del singleton. No es estrictamente
        // necesario porque los singletons son lazy, pero asi el primer
        // login no paga el costo de inicializar Volley + cargar
        // SharedPreferences en el thread principal.
        ApiClient.get(this)
        SessionManager.get(this)
        SetlistDatabase.get(this)

        // Canal de notificaciones (Android 8+) requerido tanto para
        // notificaciones locales (polling) como para futuras push.
        createNotificationChannel(this)
    }

    // ImageLoaderFactory: Coil llama a este metodo la primera vez que
    // se usa AsyncImage sin un ImageLoader explicito, y guarda la
    // instancia. Nuestra implementacion tiene cache de disco habilitada
    // y respectCacheHeaders=false para que las fotos no se re-piden a
    // S3 cada vez que se recompone la pantalla.
    override fun newImageLoader() = buildPacemDeusImageLoader(this)
}
