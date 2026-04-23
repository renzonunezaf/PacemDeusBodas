package com.pacemdeus.bodas

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Clase Application
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Punto de entrada de la aplicación Android.
// Mantiene una referencia singleton para acceso global al contexto
// (requerido por SessionManager para SharedPreferences).
// ═══════════════════════════════════════════════════════════════

import android.app.Application

class PacemDeusApp : Application() {

    companion object {
        /** Instancia singleton de la aplicación, accesible globalmente */
        lateinit var instance: PacemDeusApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
