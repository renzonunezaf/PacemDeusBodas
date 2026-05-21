package com.pacemdeus.bodas.data.network

// Configuracion centralizada del API. No hardcodeada en cada llamada
// para que cambiar el endpoint (ej. al apagar/encender el lab AWS y
// que cambie el id de API Gateway) sea un solo cambio aqui.

object ApiConfig {

    // URL del API Gateway desplegado en AWS Academy.
    // Patron: https://<api-id>.execute-api.<region>.amazonaws.com/<stage>
    const val BASE_URL = "https://57qk0t3z61.execute-api.us-east-1.amazonaws.com/v1"

    // Timeouts en milisegundos. El cold start de la Lambda puede
    // tardar 10-15 segundos la primera vez del dia, por eso damos
    // 30 segundos de margen.
    const val TIMEOUT_MS = 30_000

    // Cantidad de reintentos automaticos por error de red. Volley
    // los maneja internamente con DefaultRetryPolicy.
    const val MAX_RETRIES = 1

    // Headers comunes que mandan TODAS las requests.
    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_AUTHORIZATION = "Authorization"
    const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
    const val AUTH_PREFIX = "Bearer "
}
