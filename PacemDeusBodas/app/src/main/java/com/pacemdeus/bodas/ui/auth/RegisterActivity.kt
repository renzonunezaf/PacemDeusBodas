package com.pacemdeus.bodas.ui.auth

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Registro de Usuarios (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.RegisterCoupleRequest
import com.pacemdeus.bodas.data.api.models.RegisterPlannerRequest
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.couple.CoupleHomeActivity
import com.pacemdeus.bodas.ui.planner.PlannerDashboardActivity
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                RegisterScreen(
                    onSuccess = { role ->
                        val intent = when (role) {
                            "COUPLE" -> Intent(this, CoupleHomeActivity::class.java)
                            else -> Intent(this, PlannerDashboardActivity::class.java)
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent); finish()
                    },
                    onBack = { finish() },
                    showError = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onSuccess: (String) -> Unit, onBack: () -> Unit, showError: (String) -> Unit) {
    var isCouple by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var groomName by remember { mutableStateOf("") }
    var brideName by remember { mutableStateOf("") }
    var groomDni by remember { mutableStateOf("") }
    var brideDni by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var plannerName by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { PacemTopBar("Crear cuenta", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Registro", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Selecciona el tipo de cuenta y completa tus datos.",
                style = MaterialTheme.typography.bodyMedium,
                color = Sand
            )
            Spacer(Modifier.height(16.dp))

            // Selector de rol
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isCouple,
                    onClick = { isCouple = true },
                    label = { Text("Novio/a") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Gold20, selectedLabelColor = Gold
                    )
                )
                FilterChip(
                    selected = !isCouple,
                    onClick = { isCouple = false },
                    label = { Text("Wedding Planner") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Gold20, selectedLabelColor = Gold
                    )
                )
            }
            Spacer(Modifier.height(16.dp))

            PacemTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(8.dp))
            PacemTextField(password, { password = it }, "Contraseña", isPassword = true)
            Spacer(Modifier.height(8.dp))
            PacemTextField(phone, { phone = it }, "Teléfono", keyboardType = KeyboardType.Phone)
            Spacer(Modifier.height(8.dp))

            if (isCouple) {
                PacemTextField(groomName, { groomName = it }, "Nombre del novio")
                Spacer(Modifier.height(8.dp))
                PacemTextField(brideName, { brideName = it }, "Nombre de la novia")
                Spacer(Modifier.height(8.dp))
                PacemTextField(groomDni, { groomDni = it }, "DNI del novio")
                Spacer(Modifier.height(8.dp))
                PacemTextField(brideDni, { brideDni = it }, "DNI de la novia")
            } else {
                PacemTextField(plannerName, { plannerName = it }, "Nombre completo")
                Spacer(Modifier.height(8.dp))
                PacemTextField(company, { company = it }, "Empresa (opcional)")
            }

            Spacer(Modifier.height(24.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold)
                }
            } else {
                GoldButton("Crear cuenta") {
                    loading = true
                    scope.launch {
                        try {
                            val res = if (isCouple) {
                                ApiClient.service.registerCouple(
                                    RegisterCoupleRequest(
                                        email.trim(), password,
                                        groomName, brideName, groomDni, brideDni, phone
                                    )
                                )
                            } else {
                                ApiClient.service.registerPlanner(
                                    RegisterPlannerRequest(
                                        email.trim(), password,
                                        plannerName, company.ifBlank { null }, phone
                                    )
                                )
                            }
                            if (res.isSuccessful && res.body() != null) {
                                val auth = res.body()!!
                                SessionManager.saveToken(auth.token)
                                val user = auth.user
                                val displayName = if (isCouple) "$groomName & $brideName" else plannerName
                                SessionManager.saveUserData(user.id, user.email, user.role, displayName)
                                user.couple?.weddings?.firstOrNull()?.let {
                                    SessionManager.saveWeddingId(it.id)
                                }
                                onSuccess(user.role)
                            } else {
                                showError("Error al registrar")
                            }
                        } catch (_: Exception) {
                            showError("Error de conexión")
                        } finally {
                            loading = false
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("¿Ya tienes cuenta? ", fontSize = 14.sp, color = Sand)
                Text(
                    "Iniciar sesión",
                    fontSize = 14.sp, color = Gold,
                    modifier = Modifier.clickable { onBack() }
                )
            }
        }
    }
}
