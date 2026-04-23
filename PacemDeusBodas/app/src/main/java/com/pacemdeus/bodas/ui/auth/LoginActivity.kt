package com.pacemdeus.bodas.ui.auth

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Login (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.LoginRequest
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.coordinator.CoordinatorHomeActivity
import com.pacemdeus.bodas.ui.couple.CoupleHomeActivity
import com.pacemdeus.bodas.ui.planner.PlannerDashboardActivity
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                LoginScreen(
                    onLoginSuccess = { role -> navigateByRole(role) },
                    onRegister = { startActivity(Intent(this, RegisterActivity::class.java)) },
                    showError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    private fun navigateByRole(role: String) {
        val intent = when (role) {
            "ADMIN" -> Intent(this, CoordinatorHomeActivity::class.java)
            "COUPLE" -> Intent(this, CoupleHomeActivity::class.java)
            "WEDDING_PLANNER" -> Intent(this, PlannerDashboardActivity::class.java)
            else -> return
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent); finish()
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit, onRegister: () -> Unit, showError: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Text("✝", fontSize = 48.sp, color = Gold)
        Spacer(Modifier.height(12.dp))
        Text("Pacem Deus Bodas", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Brown)
        Text("Cantemos al Amor de los Amores", fontSize = 12.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Sand)
        Spacer(Modifier.height(40.dp))

        // Campos
        PacemTextField(value = email, onValueChange = { email = it }, label = "Email", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        PacemTextField(value = password, onValueChange = { password = it }, label = "Contraseña", isPassword = true)
        Spacer(Modifier.height(24.dp))

        // Botón login
        if (loading) {
            CircularProgressIndicator(color = Gold)
        } else {
            GoldButton(text = "Iniciar sesión", onClick = {
                if (email.isBlank() || password.isBlank()) { showError("Completa todos los campos"); return@GoldButton }
                loading = true
                scope.launch {
                    try {
                        val res = ApiClient.service.login(LoginRequest(email.trim(), password))
                        if (res.isSuccessful && res.body() != null) {
                            val auth = res.body()!!
                            SessionManager.saveToken(auth.token)
                            val user = auth.user
                            val displayName = when (user.role) {
                                "COUPLE" -> "${user.couple?.groomName ?: ""} & ${user.couple?.brideName ?: ""}"
                                "WEDDING_PLANNER" -> user.weddingPlanner?.name ?: user.email
                                else -> user.email
                            }
                            SessionManager.saveUserData(user.id, user.email, user.role, displayName)
                            user.couple?.weddings?.firstOrNull()?.let { SessionManager.saveWeddingId(it.id) }
                            onLoginSuccess(user.role)
                        } else {
                            showError("Credenciales inválidas")
                        }
                    } catch (_: Exception) {
                        showError("Error de conexión")
                    } finally { loading = false }
                }
            })
        }

        Spacer(Modifier.height(20.dp))
        Row {
            Text("¿No tienes cuenta? ", fontSize = 14.sp, color = Sand)
            Text("Crear una", fontSize = 14.sp, color = Gold, modifier = Modifier.clickable { onRegister() })
        }
    }
}
