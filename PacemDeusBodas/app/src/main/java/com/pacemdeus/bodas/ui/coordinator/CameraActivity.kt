package com.pacemdeus.bodas.ui.coordinator

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Captura de Foto (Placeholder funcional)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════
// Placeholder del preview de cámara. Simula el viewfinder con un
// gradiente oscuro + retícula + botón de captura redondo típico.
// Al "capturar" muestra un confirm visual y vuelve atrás.
// La integración real con CameraX queda pendiente.
// ═══════════════════════════════════════════════════════════════

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Church
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.ui.components.*
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                CameraScreen(
                    onBack = { finish() },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(onBack: () -> Unit, showToast: (String) -> Unit) {
    var captured by remember { mutableStateOf(false) }
    var flashing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1612))) {
        // ─── Viewfinder simulado ────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF3D342A),
                            Color(0xFF2E2723),
                            Color(0xFF1C1714)
                        )
                    )
                )
        ) {
            // Grid de ayuda (como el de las cámaras)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val line = Color.White.copy(alpha = 0.15f)
                // Líneas verticales
                drawLine(line, Offset(w / 3, 0f), Offset(w / 3, h), 1f)
                drawLine(line, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), 1f)
                // Líneas horizontales
                drawLine(line, Offset(0f, h / 3), Offset(w, h / 3), 1f)
                drawLine(line, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), 1f)
            }

            // Icono de iglesia centrado (sugiere lo que se captura)
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Church,
                    null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Apunta al lugar de la ceremonia",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        // ─── Barra superior con flecha atrás ───────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Atrás",
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Cámara",
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        // ─── Flash simulado al capturar ────────────────
        AnimatedVisibility(
            visible = flashing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }

        // ─── Botón de captura tipo cámara ──────────────
        if (!captured) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Anillo exterior semitransparente
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Disparador blanco (círculo sólido)
                    IconButton(
                        onClick = {
                            scope.launch {
                                flashing = true
                                delay(140)
                                flashing = false
                                captured = true
                                showToast("Foto capturada ✓")
                                delay(1200)
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        // Contenido vacío: el círculo blanco ES el botón
                    }
                }
            }
        }

        // ─── Banner de confirmación post-captura ───────
        AnimatedVisibility(
            visible = captured,
            enter = fadeIn(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✓", color = Gold, fontSize = 40.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Foto guardada",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "La foto quedó registrada en el evento",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
