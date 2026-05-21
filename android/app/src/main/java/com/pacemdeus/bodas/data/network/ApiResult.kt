package com.pacemdeus.bodas.data.network

// Resultado de cualquier llamada al backend. Las pantallas usan este
// tipo en su estado: var state by remember mutableStateOf<ApiResult<X>>(ApiResult.Idle)
// y hacen when(state) is Success -> ... is Error -> ... para renderizar.

sealed class ApiResult<out T> {

    // Estado inicial. La pantalla aun no ha invocado la llamada.
    object Idle : ApiResult<Nothing>()

    // En curso. La pantalla debe mostrar un loader.
    object Loading : ApiResult<Nothing>()

    // Exito. data contiene la respuesta del backend ya parseada.
    data class Success<T>(val data: T) : ApiResult<T>()

    // Fallo. message es legible para el usuario, statusCode opcional
    // para casos donde la pantalla quiera comportarse distinto segun
    // el codigo (401 -> redirigir a login, 403 -> mostrar permisos, etc.).
    data class Error(val message: String, val statusCode: Int? = null) : ApiResult<Nothing>()
}
