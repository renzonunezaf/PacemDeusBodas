package com.pacemdeus.bodas.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.DemoData
import com.pacemdeus.bodas.data.UserRole
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.ui.components.Brown
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.Gold
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.Sand

// Pantalla de inicio de sesion. Valida las credenciales contra la lista
// hardcoded de usuarios demo (DemoData.users). Si coinciden, construye
// la UserSession con el perfil correspondiente al rol y la pasa hacia
// arriba mediante onAuthenticated.

@Composable
fun LoginScreen(
    onAuthenticated: (UserSession) -> Unit = {},
    onGoToRegister: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    /**
     * Busca el usuario por email + password. Si lo encuentra, arma la
     * UserSession completando con su perfil de couple/planner y la id
     * de la boda activa cuando corresponde.
     */
    fun attemptLogin() {
        val match = DemoData.users.firstOrNull {
            it.email.equals(email.trim(), ignoreCase = true) && it.password == password
        }
        if (match == null) {
            errorMessage = "Credenciales invalidas"
            return
        }
        val coupleProfile = DemoData.couples.firstOrNull { it.userId == match.id }
        val plannerProfile = DemoData.planners.firstOrNull { it.userId == match.id }
        val weddingId = when (match.role) {
            UserRole.COUPLE -> {
                val cId = coupleProfile?.id
                DemoData.initialWeddings.firstOrNull { it.coupleId == cId }?.id
            }
            else -> null
        }
        onAuthenticated(
            UserSession(
                user = match,
                coupleProfile = coupleProfile,
                plannerProfile = plannerProfile,
                weddingId = weddingId
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 60.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Branding ──────────────────────────────────
            Box(
                modifier = Modifier.size(72.dp).background(Gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Church,
                    contentDescription = null,
                    tint = Cream,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Pacem Deus Bodas",
                color = Brown,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Inicia sesion para continuar",
                color = Sand,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(36.dp))

            // ─── Formulario ────────────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Correo electronico") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = goldTextFieldColors()
            )
            
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Contraseña") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = goldTextFieldColors()
            )

            errorMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(28.dp))

            GoldButton(
                text = "Iniciar sesion",
                enabled = email.isNotBlank() && password.isNotBlank(),
                onClick = { attemptLogin() }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "¿No tienes cuenta?",
                color = Sand,
                fontSize = 13.sp
            )
            TextButton(onClick = onGoToRegister) {
                Text("Crear cuenta nueva", color = Gold, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(20.dp))

            // ─── Hint para evaluador ───────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Cream),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "CUENTAS DEMO",
                        color = Gold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("novia1@correo.com  -  Pareja",     color = Brown, fontSize = 12.sp)
                    Text("renzonunez.af@gmail.com  -  Admin", color = Brown, fontSize = 12.sp)
                    Text("wedding1@correo.com  -  Planner",   color = Brown, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Contraseña: PacemDeus2026!", color = Sand, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Colores en tono dorado para los OutlinedTextField del flujo auth. */
@Composable
fun goldTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold,
    unfocusedBorderColor = Sand,
    focusedLabelColor = Gold,
    cursorColor = Gold
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    MaterialTheme { LoginScreen() }
}
