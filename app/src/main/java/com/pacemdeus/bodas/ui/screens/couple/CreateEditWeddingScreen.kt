package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.WeddingStatus
import com.pacemdeus.bodas.ui.components.Cream
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.Sand
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.screens.auth.goldTextFieldColors

// Formulario para crear un evento nuevo o editar uno existente.
// Si recibe `existing` precarga los campos; si no, deja vacios y se
// crea una boda nueva al guardar.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditWeddingScreen(
    session: UserSession,
    existing: Wedding?,
    onBack: () -> Unit = {},
    onSave: (Wedding) -> Unit = {}
) {
    val isEdit = existing != null

    var date by remember { mutableStateOf(existing?.weddingDate ?: "") }
    var time by remember { mutableStateOf(existing?.weddingTime ?: "") }
    var venueName by remember { mutableStateOf(existing?.venueName ?: "") }
    var venueAddress by remember { mutableStateOf(existing?.venueAddress ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun validateAndSave() {
        if (date.isBlank() || time.isBlank() || venueName.isBlank() || venueAddress.isBlank()) {
            error = "Completa todos los campos"
            return
        }
        // Validacion basica de formato de fecha y hora
        if (!date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            error = "La fecha debe tener formato AAAA-MM-DD"
            return
        }
        if (!time.matches(Regex("^\\d{2}:\\d{2}$"))) {
            error = "La hora debe tener formato HH:MM"
            return
        }

        val coupleId = session.coupleProfile?.id ?: return
        val saved = existing?.copy(
            weddingDate = date,
            weddingTime = time,
            venueName = venueName.trim(),
            venueAddress = venueAddress.trim()
        ) ?: Wedding(
            id = "wed-${System.currentTimeMillis()}",
            coupleId = coupleId,
            plannerId = null,
            weddingDate = date,
            weddingTime = time,
            venueName = venueName.trim(),
            venueAddress = venueAddress.trim(),
            venueLat = null,
            venueLng = null,
            venuePhotoTaken = false,
            status = WeddingStatus.DRAFT,
            basePrice = 1800.0,
            instrumentsPrice = 0.0,
            notes = null
        )
        onSave(saved)
    }

    Scaffold(
        topBar = {
            PacemTopBar(
                title = if (isEdit) "Editar evento" else "Crear evento",
                onBack = onBack
            )
        },
        containerColor = Cream
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionLabel("Fecha y hora")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it; error = null },
                    label = { Text("AAAA-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = goldTextFieldColors()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it; error = null },
                    label = { Text("HH:MM") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = goldTextFieldColors()
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Lugar de la ceremonia")
            OutlinedTextField(
                value = venueName,
                onValueChange = { venueName = it; error = null },
                label = { Text("Nombre del local") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = goldTextFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = venueAddress,
                onValueChange = { venueAddress = it; error = null },
                label = { Text("Direccion completa") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = goldTextFieldColors()
            )

            error?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "El precio base de S/. 1800 incluye un cantante y un pianista por una hora. " +
                "Puedes contratar instrumentos adicionales desde el menu de Instrumentos.",
                color = Sand,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(28.dp))
            GoldButton(
                text = if (isEdit) "Guardar cambios" else "Crear evento",
                onClick = { validateAndSave() }
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CreateEditWeddingScreenPreview() {
    MaterialTheme {
        CreateEditWeddingScreen(
            session = com.pacemdeus.bodas.data.UserSession(
                user = com.pacemdeus.bodas.data.DemoData.users[5],
                coupleProfile = com.pacemdeus.bodas.data.DemoData.couples[0],
                plannerProfile = null,
                weddingId = "wed-1"
            ),
            existing = com.pacemdeus.bodas.data.DemoData.initialWeddings[0]
        )
    }
}
