package com.pacemdeus.bodas.ui.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

/**
 * Construye el ImageLoader global de Coil para la app.
 *
 * Por que existe esta config (vs el default de Coil 2.x):
 *
 * El bucket S3 pacem-deus-fotos no manda Cache-Control en las
 * respuestas. Por defecto Coil REVALIDA en cada recomposicion cuando
 * el server no manda headers de cache, lo que hace que las fotos
 * "desaparezcan" al volver a una pantalla que ya las habia mostrado:
 *   - la pantalla se destruye y recompone (volver desde otro tab)
 *   - Coil pide la imagen otra vez
 *   - en algunos casos la request queda en limbo (scope cancelado,
 *     race condition) y el placeholder se queda vacio
 *
 * Fix:
 *   - respectCacheHeaders = false: Coil usa su cache aunque el server
 *     no lo permita explicitamente
 *   - DiskCache habilitado con tamaño concreto (Coil 2.x no lo hace
 *     automatico sin config explicita en algunas versiones)
 *   - MemoryCache con limite generoso para que volver entre pantallas
 *     sea instantaneo
 */
fun buildPacemDeusImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25) // hasta 25% de la RAM disponible
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("pacem_deus_image_cache"))
                .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                .build()
        }
        .respectCacheHeaders(false)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .build()
}
