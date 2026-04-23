package com.pacemdeus.bodas.data.local

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Base de Datos Local (Room)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Base de datos SQLite local para almacenamiento offline.
// Usa el patrón singleton para evitar múltiples instancias.
// Contiene 2 tablas de cache: momentos litúrgicos y setlist.
// ═══════════════════════════════════════════════════════════════

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pacemdeus.bodas.data.local.dao.MomentDao
import com.pacemdeus.bodas.data.local.dao.SetlistDao
import com.pacemdeus.bodas.data.local.entities.CachedMoment
import com.pacemdeus.bodas.data.local.entities.CachedSetlistItem

@Database(
    entities = [CachedMoment::class, CachedSetlistItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** DAO para momentos litúrgicos */
    abstract fun momentDao(): MomentDao

    /** DAO para items del setlist */
    abstract fun setlistDao(): SetlistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Retorna la instancia singleton de la base de datos */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pacem_deus_cache"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
