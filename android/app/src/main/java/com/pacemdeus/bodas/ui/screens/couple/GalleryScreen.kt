package com.pacemdeus.bodas.ui.screens.couple

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.pacemdeus.bodas.data.UserSession
import com.pacemdeus.bodas.data.WeddingPhoto
import com.pacemdeus.bodas.data.network.ApiClient
import com.pacemdeus.bodas.data.network.ApiResult
import com.pacemdeus.bodas.ui.components.GoldButton
import com.pacemdeus.bodas.ui.components.OutlineGoldButton
import com.pacemdeus.bodas.ui.components.PacemTopBar
import com.pacemdeus.bodas.ui.theme.Brown
import com.pacemdeus.bodas.ui.theme.Cream
import com.pacemdeus.bodas.ui.theme.Gold
import com.pacemdeus.bodas.ui.theme.GoldSoft
import com.pacemdeus.bodas.ui.theme.Sand
import java.io.File

// Pantalla para gestionar las fotos del local del evento.
//
// Maximo 5 fotos por evento. Soporta:
//   1. Tomar foto (camara nativa via TakePicture launcher)
//   2. Elegir varias de galeria (PickMultipleVisualMedia, hasta 5)
//
// Antes de subir, se pide un comentario opcional que se aplica como
// caption a todas las fotos del batch. Si la novia/planner sube fotos
// distintas que merezcan distinto comentario, puede editarlo despues
// con el boton de edicion en cada foto.
//
// Cada foto muestra el caption + firma del autor.

private const val TAG = "GalleryScreen"
private const val MAX_PHOTOS = 5
private const val MAX_CAPTION_LEN = 500

/**
 * Devuelve el texto de firma del autor: nombre + rol legible separados
 * por punto medio. Ej: "Carla Mendoza · Wedding planner".
 *
 * Los roles que el backend envia son COUPLE / WEDDING_PLANNER / ADMIN;
 * los traducimos a etiquetas en espanol para mostrar en UI.
 */
private fun formatAuthorWithRole(name: String?, role: String?): String {
    val n = name?.trim().orEmpty()
    if (n.isEmpty()) return ""
    val roleLabel = when (role?.uppercase()) {
        "COUPLE"          -> "Novios"
        "WEDDING_PLANNER" -> "Wedding planner"
        "ADMIN"           -> "Director"
        else              -> null
    }
    return if (roleLabel != null) "$n · $roleLabel" else n
}

/** Pending upload batch: bytes + mime, esperando el caption del usuario. */
private data class PendingBatch(
    val items: List<Pair<ByteArray, String>>   // (bytes, mimeType)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    session: UserSession,
    weddingId: String,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.get(context) }
    val currentUserId = session.user.id

    var photos by remember { mutableStateOf<List<WeddingPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isWorking by remember { mutableStateOf(false) }   // upload o delete
    var workingMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Permiso CAMERA en runtime: Android 11+ exige que la app lo tenga
    // concedido antes de lanzar ACTION_IMAGE_CAPTURE con URI propia.
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) errorMessage = "Se necesita permiso de camara para tomar fotos"
    }
    var pendingBatch by remember { mutableStateOf<PendingBatch?>(null) }
    var photoToDelete by remember { mutableStateOf<WeddingPhoto?>(null) }
    var photoToView by remember { mutableStateOf<WeddingPhoto?>(null) }
    var photoToEditCaption by remember { mutableStateOf<WeddingPhoto?>(null) }

    // ─── Carga inicial ───────────────────────────────────
    LaunchedEffect(Unit) {
        isLoading = true
        apiClient.listarFotos(weddingId) { result ->
            isLoading = false
            when (result) {
                is ApiResult.Success -> photos = result.data
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    /**
     * Sube todas las fotos del batch secuencialmente con el mismo caption.
     * Cualquier fallo se reporta al final pero no detiene el resto.
     */
    fun uploadBatchWithCaption(batch: PendingBatch, caption: String?) {
        if (batch.items.isEmpty()) return
        isWorking = true
        workingMessage = "Subiendo 1 de ${batch.items.size}..."
        errorMessage = null

        var index = 0
        var failures = 0

        fun uploadNext() {
            if (index >= batch.items.size) {
                isWorking = false
                workingMessage = null
                if (failures > 0) {
                    errorMessage = "$failures de ${batch.items.size} foto(s) no se pudieron subir"
                }
                return
            }

            workingMessage = "Subiendo ${index + 1} de ${batch.items.size}..."
            val (bytes, mime) = batch.items[index]
            apiClient.agregarFoto(weddingId, bytes, mime, caption) { result ->
                when (result) {
                    is ApiResult.Success -> photos = photos + result.data
                    is ApiResult.Error -> {
                        failures++
                        Log.w(TAG, "Falla foto $index: ${result.message}")
                    }
                    else -> failures++
                }
                index++
                uploadNext()
            }
        }
        uploadNext()
    }

    // ─── Procesamiento de URIs seleccionadas ─────────────
    //
    // Funcion compartida por ambos launchers (multi y single). Convierte
    // las URIs en bytes en memoria y dispara el dialog de caption. Se
    // extrae aqui para evitar duplicar logica entre los dos launchers.
    val procesarUrisSeleccionadas: (List<Uri>) -> Unit = procesar@{ uris ->
        if (uris.isEmpty()) return@procesar
        try {
            val items = uris.mapNotNull { uri ->
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) return@mapNotNull null
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                bytes to mime
            }
            if (items.isEmpty()) {
                errorMessage = "No se pudieron leer las imagenes seleccionadas"
                return@procesar
            }
            pendingBatch = PendingBatch(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo URIs", e)
            errorMessage = "No se pudo leer las imagenes"
        }
    }

    // ─── Launchers: galeria multi-select y single-select ─────────
    //
    // El SDK de Android requiere maxItems >= 2 para PickMultipleVisualMedia.
    // Si solo queda espacio para 1 foto mas, usamos el contract singular
    // PickVisualMedia. Esto evita IllegalArgumentException("Max items must
    // be higher than 1") que crashea la pantalla al rendering.
    val slotsRestantes = (MAX_PHOTOS - photos.size).coerceAtLeast(0)

    val galleryLauncherMulti = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            // Garantizamos minimo 2 para que el constructor no lance.
            // Cuando slotsRestantes < 2 usamos el launcher single.
            maxItems = slotsRestantes.coerceAtLeast(2)
        )
    ) { uris -> procesarUrisSeleccionadas(uris) }

    val galleryLauncherSingle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        procesarUrisSeleccionadas(if (uri != null) listOf(uri) else emptyList())
    }

    // Wrapper que decide cual launcher disparar segun el espacio disponible.
    val abrirSelector: () -> Unit = {
        val request = PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly
        )
        if (slotsRestantes >= 2) {
            galleryLauncherMulti.launch(request)
        } else {
            galleryLauncherSingle.launch(request)
        }
    }


    // ─── Launcher: camara ────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (!success || uri == null) {
            pendingCameraUri = null
            return@rememberLauncherForActivityResult
        }
        try {
            val bytes = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes() } ?: ByteArray(0)
            if (bytes.isEmpty()) {
                errorMessage = "No se pudo leer la foto capturada"
            } else {
                pendingBatch = PendingBatch(listOf(bytes to "image/jpeg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo URI de camara", e)
            errorMessage = "No se pudo leer la foto capturada"
        } finally {
            pendingCameraUri = null
        }
    }

    fun launchCamera() {
        try {
            val uri = createTempCaptureUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparando captura de camara", e)
            errorMessage = "No se pudo abrir la camara"
        }
    }

    fun launchGallery() {
        abrirSelector()
    }

    fun deletePhoto(photo: WeddingPhoto) {
        isWorking = true
        workingMessage = "Eliminando foto..."
        apiClient.eliminarFoto(weddingId, photo.id) { result ->
            isWorking = false
            workingMessage = null
            when (result) {
                is ApiResult.Success -> photos = photos.filterNot { it.id == photo.id }
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    fun saveCaptionEdit(photo: WeddingPhoto, newCaption: String?) {
        isWorking = true
        workingMessage = "Guardando comentario..."
        apiClient.editarCaptionFoto(weddingId, photo.id, newCaption) { result ->
            isWorking = false
            workingMessage = null
            when (result) {
                is ApiResult.Success -> {
                    // Actualizacion in-place
                    photos = photos.map {
                        if (it.id == photo.id) it.copy(caption = newCaption?.takeIf { c -> c.isNotBlank() })
                        else it
                    }
                }
                is ApiResult.Error -> errorMessage = result.message
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = { PacemTopBar(title = "Fotos del local", onBack = onBack) },
        containerColor = Cream
    ) { padding ->

        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ─── Header con contador ─────────────────
            CounterHeader(total = photos.size, max = MAX_PHOTOS)

            Spacer(Modifier.height(20.dp))

            // ─── Lista de fotos ──────────────────────
            if (photos.isEmpty()) {
                EmptyPhotosState()
            } else {
                for ((idx, photo) in photos.withIndex()) {
                    PhotoCard(
                        photo = photo,
                        canEditCaption = photo.authorUserId == currentUserId,
                        onTap = { photoToView = photo },
                        onDelete = { photoToDelete = photo },
                        onEditCaption = { photoToEditCaption = photo }
                    )
                    if (idx < photos.size - 1) Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Botones para agregar ────────────────
            if (photos.size < MAX_PHOTOS && !isWorking) {
                GoldButton(
                    text = "Tomar foto con la camara",
                    onClick = {
                        if (hasCameraPermission) launchCamera()
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlineGoldButton(
                    text = "Elegir de la galeria",
                    onClick = { launchGallery() }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (photos.size + 1 >= MAX_PHOTOS)
                        "Puedes agregar 1 foto mas."
                    else
                        "Puedes agregar hasta ${MAX_PHOTOS - photos.size} fotos mas (selecciona varias a la vez).",
                    color = Sand,
                    fontSize = 11.sp
                )
            } else if (photos.size >= MAX_PHOTOS) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GoldSoft, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "Ya alcanzaste el maximo de $MAX_PHOTOS fotos. " +
                            "Quita alguna si quieres subir otra.",
                        color = Brown,
                        fontSize = 12.sp
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // Cierra la gestion de fotos y regresa al dashboard. Las
            // fotos se persisten al subirse/eliminarse, no en este
            // boton: aqui solo confirmamos que el usuario termino.
            GoldButton(
                text = "Confirmar y volver",
                onClick = onBack
            )

            Spacer(Modifier.height(20.dp))
        }

        // ─── Overlay de trabajo (upload/delete/edit) ─────
        if (isWorking) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Gold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        workingMessage ?: "Procesando...",
                        color = Cream,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // ─── Dialog: pedir caption antes de subir batch ──────
    pendingBatch?.let { batch ->
        CaptionInputDialog(
            title = if (batch.items.size == 1) "Comentario para esta foto"
                    else "Comentario para las ${batch.items.size} fotos",
            initialCaption = "",
            helperText = if (batch.items.size > 1)
                "El mismo comentario se aplicara a todas las fotos seleccionadas. " +
                    "Podras editarlo individualmente despues."
            else
                "Comparte que quieres transmitir con esta foto. Es opcional.",
            onCancel = {
                pendingBatch = null
            },
            onConfirm = { caption ->
                pendingBatch = null
                uploadBatchWithCaption(batch, caption)
            }
        )
    }

    // ─── Dialog: editar caption de una foto ya subida ────
    photoToEditCaption?.let { photo ->
        CaptionInputDialog(
            title = "Editar comentario",
            initialCaption = photo.caption.orEmpty(),
            helperText = "Deja vacio para quitar el comentario.",
            onCancel = { photoToEditCaption = null },
            onConfirm = { newCaption ->
                photoToEditCaption = null
                saveCaptionEdit(photo, newCaption)
            }
        )
    }

    // ─── Dialog: confirmar borrado ──────────────────────
    photoToDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Quitar foto", color = Brown, fontWeight = FontWeight.Bold) },
            text = { Text("Esta foto se eliminara del evento. Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    photoToDelete = null
                    deletePhoto(p)
                }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) {
                    Text("Cancelar", color = Sand)
                }
            },
            containerColor = Cream
        )
    }

    // ─── Dialog: ver foto grande ────────────────────────
    photoToView?.let { p ->
        AlertDialog(
            onDismissRequest = { photoToView = null },
            confirmButton = {
                TextButton(onClick = { photoToView = null }) {
                    Text("Cerrar", color = Gold, fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column {
                    AsyncImage(
                        model = p.url,
                        contentDescription = "Foto del local",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                    )
                    if (!p.caption.isNullOrBlank() || !p.authorName.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        if (!p.caption.isNullOrBlank()) {
                            Text(
                                "\"${p.caption}\"",
                                color = Brown,
                                fontSize = 14.sp,
                                fontStyle = FontStyle.Italic
                            )
                        }
                        if (!p.authorName.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "— ${formatAuthorWithRole(p.authorName, p.authorRole)}",
                                color = Sand,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            containerColor = Cream
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Sub-composables
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CounterHeader(total: Int, max: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GoldSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "FOTOS DEL LOCAL",
                    color = Sand,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (total == 0) "Aun no has subido fotos"
                    else "$total foto${if (total == 1) "" else "s"} subida${if (total == 1) "" else "s"}",
                    color = Brown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier.size(54.dp).background(Gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$total/$max",
                    color = Cream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Card que muestra una foto del local con su caption y firma del autor.
 * El boton de editar comentario aparece solo si el usuario actual es el
 * autor de la foto.
 */
@Composable
private fun PhotoCard(
    photo: WeddingPhoto,
    canEditCaption: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onEditCaption: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(14.dp))
            .border(1.dp, GoldSoft, RoundedCornerShape(14.dp))
    ) {
        // ─── Imagen con controles overlay ─────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable(onClick = onTap)
        ) {
            AsyncImage(
                model = photo.url,
                contentDescription = "Foto ${photo.order}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(2.dp)
            )

            // Numero de orden (top-left)
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .size(28.dp)
                    .background(Color(0xCC000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${photo.order}",
                    color = Cream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Boton quitar (top-right)
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .background(Color(0xCC000000), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar foto",
                    tint = Cream,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ─── Footer con caption + autor ────────────
        CaptionFooter(
            photo = photo,
            canEdit = canEditCaption,
            onEditCaption = onEditCaption
        )
    }
}

@Composable
private fun CaptionFooter(
    photo: WeddingPhoto,
    canEdit: Boolean,
    onEditCaption: () -> Unit
) {
    val hasCaption = !photo.caption.isNullOrBlank()
    val hasAuthor = !photo.authorName.isNullOrBlank()

    if (!hasCaption && !hasAuthor && !canEdit) {
        // Nada que mostrar y nada para editar: footer compacto
        Spacer(Modifier.height(8.dp))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (hasCaption) {
            // Caption en estilo cita
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.FormatQuote,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    photo.caption ?: "",
                    color = Brown,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        } else if (canEdit) {
            Text(
                "Aun no has agregado un comentario a esta foto.",
                color = Sand,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic
            )
        }

        // Firma del autor + boton editar
        if (hasAuthor || canEdit) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasAuthor) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Sand,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        formatAuthorWithRole(photo.authorName, photo.authorRole),
                        color = Sand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (canEdit) {
                    TextButton(
                        onClick = onEditCaption,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp, vertical = 0.dp
                        )
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            if (hasCaption) "Editar comentario" else "Agregar comentario",
                            color = Gold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionInputDialog(
    title: String,
    initialCaption: String,
    helperText: String,
    onCancel: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var text by remember { mutableStateOf(initialCaption) }
    val isValid = text.length <= MAX_CAPTION_LEN

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, color = Brown, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    helperText,
                    color = Sand,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_CAPTION_LEN + 50) text = it },
                    placeholder = {
                        Text(
                            "Ej. Vista de la nave central desde la entrada...",
                            color = Sand,
                            fontSize = 12.sp
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    isError = !isValid,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = GoldSoft,
                        cursorColor = Gold,
                        focusedTextColor = Brown,
                        unfocusedTextColor = Brown
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${text.length} / $MAX_CAPTION_LEN",
                    color = if (isValid) Sand else MaterialTheme.colorScheme.error,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val sanitized = text.trim().takeIf { it.isNotEmpty() }
                    onConfirm(sanitized)
                }
            ) {
                Text("Guardar", color = Gold, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar", color = Sand)
            }
        },
        containerColor = Cream
    )
}

@Composable
private fun EmptyPhotosState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(72.dp).background(GoldSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Aun no has subido fotos del local",
                color = Brown,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Estas fotos ayudan al coro a planificar la disposicion el dia de la ceremonia.",
                color = Sand,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Helper: URI temporal para captura de camara
// ═══════════════════════════════════════════════════════════════════

private fun createTempCaptureUri(context: Context): Uri {
    val cacheDir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
