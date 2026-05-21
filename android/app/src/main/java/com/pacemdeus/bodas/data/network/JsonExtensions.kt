package com.pacemdeus.bodas.data.network

import org.json.JSONObject

// Helpers para parsear JSON del backend. Centralizan el workaround del
// bug clasico de Android donde JSONObject.optString, cuando el valor
// es JSON null, devuelve el string literal "null" (cuatro letras) en
// lugar de un null real de Kotlin.

/**
 * Lee un string opcional del JSON tratando como ausencia todos los
 * casos problematicos: clave inexistente, JSON null, o string "null"
 * literal. Devuelve null real, listo para usar con Elvis (?:) o
 * checkeos con !isNullOrBlank().
 */
fun JSONObject.safeOptString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return value.takeIf { it.isNotBlank() && it != "null" }
}
