package com.pacemdeus.bodas.ui.couple

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Contrato (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Muestra los datos del contrato del evento obtenidos del backend.
// Permite firmar (placeholder) y descargar PDF (placeholder).
// ═══════════════════════════════════════════════════════════════

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.data.api.ApiClient
import com.pacemdeus.bodas.data.api.models.ContractData
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.launch

class ContractActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weddingId = intent.getStringExtra("weddingId") ?: ""
        setContent {
            PacemDeusTheme {
                ContractScreen(
                    weddingId = weddingId,
                    onBack = { finish() },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractScreen(
    weddingId: String,
    onBack: () -> Unit,
    showToast: (String) -> Unit
) {
    var contract by remember { mutableStateOf<ContractData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var signed by remember { mutableStateOf(false) }
    var showSignConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(weddingId) {
        scope.launch {
            try {
                val res = ApiClient.service.getContract(weddingId)
                if (res.isSuccessful) contract = res.body()
                else showToast("No se pudo cargar el contrato")
            } catch (_: Exception) {
                showToast("Error de conexión")
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar("Contrato", onBack = onBack) }
    ) { padding ->
        when {
            loading -> LoadingIndicator()
            contract == null -> EmptyState("No se pudo cargar el contrato")
            else -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    ContractBody(contract!!, signed)

                    Spacer(Modifier.height(24.dp))

                    // Acciones (placeholders)
                    if (!signed) {
                        GoldButton("Firmar contrato") { showSignConfirm = true }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlineGoldButton("Descargar PDF") {
                        showToast("PDF descargado a Documentos")
                    }
                }
            }
        }
    }

    if (showSignConfirm) {
        ConfirmDialog(
            title = "Firmar contrato",
            message = "Al firmar estás aceptando los términos del servicio. " +
                    "Tu firma quedará registrada con fecha y hora.",
            confirmLabel = "Firmar",
            onConfirm = {
                showSignConfirm = false
                signed = true
                showToast("Contrato firmado exitosamente")
            },
            onDismiss = { showSignConfirm = false }
        )
    }
}

/** Cuerpo visual del contrato — estructura de documento formal */
@Composable
private fun ContractBody(c: ContractData, signed: Boolean) {
    // Encabezado del contrato
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("♱", fontSize = 32.sp, color = Gold)
        Spacer(Modifier.height(8.dp))
        Text(
            "CORO PACEM DEUS",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 2.sp,
            color = Gold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Contrato de servicios musicales",
            style = MaterialTheme.typography.titleMedium,
            color = Brown
        )
    }
    Spacer(Modifier.height(24.dp))

    PacemCard {
        SectionLabel("Contratantes")
        ContractLine("Novios", "${c.groomName} & ${c.brideName}")
        c.groomDni?.let { ContractLine("DNI novio", it) }
        c.brideDni?.let { ContractLine("DNI novia", it) }
        c.phone?.let { ContractLine("Teléfono", it) }
    }

    Spacer(Modifier.height(12.dp))

    PacemCard {
        SectionLabel("Detalle del evento")
        ContractLine("Fecha", c.weddingDate)
        ContractLine("Hora", c.weddingTime)
        ContractLine("Lugar", c.venueName)
        ContractLine("Dirección", c.venueAddress)
    }

    Spacer(Modifier.height(12.dp))

    if (c.instruments.isNotEmpty()) {
        PacemCard {
            SectionLabel("Instrumentos contratados")
            c.instruments.forEach {
                ContractLine(it.name, "S/. ${"%.2f".format(it.priceLima)}")
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    PacemCard {
        SectionLabel("Inversión")
        ContractLine("Base", "S/. ${"%.2f".format(c.basePrice)}")
        ContractLine("Instrumentos", "S/. ${"%.2f".format(c.instrumentsPrice)}")
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 8.dp))
        Row {
            Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "S/. ${"%.2f".format(c.totalPrice)}",
                style = MaterialTheme.typography.titleMedium,
                color = Gold,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    PacemCard {
        SectionLabel("Términos")
        Text(
            "1. El coro se compromete a interpretar los cantos seleccionados por los novios " +
            "en los momentos litúrgicos correspondientes.\n\n" +
            "2. Los novios deben confirmar con al menos 15 días de anticipación cualquier " +
            "modificación al setlist.\n\n" +
            "3. El pago se realiza en dos partes: 50% al firmar el contrato y 50% antes de " +
            "la ceremonia.\n\n" +
            "4. En caso de cancelación con menos de 7 días de anticipación se retendrá el 50%.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrownLight,
            lineHeight = 20.sp
        )
    }

    if (signed) {
        Spacer(Modifier.height(16.dp))
        Surface(
            color = StatusApprovedBg,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓", fontSize = 24.sp, color = Green)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Contrato firmado", fontWeight = FontWeight.SemiBold, color = Green)
                    Text(
                        "Tu firma quedó registrada",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrownLight
                    )
                }
            }
        }
    }
}

/** Línea de clave-valor usada en cada card del contrato */
@Composable
private fun ContractLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Sand,
            modifier = Modifier.width(130.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Brown)
    }
}
