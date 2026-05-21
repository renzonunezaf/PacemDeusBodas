package com.pacemdeus.bodas.ui.screens.planner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.core.net.toUri
import com.pacemdeus.bodas.data.Wedding
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.EmptyState
import com.pacemdeus.bodas.ui.components.GoldButtonWithIcon
import com.pacemdeus.bodas.ui.components.OutlineGoldButtonWithIcon
import com.pacemdeus.bodas.ui.components.PacemCard
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.components.SectionLabel
import com.pacemdeus.bodas.ui.components.StatusBadge
import com.pacemdeus.bodas.ui.util.openInMaps
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.Sand

// Telefono fijo del Coro Pacem Deus que el planner llama desde
// el detalle del evento (intent ACTION_DIAL). Es un dato de configuracion
// estable de la app, no depende del backend.
private const val CHOIR_PHONE = "+51989159777"

// Detalle de evento desde la perspectiva del wedding planner. Solo
// lectura. Carga la boda completa via apiClient.getBoda y el conteo
// del setlist via apiClient.listSetlist.
//
// Las dos acciones nativas son llamar al coro (Intent ACTION_DIAL con
// tel:) y abrir la ubicacion en Maps (Intent ACTION_VIEW con geo:).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDetailScreen(
    weddingId: String,
    onBack: () -> Unit = {},
    onOpenContract: () -> Unit = {},
    onOpenGallery: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }

    var wedding by remember { mutableStateOf<Wedding?>(null) }
    var setlistCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(weddingId) {
        isLoading = true
        apiClient.getBoda(weddingId) { result ->
            when (result) {
                is ApiResult.Success -> {
                    wedding = result.data
                    apiClient.listSetlist(weddingId) { sl ->
                        if (sl is ApiResult.Success) setlistCount = sl.data.size
                        isLoading = false
                    }
                }
                is ApiResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Detalle del evento", onBack = onBack) },
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
            EmptyState(errorMessage ?: "No se encontro el evento")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Cabecera pareja
            PacemCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentWedding.coupleLabel(),
                            color = Brown,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${currentWedding.weddingDate}  -  ${currentWedding.weddingTime}",
                            color = Sand,
                            fontSize = 12.sp
                        )
                    }
                    StatusBadge(currentWedding.status)
                }
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            PacemCard {
                SectionLabel("Lugar")
                Text(
                    currentWedding.venueName,
                    color = Brown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(currentWedding.venueAddress, color = Sand, fontSize = 12.sp)
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            PacemCard {
                SectionLabel("Ensamble musical")
                Text("$setlistCount canciones registradas", color = Brown, fontSize = 13.sp)
            }

            Spacer(Modifier.padding(vertical = 6.dp))

            PacemCard {
                SectionLabel("Inversion total")
                Text(
                    "S/. ${"%.2f".format(currentWedding.totalPrice)}",
                    color = Brown,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.padding(vertical = 8.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.padding(vertical = 16.dp))

            // Acciones nativas: llamar y abrir maps. Cada boton tiene su
            // icono para refuerzo visual de lo que hace.
            GoldButtonWithIcon(
                text = "Llamar al coro",
                icon = Icons.Default.Phone,
                onClick = {
                    // No usamos resolveActivity() porque en emuladores
                    // sin app de telefono devuelve null y el boton se
                    // siente "muerto". Mejor lanzar el Intent siempre y
                    // catchear ActivityNotFoundException si no hay app
                    // de llamadas instalada, mostrando el numero en un
                    // Toast para que el usuario lo marque manualmente.
                    try {
                        val intent = Intent(Intent.ACTION_DIAL, "tel:$CHOIR_PHONE".toUri())
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            "No se encontro app de llamadas. Telefono del coro: $CHOIR_PHONE",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            if (currentWedding.venueLat != null && currentWedding.venueLng != null) {
                Spacer(Modifier.padding(vertical = 4.dp))
                OutlineGoldButtonWithIcon(
                    text = "Abrir ubicacion en Maps",
                    icon = Icons.Default.LocationOn,
                    onClick = {
                        openInMaps(
                            context,
                            currentWedding.venueLat,
                            currentWedding.venueLng,
                            currentWedding.venueName
                        )
                    }
                )
            }

            Spacer(Modifier.padding(vertical = 4.dp))
            OutlineGoldButtonWithIcon(
                text = "Fotos del local",
                icon = Icons.Default.PhotoLibrary,
                onClick = onOpenGallery
            )

            Spacer(Modifier.padding(vertical = 4.dp))
            OutlineGoldButtonWithIcon(
                text = "Ver contrato",
                icon = Icons.AutoMirrored.Filled.Article,
                onClick = onOpenContract
            )

            Spacer(Modifier.padding(vertical = 12.dp))
        }
    }
}
