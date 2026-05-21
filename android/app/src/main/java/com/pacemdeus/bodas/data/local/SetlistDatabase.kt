package com.pacemdeus.bodas.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Almacenamiento local SQLite para la HU-06: "Setlist disponible sin conexion".
//
// Cuando la novia/o ve su setlist en la app, lo cacheamos aqui. Si pierde
// conexion (ej. dentro de la iglesia el dia de la boda), la pantalla
// puede leer del SQLite y mostrar la lista sin pedir red.
//
// Usamos SQLite raw (SQLiteOpenHelper) tal como lo enseña el profesor.
// NO usamos Room (sigue prohibido por el patron del curso).
//
// Esquema simple, una sola tabla con la info ya desnormalizada para
// renderizar rapido (no hay JOINs aqui adentro).

class SetlistDatabase(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_SETLIST (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ID_BODA TEXT NOT NULL,
                $COL_ID_SETLIST TEXT NOT NULL,
                $COL_ID_MOMENTO TEXT NOT NULL,
                $COL_NOMBRE_MOMENTO TEXT NOT NULL,
                $COL_ORDEN_MOMENTO INTEGER NOT NULL,
                $COL_ID_CANCION TEXT NOT NULL,
                $COL_TITULO_CANCION TEXT NOT NULL,
                $COL_AUTOR_CANCION TEXT,
                $COL_IDIOMA_CANCION TEXT,
                $COL_ORDEN_SETLIST INTEGER NOT NULL,
                $COL_FECHA_CACHEO INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX idx_setlist_boda ON $TABLE_SETLIST($COL_ID_BODA)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Politica simple: en cambios de version, descartamos cache local.
        // El siguiente refresh online lo vuelve a llenar.
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SETLIST")
        onCreate(db)
    }

    /**
     * Reemplaza completamente el cache del setlist de una boda.
     * Se llama cuando la pantalla obtiene una respuesta fresca del backend.
     */
    fun cacheSetlist(idBoda: String, items: List<CachedSetlistItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Borra el cache viejo de esta boda
            db.delete(TABLE_SETLIST, "$COL_ID_BODA = ?", arrayOf(idBoda))

            // Inserta los items nuevos
            val now = System.currentTimeMillis()
            for (item in items) {
                val values = ContentValues().apply {
                    put(COL_ID_BODA, idBoda)
                    put(COL_ID_SETLIST, item.idSetlist)
                    put(COL_ID_MOMENTO, item.idMomento)
                    put(COL_NOMBRE_MOMENTO, item.nombreMomento)
                    put(COL_ORDEN_MOMENTO, item.ordenMomento)
                    put(COL_ID_CANCION, item.idCancion)
                    put(COL_TITULO_CANCION, item.tituloCancion)
                    put(COL_AUTOR_CANCION, item.autorCancion)
                    put(COL_IDIOMA_CANCION, item.idiomaCancion)
                    put(COL_ORDEN_SETLIST, item.ordenSetlist)
                    put(COL_FECHA_CACHEO, now)
                }
                db.insert(TABLE_SETLIST, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Lee el setlist cacheado de una boda. Devuelve lista vacia si no hay cache.
     * Ordenado por momento (orden litorgico) y luego por orden dentro del momento.
     */
    fun loadSetlist(idBoda: String): List<CachedSetlistItem> {
        val items = mutableListOf<CachedSetlistItem>()
        val cursor = readableDatabase.query(
            TABLE_SETLIST,
            null,
            "$COL_ID_BODA = ?",
            arrayOf(idBoda),
            null, null,
            "$COL_ORDEN_MOMENTO ASC, $COL_ORDEN_SETLIST ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                items.add(
                    CachedSetlistItem(
                        idSetlist = it.getString(it.getColumnIndexOrThrow(COL_ID_SETLIST)),
                        idMomento = it.getString(it.getColumnIndexOrThrow(COL_ID_MOMENTO)),
                        nombreMomento = it.getString(it.getColumnIndexOrThrow(COL_NOMBRE_MOMENTO)),
                        ordenMomento = it.getInt(it.getColumnIndexOrThrow(COL_ORDEN_MOMENTO)),
                        idCancion = it.getString(it.getColumnIndexOrThrow(COL_ID_CANCION)),
                        tituloCancion = it.getString(it.getColumnIndexOrThrow(COL_TITULO_CANCION)),
                        autorCancion = it.getString(it.getColumnIndexOrThrow(COL_AUTOR_CANCION)) ?: "",
                        idiomaCancion = it.getString(it.getColumnIndexOrThrow(COL_IDIOMA_CANCION)) ?: "ES",
                        ordenSetlist = it.getInt(it.getColumnIndexOrThrow(COL_ORDEN_SETLIST))
                    )
                )
            }
        }
        return items
    }

    /** Cuenta cuantos items hay cacheados para una boda. Util para mostrar
     * indicadores tipo "tienes 12 cantos disponibles offline". */
    fun countSetlist(idBoda: String): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SETLIST WHERE $COL_ID_BODA = ?",
            arrayOf(idBoda)
        )
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /** Borra el cache de una boda especifica. */
    fun clearSetlist(idBoda: String) {
        writableDatabase.delete(TABLE_SETLIST, "$COL_ID_BODA = ?", arrayOf(idBoda))
    }

    /** Borra TODO el cache local. Util al cerrar sesion. */
    fun clearAll() {
        writableDatabase.delete(TABLE_SETLIST, null, null)
    }

    /** Fecha del cache mas reciente para esta boda. Para mostrar
     * "Ultima sincronizacion: hace X minutos". */
    fun lastCachedAt(idBoda: String): Long? {
        val cursor = readableDatabase.rawQuery(
            "SELECT MAX($COL_FECHA_CACHEO) FROM $TABLE_SETLIST WHERE $COL_ID_BODA = ?",
            arrayOf(idBoda)
        )
        cursor.use {
            return if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }

    companion object {
        private const val DATABASE_NAME = "pacem_deus_local.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_SETLIST = "cached_setlist"
        const val COL_ID = "id"
        const val COL_ID_BODA = "id_boda"
        const val COL_ID_SETLIST = "id_setlist"
        const val COL_ID_MOMENTO = "id_momento"
        const val COL_NOMBRE_MOMENTO = "nombre_momento"
        const val COL_ORDEN_MOMENTO = "orden_momento"
        const val COL_ID_CANCION = "id_cancion"
        const val COL_TITULO_CANCION = "titulo_cancion"
        const val COL_AUTOR_CANCION = "autor_cancion"
        const val COL_IDIOMA_CANCION = "idioma_cancion"
        const val COL_ORDEN_SETLIST = "orden_setlist"
        const val COL_FECHA_CACHEO = "fecha_cacheo"

        @Volatile private var INSTANCE: SetlistDatabase? = null

        fun get(context: Context): SetlistDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SetlistDatabase(context.applicationContext).also { INSTANCE = it }
            }
    }
}

/**
 * Item del setlist en formato desnormalizado para mostrar en pantalla
 * sin necesidad de JOINs en SQLite. Es el formato que la pantalla
 * SetlistScreen consume directamente.
 */
data class CachedSetlistItem(
    val idSetlist: String,
    val idMomento: String,
    val nombreMomento: String,
    val ordenMomento: Int,
    val idCancion: String,
    val tituloCancion: String,
    val autorCancion: String,
    val idiomaCancion: String,
    val ordenSetlist: Int
)
