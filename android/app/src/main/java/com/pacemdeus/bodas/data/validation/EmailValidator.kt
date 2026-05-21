package com.pacemdeus.bodas.data.validation

import android.util.Patterns

// Validacion de formato de email. Usamos el patron oficial de Android,
// que es el mismo que usa el campo de tipo "email" en formularios web
// y el que el equipo Android Studio recomienda.

object EmailValidator {

    /**
     * Devuelve true si el texto pasado tiene formato valido de email.
     * Reglas: maximo 254 caracteres totales, sin espacios, debe matchear
     * el patron RFC compatible de Android (uno o mas locales, arroba,
     * dominio con un punto y TLD de al menos 2 letras).
     */
    fun isValid(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.length > 254) return false
        if (trimmed.contains(" ")) return false
        return Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
    }

    /**
     * Devuelve un mensaje de error legible si el email es invalido, o
     * null si es valido. Pensado para usarse directamente en supportingText
     * de los OutlinedTextField.
     */
    fun errorMessage(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isBlank() -> "Ingresa tu correo"
            !isValid(trimmed) -> "El correo no tiene un formato valido"
            else -> null
        }
    }
}
