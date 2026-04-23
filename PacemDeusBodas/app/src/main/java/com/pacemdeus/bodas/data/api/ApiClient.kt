package com.pacemdeus.bodas.data.api

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Cliente HTTP (Retrofit)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Singleton que configura Retrofit con OkHttp.
// Incluye un interceptor que agrega automáticamente el token JWT
// en el header Authorization de cada request HTTP.
//
// La URL base se define en build.gradle como buildConfigField:
//   Desarrollo: http://10.0.2.2:5000 (emulador → localhost)
//   Producción: URL de AWS API Gateway
// ═══════════════════════════════════════════════════════════════

import com.pacemdeus.bodas.BuildConfig
import com.pacemdeus.bodas.data.prefs.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /** Interceptor que agrega el JWT a cada request saliente */
    private val authInterceptor = Interceptor { chain ->
        val token = SessionManager.getToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    /** Cliente HTTP con interceptor de autenticación y timeouts */
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Instancia singleton del servicio API para consumir endpoints */
    val service: ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL + "/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
