package com.pacemdeus.bodas.ui.screens.coordinator

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Church
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.ui.components.Gold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Pantalla de camara simulada. CameraX y ActivityResultLauncher aun no
// se han enseñado, por lo que mostramos un viewfinder ficticio (gradiente
// + grid + icono de iglesia) y al "capturar" disparamos un destello blanco
// breve, llamamos al callback onSavePhoto y volvemos atras.

@Composable
fun CameraScreen(
    onBack: () -> Unit = {},
    onSavePhoto: () -> Unit = {}
) {
    var captured by remember { mutableStateOf(false) }
    val flashAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1612))
    ) {
        // ─── Viewfinder simulado ───────────────────────────
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
            // Grid de ayuda como en una camara real
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val line = Color.White.copy(alpha = 0.15f)
                drawLine(line, Offset(w / 3, 0f), Offset(w / 3, h), 1f)
                drawLine(line, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), 1f)
                drawLine(line, Offset(0f, h / 3), Offset(w, h / 3), 1f)
                drawLine(line, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), 1f)
            }

            // Icono central que sugiere lo que se captura
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Church,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Apunta hacia la entrada de la iglesia",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }

            // Topbar transparente con boton atras
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atras",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "FOTO DEL LOCAL",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            // Boton de captura
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (captured) return@IconButton
                            captured = true
                            scope.launch {
                                // Destello breve
                                flashAlpha.snapTo(1f)
                                flashAlpha.animateTo(0f)
                                delay(200)
                                onSavePhoto()
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Gold, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Camera,
                            contentDescription = "Capturar",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // ─── Capa de flash ─────────────────────────────────
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CameraScreenPreview() {
    MaterialTheme { CameraScreen() }
}
