package com.pacemdeus.bodas.ui.screens.coordinator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.Sand
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Pantalla de captura de foto del local con CameraX + upload al backend.
//
// Flujo:
//   1. Pide permiso CAMERA en runtime.
//   2. Monta una PreviewView con CameraX bindeada al lifecycle.
//   3. Al pulsar el shutter:
//      a) imageCapture.takePicture guarda el JPEG en files/fotos_locales/
//      b) Leemos el archivo, lo decodificamos a Bitmap, lo recomprimimos
//         a JPEG calidad 80 para reducir el payload (~50% del tamano)
//      c) Llamamos apiClient.agregarFoto() con los bytes comprimidos
//      d) El backend la sube a S3 y devuelve la URL HTTPS publica
//      e) Notificamos al caller via onUploaded() para refrescar la UI
//
// Si el upload falla, mantenemos la foto local (no se pierde) y mostramos
// un error en pantalla. El coordinador puede volver a tomarla si quiere.

private const val TAG = "CameraScreen"

// Calidad JPEG para la recompresion antes de subir. 80 mantiene buena
// calidad visual y reduce el tamano ~50% vs el original sin comprimir.
private const val UPLOAD_JPEG_QUALITY = 80

@Composable
fun CameraScreen(
    weddingId: String,
    onBack: () -> Unit = {},
    onUploaded: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient.get(context) }

    // Permiso de camara
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val flashAlpha = remember { Animatable(0f) }

    // Estados del flujo: capturando / subiendo / mensaje de error
    var isCapturing by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Foto recien capturada pendiente de confirmacion. Mientras
    // pendingFile != null mostramos el overlay de preview + caption
    // y NO subimos nada hasta que el usuario confirme.
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingCaption by remember { mutableStateOf("") }

    // Referencia mutable para la PreviewView para poder bindearla desde el LaunchedEffect
    val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }
    var cameraInitialized by remember { mutableStateOf(false) }

    // Inicializar CameraX en un LaunchedEffect cuando tenemos permisos.
    // Esto asegura que el lifecycle esté listo y maneja mejor los errores.
    LaunchedEffect(hasCameraPermission, lifecycleOwner) {
        if (!hasCameraPermission) return@LaunchedEffect

        try {
            val previewView = previewViewRef.value ?: return@LaunchedEffect
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            cameraInitialized = true
            errorMessage = null
            Log.d(TAG, "Camara inicializada correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar la camara: ${e.message}", e)
            errorMessage = "No se pudo inicializar la camara: ${e.message}"
            cameraInitialized = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1612))) {

        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewViewRef.value = it }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Camera,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Para tomar la foto del local necesitamos acceso a la camara.",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Flash blanco al capturar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha.value))
        )

        // Overlay de upload en progreso (oscurece la pantalla)
        if (isUploading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        statusMessage ?: "Subiendo foto al servidor...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Barra superior con boton de regreso
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            IconButton(
                onClick = { if (!isUploading) onBack() },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
        }

        // Mensaje de error
        errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 12.sp)
            }
        }

        // Boton de captura (shutter) - deshabilitado durante captura o upload
        if (!isUploading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .size(80.dp)
                    .background(Gold, CircleShape)
            ) {
                IconButton(
                    onClick = {
                        if (!hasCameraPermission || isCapturing) return@IconButton
                        isCapturing = true
                        errorMessage = null
                        capturarFoto(
                            context = context,
                            imageCapture = imageCapture,
                            onSuccess = { file ->
                                scope.launch {
                                    // Flash visual breve
                                    flashAlpha.snapTo(0.9f)
                                    flashAlpha.animateTo(0f,
                                        animationSpec = androidx.compose.animation.core.tween(220))
                                    delay(60)
                                    isCapturing = false

                                    // En lugar de subir directo, dejamos
                                    // la foto en estado pendiente para que
                                    // el coordinador pueda escribir un
                                    // comentario y confirmar (o descartar).
                                    pendingFile = file
                                    pendingCaption = ""
                                }
                            },
                            onError = { err ->
                                errorMessage = err
                                isCapturing = false
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = "Capturar foto",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        // ─── Overlay de confirmacion + caption ────────────────────
        // Si hay foto pendiente, mostramos un panel modal con preview,
        // campo de comentario opcional y botones Cancelar / Subir.
        // Hasta que el coordinador confirme, no se sube nada al backend.
        val currentPending = pendingFile
        if (currentPending != null) {
            ConfirmPhotoOverlay(
                file = currentPending,
                caption = pendingCaption,
                onCaptionChange = { pendingCaption = it },
                isUploading = isUploading,
                statusMessage = statusMessage,
                onCancel = {
                    // Descartar el archivo local y volver a la camara
                    try { currentPending.delete() } catch (_: Exception) {}
                    pendingFile = null
                    pendingCaption = ""
                    statusMessage = null
                },
                onConfirm = {
                    isUploading = true
                    statusMessage = "Procesando imagen..."
                    scope.launch {
                        uploadFoto(
                            file = currentPending,
                            weddingId = weddingId,
                            caption = pendingCaption.takeIf { it.isNotBlank() },
                            apiClient = apiClient,
                            onProgress = { statusMessage = it },
                            onResult = { result ->
                                isUploading = false
                                statusMessage = null
                                when (result) {
                                    is UploadResult.Success -> {
                                        pendingFile = null
                                        pendingCaption = ""
                                        onUploaded()
                                    }
                                    is UploadResult.Error -> {
                                        errorMessage = result.message
                                    }
                                }
                            }
                        )
                    }
                }
            )
        }
    }
}

/** Sealed para distinguir success/error de upload sin acoplar a ApiResult. */
private sealed class UploadResult {
    object Success : UploadResult()
    data class Error(val message: String) : UploadResult()
}

/**
 * Dispara la captura de CameraX. Guarda el JPEG en files/fotos_locales/
 * con nombre por timestamp.
 */
private fun capturarFoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onSuccess: (File) -> Unit,
    onError: (String) -> Unit
) {
    val outputDir = File(context.filesDir, "fotos_locales").apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val photoFile = File(outputDir, "local_$timestamp.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d(TAG, "Foto guardada en ${photoFile.absolutePath} (${photoFile.length()} bytes)")
                onSuccess(photoFile)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Error al capturar la foto", exc)
                onError("No se pudo guardar la foto")
            }
        }
    )
}

/**
 * Recomprime la foto a JPEG calidad 80 (para reducir payload ~50%) y la
 * sube al backend. El backend hace put_object en S3 y devuelve la URL.
 */
private suspend fun uploadFoto(
    file: File,
    weddingId: String,
    caption: String?,
    apiClient: ApiClient,
    onProgress: (String) -> Unit,
    onResult: (UploadResult) -> Unit
) {
    try {
        onProgress("Procesando imagen...")
        val compressedBytes = withContext(Dispatchers.IO) {
            comprimirJpeg(file, UPLOAD_JPEG_QUALITY)
        }
        Log.d(TAG, "Imagen comprimida: ${compressedBytes.size} bytes (orig ${file.length()})")

        onProgress("Subiendo al servidor...")
        apiClient.agregarFoto(
            idBoda = weddingId,
            imageBytes = compressedBytes,
            mimeType = "image/jpeg",
            caption = caption
        ) { result ->
            when (result) {
                is ApiResult.Success -> {
                    Log.d(TAG, "Foto subida. id=${result.data.id} orden=${result.data.order}")
                    onResult(UploadResult.Success)
                }
                is ApiResult.Error -> {
                    onResult(UploadResult.Error("No se pudo subir: ${result.message}"))
                }
                else -> onResult(UploadResult.Error("Estado inesperado"))
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error preparando foto para upload", e)
        onResult(UploadResult.Error("Error al procesar la foto"))
    }
}

/**
 * Lee el archivo JPEG capturado por CameraX y lo recomprime a la calidad
 * solicitada. Devuelve los bytes resultantes listos para enviar.
 *
 * No redimensionamos: las camaras modernas devuelven imagenes ya en
 * resolucion razonable (~2-4 MP). La compresion JPEG reduce el tamano
 * sin perder calidad visual significativa.
 */
private fun comprimirJpeg(file: File, quality: Int): ByteArray {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        ?: throw IllegalStateException("No se pudo decodificar la imagen")
    val out = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
    bitmap.recycle()
    return out.toByteArray()
}

/**
 * Overlay modal sobre la camara que muestra el preview de la foto recien
 * capturada, un campo de texto opcional para anotar lo que se ve, y dos
 * botones: descartar (vuelve a la camara borrando el archivo) o subir
 * (envia al backend con el comentario).
 *
 * El proposito del comentario es que el coro registre detalles del local
 * relevantes para la performance (acustica, ubicacion del organo, etc.)
 * que luego puedan revisar al planificar el ensamble.
 */
@Composable
private fun ConfirmPhotoOverlay(
    file: File,
    caption: String,
    onCaptionChange: (String) -> Unit,
    isUploading: Boolean,
    statusMessage: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    // Fondo oscuro semi-transparente que bloquea la camara debajo
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .background(Cream, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                "Confirma la foto",
                color = Brown,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(
                "Agrega un comentario para que el coro tenga contexto del local",
                color = Sand,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

            // Preview de la foto recien tomada
            coil.compose.AsyncImage(
                model = file,
                contentDescription = "Foto capturada",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

            // Campo de comentario (opcional)
            androidx.compose.material3.OutlinedTextField(
                value = caption,
                onValueChange = onCaptionChange,
                label = { androidx.compose.material3.Text("Comentario (opcional)") },
                placeholder = {
                    androidx.compose.material3.Text(
                        "Ej. organo al fondo derecha, ambiente acustico bueno"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                minLines = 2,
                maxLines = 4,
                enabled = !isUploading,
                colors = com.pacemdeus.bodas.ui.components.goldTextFieldColors()
            )

            if (statusMessage != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    statusMessage,
                    color = Brown,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))

            // Botones de accion
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    com.pacemdeus.bodas.ui.components.OutlineGoldButton(
                        text = "Descartar",
                        onClick = onCancel
                    )
                }
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    com.pacemdeus.bodas.ui.components.GoldButton(
                        text = if (isUploading) "Subiendo..." else "Subir foto",
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}
