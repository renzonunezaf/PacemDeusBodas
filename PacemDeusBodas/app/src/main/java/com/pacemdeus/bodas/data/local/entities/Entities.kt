package com.pacemdeus.bodas.data.local.entities

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Entidades Room (SQLite)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Tablas de cache local para funcionamiento offline.
// Se almacenan momentos litúrgicos y setlist para que los novios
// puedan consultar su ceremonia sin conexión a internet.
// ═══════════════════════════════════════════════════════════════

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Momento litúrgico cacheado desde la API */
@Entity(tableName = "cached_moments")
data class CachedMoment(
    @PrimaryKey val id: String,
    val slug: String,
    val name: String,
    val description: String?,
    val icon: String?,
    val displayOrder: Int,
    val maxSongs: Int
)

/** Item del setlist cacheado para consulta offline */
@Entity(tableName = "cached_setlist")
data class CachedSetlistItem(
    @PrimaryKey val id: String,
    val weddingId: String,
    val songTitle: String,
    val songAuthor: String?,
    val momentName: String,
    val momentSlug: String,
    val momentOrder: Int,
    val displayOrder: Int
)

/** Offline_Setlist**/

@Entity(tableName = "offline_setlist")
data class SetlistEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val author: String,
    val moment_name: String
)
