package com.pacemdeus.bodas.ui.screens.couple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.data.session.SessionManager
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.Sand

// Vista informativa del contrato. Carga la boda del backend e imita el
// look de un contrato impreso con los datos formales del evento, el
// desglose de precios y los espacios para firmas.
//
// Para el couple, los datos personales (nombres, DNIs, telefono) se
// toman de la sesion local. Para admin/planner que vea esta pantalla
// en un sprint futuro, se cargaran datos del usuario propietario del
// contrato cuando el backend exponga ese detalle.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractScreen(
    weddingId: String,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }
    val session = remember { SessionManager.get(context).loadSession() }

    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(weddingId) {
        isLoading = true
        apiClient.getBoda(weddingId) { result ->
            isLoading = false
            when (result) {
                is ApiResult.Success -> wedding = result.data
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    val couple = session?.coupleProfile
    val plannerName = session?.plannerProfile?.name

    Scaffold(
        topBar = { PacemTopBar(title = "Contrato", onBack = onBack) },
        containerColor = Cream
    ) { padding ->

        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        val currentWedding = wedding
        if (currentWedding == null) {
            EmptyState(errorMessage ?: "No se encontro el contrato")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Cabecera estilo formal
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "CONTRATO DE SERVICIO MUSICAL",
                        color = Brown,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text("Coro Pacem Deus", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Lima, Peru", color = Sand, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            Divider(color = Gold, thickness = 1.dp)
            Spacer(Modifier.height(20.dp))

            // Datos de la pareja (vienen de la sesion local del couple)
            if (couple != null) {
                SectionLabel("Contratantes")
                PacemCard {
                    LineRow("Novio",  couple.groomName)
                    LineRow("DNI",    couple.groomDni)
                    Spacer(Modifier.height(6.dp))
                    LineRow("Novia",  couple.brideName)
                    LineRow("DNI",    couple.brideDni)
                    Spacer(Modifier.height(6.dp))
                    LineRow("Telefono", couple.phone)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Detalle del evento
            SectionLabel("Detalle del evento")
            PacemCard {
                LineRow("Fecha",     currentWedding.weddingDate)
                LineRow("Hora",      currentWedding.weddingTime)
                LineRow("Lugar",     currentWedding.venueName)
                LineRow("Direccion", currentWedding.venueAddress)
                if (plannerName != null) {
                    LineRow("Wedding Planner", plannerName)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Servicios contratados (desglose alto nivel)
            SectionLabel("Servicios contratados")
            PacemCard {
                Row {
                    Text("Servicio base del coro", color = Brown, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        "S/. ${"%.2f".format(currentWedding.basePrice)}",
                        color = Brown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("Instrumentos adicionales", color = Brown, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        "S/. ${"%.2f".format(currentWedding.instrumentsPrice)}",
                        color = Brown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Divider(color = Sand)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        "TOTAL",
                        color = Gold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "S/. ${"%.2f".format(currentWedding.totalPrice)}",
                        color = Gold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "El presente contrato establece el acuerdo entre los contratantes y el Coro " +
                "Pacem Deus para la prestacion del servicio musical descrito en la fecha y " +
                "lugar indicados. El valor total incluye la asistencia del coro durante la " +
                "ceremonia, la preparacion del repertorio acordado y los instrumentos " +
                "musicales seleccionados.",
                color = Brown,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SignatureBox("Por los novios", modifier = Modifier.weight(1f))
                SignatureBox("Por el coro",    modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            // Boton para descargar y compartir el contrato como PDF
            // profesional generado por el backend (HU-08).
            var isDownloading by remember { mutableStateOf(false) }
            var downloadError by remember { mutableStateOf<String?>(null) }

            OutlineGoldButton(
                text = if (isDownloading) "Generando contrato..." else "Descargar y compartir contrato",
                onClick = {
                    if (isDownloading) return@OutlineGoldButton
                    isDownloading = true
                    downloadError = null
                    apiClient.getContratoPdf(currentWedding.id) { result ->
                        isDownloading = false
                        when (result) {
                            is ApiResult.Success -> {
                                compartirContratoPdf(
                                    context = context,
                                    filename = result.data.filename,
                                    bytes = result.data.bytes
                                )
                            }
                            is ApiResult.Error -> {
                                downloadError = result.message
                            }
                            else -> {}
                        }
                    }
                }
            )

            if (downloadError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    downloadError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Guarda el PDF en cache y dispara Intent.ACTION_SEND para que el usuario
 * elija app (WhatsApp, Gmail, Drive, etc.). Usa FileProvider para no
 * exponer paths internos a otras apps.
 */
private fun compartirContratoPdf(
    context: android.content.Context,
    filename: String,
    bytes: ByteArray
) {
    try {
        // Guardar en cache/contratos para que FileProvider pueda exportarlo.
        val cacheDir = java.io.File(context.cacheDir, "contratos").apply { mkdirs() }
        val file = java.io.File(cacheDir, filename)
        file.outputStream().use { it.write(bytes) }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Contrato Coro Pacem Deus")
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "Adjunto el contrato de servicio musical con el Coro Pacem Deus."
            )
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            android.content.Intent.createChooser(sendIntent, "Compartir contrato")
        )
    } catch (e: Exception) {
        android.util.Log.e("ContractScreen", "Error al compartir PDF", e)
    }
}

@Composable
private fun LineRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            color = Sand,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            color = Brown,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun SignatureBox(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Divider(color = Brown, thickness = 1.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Sand, fontSize = 11.sp)
    }
}
