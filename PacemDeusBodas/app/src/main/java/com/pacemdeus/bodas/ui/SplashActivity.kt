package com.pacemdeus.bodas.ui

// ═══════════════════════════════════════════════════════════════
// Pacem Deus Bodas — Splash Screen (Compose)
// Plataformas Móviles y Análisis Cloud (IS276) — UPC 2026-1
// ═══════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemdeus.bodas.BuildConfig
import com.pacemdeus.bodas.data.prefs.SessionManager
import com.pacemdeus.bodas.ui.auth.LoginActivity
import com.pacemdeus.bodas.ui.coordinator.CoordinatorHomeActivity
import com.pacemdeus.bodas.ui.couple.CoupleHomeActivity
import com.pacemdeus.bodas.ui.planner.PlannerDashboardActivity
import com.pacemdeus.bodas.ui.theme.*
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacemDeusTheme {
                SplashScreen {
                    val intent = if (!SessionManager.isLoggedIn()) {
                        Intent(this, LoginActivity::class.java)
                    } else when (SessionManager.getRole()) {
                        "ADMIN" -> Intent(this, CoordinatorHomeActivity::class.java)
                        "COUPLE" -> Intent(this, CoupleHomeActivity::class.java)
                        "WEDDING_PLANNER" -> Intent(this, PlannerDashboardActivity::class.java)
                        else -> Intent(this, LoginActivity::class.java)
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent); finish()
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(800))

    LaunchedEffect(Unit) {
        visible = true
        delay(2000)
        onFinished()
    }

    Box(Modifier.fillMaxSize().alpha(alpha), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✝", fontSize = 48.sp, color = Gold)
            Spacer(Modifier.height(20.dp))
            Text("Pacem Deus Bodas", fontSize = 28.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Brown)
            Spacer(Modifier.height(8.dp))
            Text("Cantemos al Amor de los Amores", fontSize = 13.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Sand)
            Spacer(Modifier.height(40.dp))
            Text("v${BuildConfig.VERSION_NAME}", fontSize = 11.sp, color = Sand)
        }
    }
}
