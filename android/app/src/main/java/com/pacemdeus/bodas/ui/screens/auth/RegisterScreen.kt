package com.pacemdeus.bodas.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.UserRole
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.validation.EmailValidator
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.theme.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.goldTextFieldColors
import androidx.compose.ui.platform.LocalContext
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult

// Pantalla de registro. Primero el usuario elige el rol (Pareja o Wedding
// Planner) y luego completa los campos correspondientes. Al confirmar,
// se construye una UserSession in-memory y se notifica hacia arriba.
// No persiste nada entre sesiones.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBack: () -> Unit = {},
    onRegistered: (UserSession) -> Unit = {}
) {
    var role by remember { mutableStateOf(UserRole.COUPLE) }

    // Campos comunes
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    // Campos del couple
    var groomName by remember { mutableStateOf("") }
    var brideName by remember { mutableStateOf("") }
    var groomDni by remember { mutableStateOf("") }
    var brideDni by remember { mutableStateOf("") }
    var couplePhone by remember { mutableStateOf("") }

    // Campos del planner
    var plannerName by remember { mutableStateOf("") }
    var plannerCompany by remember { mutableStateOf("") }
    var plannerPhone by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    /** Valida campos requeridos segun el rol y dispara el alta en el backend. */
    fun submit() {
        // Email: usa el validador centralizado (formato RFC compatible Android)
        val emailErr = EmailValidator.errorMessage(email)
        if (emailErr != null) {
            errorMessage = emailErr
            return
        }
        if (password.length < 8) {
            errorMessage = "La contrasena debe tener al menos 8 caracteres"
            return
        }
        if (password != passwordConfirm) {
            errorMessage = "Las contrasenas no coinciden"
            return
        }
        errorMessage = null
        isLoading = true

        // El callback compartido: si la llamada al backend tiene exito,
        // se notifica hacia arriba con la UserSession ya armada por el
        // ApiClient (que ademas guardo el JWT en SharedPreferences).
        val onApiResult: (ApiResult<UserSession>) -> Unit = { result ->
            isLoading = false
            when (result) {
                is ApiResult.Success -> onRegistered(result.data)
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }

        when (role) {
            UserRole.COUPLE -> {
                if (groomName.isBlank() || brideName.isBlank() ||
                    groomDni.isBlank() || brideDni.isBlank() || couplePhone.isBlank()
                ) {
                    isLoading = false
                    errorMessage = "Completa los datos del novio y la novia"
                    return
                }
                // Validamos que ambos DNI sean exactamente 8 digitos numericos
                // (formato estandar peruano). El backend lo vuelve a validar.
                val dniRegex = Regex("^\\d{8}$")
                if (!dniRegex.matches(groomDni.trim()) || !dniRegex.matches(brideDni.trim())) {
                    isLoading = false
                    errorMessage = "El DNI debe tener exactamente 8 digitos numericos"
                    return
                }
                apiClient.registerCouple(
                    email = email.trim(),
                    password = password,
                    groomName = groomName.trim(),
                    brideName = brideName.trim(),
                    groomDni = groomDni.trim(),
                    brideDni = brideDni.trim(),
                    phone = couplePhone.trim(),
                    callback = onApiResult
                )
            }
            UserRole.WEDDING_PLANNER -> {
                if (plannerName.isBlank() || plannerPhone.isBlank()) {
                    isLoading = false
                    errorMessage = "Nombre y telefono son obligatorios"
                    return
                }
                apiClient.registerPlanner(
                    email = email.trim(),
                    password = password,
                    name = plannerName.trim(),
                    company = plannerCompany.trim().ifBlank { null },
                    phone = plannerPhone.trim(),
                    callback = onApiResult
                )
            }
            UserRole.ADMIN -> {
                isLoading = false
                errorMessage = "El registro de admins no esta disponible"
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Crear cuenta", onBack = onBack) },
        containerColor = Cream
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ─── Selector de rol ───────────────────────────
            SectionLabel("¿Como te registras?")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoleCard(
                    icon = Icons.Default.Favorite,
                    title = "Soy novio/a",
                    subtitle = "Quiero contratar al coro",
                    selected = role == UserRole.COUPLE,
                    modifier = Modifier.weight(1f),
                    onClick = { role = UserRole.COUPLE }
                )
                RoleCard(
                    icon = Icons.Default.Star,
                    title = "Wedding Planner",
                    subtitle = "Coordino bodas",
                    selected = role == UserRole.WEDDING_PLANNER,
                    modifier = Modifier.weight(1f),
                    onClick = { role = UserRole.WEDDING_PLANNER }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ─── Datos comunes ─────────────────────────────
            SectionLabel("Datos de la cuenta")

            // El isError solo se enciende DESPUES de que el usuario haya
            // tocado el campo (escrito al menos un caracter), para no
            // mostrar el campo en rojo al inicio.
            val emailHasContent = email.isNotEmpty()
            val emailIsInvalid = emailHasContent && !EmailValidator.isValid(email)

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Correo electronico") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = goldTextFieldColors(),
                isError = emailIsInvalid,
                supportingText = if (emailIsInvalid) {
                    { Text("Formato de correo invalido", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                } else null
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Contraseña (min. 8)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = goldTextFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            // Confirmacion para evitar typos. La validacion de coincidencia
            // se hace en submit() antes de llamar al backend.
            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it; errorMessage = null },
                label = { Text("Confirmar contraseña") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = goldTextFieldColors(),
                isError = passwordConfirm.isNotEmpty() && passwordConfirm != password,
                supportingText = {
                    if (passwordConfirm.isNotEmpty() && passwordConfirm != password) {
                        Text(
                            "Las contrasenas no coinciden",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            // ─── Datos especificos por rol ─────────────────
            when (role) {
                UserRole.COUPLE -> CoupleFields(
                    groomName = groomName, onGroomNameChange = { groomName = it },
                    brideName = brideName, onBrideNameChange = { brideName = it },
                    groomDni = groomDni,   onGroomDniChange = { groomDni = it },
                    brideDni = brideDni,   onBrideDniChange = { brideDni = it },
                    phone = couplePhone,   onPhoneChange = { couplePhone = it }
                )
                UserRole.WEDDING_PLANNER -> PlannerFields(
                    name = plannerName,        onNameChange = { plannerName = it },
                    company = plannerCompany,  onCompanyChange = { plannerCompany = it },
                    phone = plannerPhone,      onPhoneChange = { plannerPhone = it }
                )
                else -> Unit
            }

            errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(28.dp))

            GoldButton(text = "Crear cuenta", onClick = { submit() })

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RoleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Gold else Color(0xFFE0D9C8)
    val bgColor = if (selected) GoldSoft else Color.White
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(14.dp))
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(40.dp).background(
                    if (selected) Gold else Color(0xFFE0D9C8),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) Cream else Sand,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(title, color = Brown, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Sand, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CoupleFields(
    groomName: String, onGroomNameChange: (String) -> Unit,
    brideName: String, onBrideNameChange: (String) -> Unit,
    groomDni: String,  onGroomDniChange: (String) -> Unit,
    brideDni: String,  onBrideDniChange: (String) -> Unit,
    phone: String,     onPhoneChange: (String) -> Unit
) {
    SectionLabel("Datos de los novios")
    OutlinedTextField(
        value = groomName, onValueChange = onGroomNameChange,
        label = { Text("Nombre del novio") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = goldTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = brideName, onValueChange = onBrideNameChange,
        label = { Text("Nombre de la novia") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = goldTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = groomDni, onValueChange = onGroomDniChange,
            label = { Text("DNI novio") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = goldTextFieldColors()
        )
        OutlinedTextField(
            value = brideDni, onValueChange = onBrideDniChange,
            label = { Text("DNI novia") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = goldTextFieldColors()
        )
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = phone, onValueChange = onPhoneChange,
        label = { Text("Telefono de contacto") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        colors = goldTextFieldColors()
    )
}

@Composable
private fun PlannerFields(
    name: String,    onNameChange: (String) -> Unit,
    company: String, onCompanyChange: (String) -> Unit,
    phone: String,   onPhoneChange: (String) -> Unit
) {
    SectionLabel("Datos del planner")
    OutlinedTextField(
        value = name, onValueChange = onNameChange,
        label = { Text("Nombre completo") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = goldTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = company, onValueChange = onCompanyChange,
        label = { Text("Empresa (opcional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = goldTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = phone, onValueChange = onPhoneChange,
        label = { Text("Telefono de contacto") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        colors = goldTextFieldColors()
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RegisterScreenPreview() {
    MaterialTheme { RegisterScreen() }
}
