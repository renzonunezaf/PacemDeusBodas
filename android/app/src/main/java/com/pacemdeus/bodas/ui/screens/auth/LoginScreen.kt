package com.pacemdeus.bodas.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.R
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Danger
import com.pacemdeus.bodas.ui.theme.Divider
import com.pacemdeus.bodas.ui.theme.EdwardianScript
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand

// Pantalla de inicio de sesion. Reescrita en v06 con imagen de hero
// arriba y panel inferior con bordes superiores redondeados que se
// solapa sobre la imagen — sigue el mockup que aprobo Renzo.
//
// Estructura:
//   - Box raiz fillMaxSize con Cream de fondo
//   - Column scrolleable adentro:
//     1. Image hero (height ~260dp, ContentScale.Crop)
//     2. Box panel inferior (offset negativo para solaparse, Cream con
//        topStart/topEnd RoundedCorners)
//     3. Icono Church circular centrado, offset negativo para solaparse
//        al borde superior del panel
//     4. Titulos: Coro Pacem Deus / Bodas / lema italic
//     5. Card "Bienvenido" con descripcion + form + boton + link registro
//     6. Footer con 4 audiencias separadas por bullets

@Composable
fun LoginScreen(
    onAuthenticated: (UserSession) -> Unit = {},
    onGoToRegister: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun attemptLogin() {
        val emailTrim = email.trim()
        if (emailTrim.isBlank() || password.isBlank()) {
            errorMessage = "Ingresa tu correo y contrasena."
            return
        }

        isLoading = true
        errorMessage = null

        apiClient.login(emailTrim, password) { result ->
            isLoading = false
            when (result) {
                is ApiResult.Success -> {
                    // Si el SDK de Firebase ya genero un FCM token antes
                    // del login (caso comun: app instalada hace rato), lo
                    // registramos ahora que tenemos JWT valido. Best
                    // effort: si falla no bloqueamos el login.
                    val session = com.pacemdeus.bodas.data.session.SessionManager.get(context)
                    val fcmToken = session.getFcmToken()
                    if (!fcmToken.isNullOrBlank()) {
                        apiClient.registerFcmToken(fcmToken) {}
                    }
                    onAuthenticated(result.data)
                }
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Cream)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ─── HERO IMAGE ─────────────────────────────────────
            Image(
                painter = painterResource(id = R.drawable.login_hero),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentScale = ContentScale.Crop
            )

            // ─── PANEL INFERIOR (se solapa con la imagen) ──────
            // offset negativo + bordes superiores redondeados crea el
            // efecto de panel que sube y oculta el borde duro de la imagen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-28).dp)
                    .background(
                        Cream,
                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icono iglesia que se solapa al borde superior
                    Box(
                        modifier = Modifier
                            .offset(y = (-44).dp)
                            .size(72.dp)
                            .background(Gold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Church,
                            contentDescription = null,
                            tint = Cream,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    // Compensamos el offset del icono para que no quede hueco
                    Spacer(Modifier.height((-28).dp))

                    Text(
                        "Coro Pacem Deus",
                        color = Brown,
                        fontFamily = EdwardianScript,
                        fontSize = 40.sp,
                        lineHeight = 44.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Bodas",
                        color = Gold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Cantamos al Amor de los Amores",
                        color = Sand,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    // ─── CARD BIENVENIDO ──────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(18.dp))
                            .padding(20.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Bienvenido",
                                color = Brown,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Gestiona tu ceremonia, repertorio y comunicacion con el coro desde un solo lugar.",
                                color = Sand,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(20.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Correo electronico") },
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Gold,
                                    unfocusedBorderColor = Sand,
                                    focusedLabelColor = Gold
                                )
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Contrasena") },
                                singleLine = true,
                                enabled = !isLoading,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Gold,
                                    unfocusedBorderColor = Sand,
                                    focusedLabelColor = Gold
                                )
                            )

                            if (errorMessage != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    color = Danger,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            GoldButton(
                                text = if (isLoading) "Iniciando sesion..." else "Iniciar sesion",
                                enabled = !isLoading,
                                onClick = { attemptLogin() }
                            )

                            if (isLoading) {
                                Spacer(Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp))
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Divisor sutil
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Divider)
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Primera vez aqui?",
                                    color = Sand,
                                    fontSize = 13.sp
                                )
                                TextButton(
                                    onClick = onGoToRegister,
                                    enabled = !isLoading
                                ) {
                                    Text(
                                        "Crea tu cuenta",
                                        color = Gold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // ─── FOOTER: audiencias ─────────────────
                    // No es clickeable, solo informativo. Comunica que la
                    // app sirve a 4 audiencias distintas.
                    AudiencesFooter()

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Footer informativo con las 4 audiencias de la app, separadas por
 * bullets. No es interactivo, solo comunica el alcance.
 */
@Composable
private fun AudiencesFooter() {
    val audiencias = listOf(
        "Para novios",
        "Wedding planners",
        "Coordinadores",
        "Musicos"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        audiencias.forEachIndexed { idx, label ->
            Text(
                label,
                color = Sand,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            if (idx < audiencias.size - 1) {
                Spacer(Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .background(GoldSoft, CircleShape)
                )
                Spacer(Modifier.size(6.dp))
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    MaterialTheme {
        LoginScreen()
    }
}
