package com.pacemdeus.bodas.data.local.dao

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Data Access Objects (Room)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Interfaces DAO que definen las operaciones CRUD sobre las
// tablas de cache local SQLite.
// ═══════════════════════════════════════════════════════════════

import androidx.room.*
import com.pacemdeus.bodas.data.local.entities.CachedMoment
import com.pacemdeus.bodas.data.local.entities.CachedSetlistItem

/** Operaciones sobre la tabla de momentos litúrgicos cacheados */
@Dao
interface MomentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedMoment>)

    @Query("SELECT * FROM cached_moments ORDER BY displayOrder")
    suspend fun getAll(): List<CachedMoment>

    @Query("DELETE FROM cached_moments")
    suspend fun deleteAll()
}

/** Operaciones sobre la tabla de setlist cacheado */
@Dao
interface SetlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedSetlistItem>)

    @Query("SELECT * FROM cached_setlist WHERE weddingId = :weddingId ORDER BY momentOrder, displayOrder")
    suspend fun getByWedding(weddingId: String): List<CachedSetlistItem>

    @Query("DELETE FROM cached_setlist WHERE weddingId = :weddingId")
    suspend fun deleteByWedding(weddingId: String)
}
