package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LiveChatMessage
import com.example.data.model.LiveStream
import com.example.data.model.MediaType
import com.example.data.model.MonetizationType
import com.example.data.model.PostVisibility
import com.example.interop.ExternalEditorBridge
import com.example.ui.components.ExpressiveSurfaceCard
import com.example.ui.components.MorphingPillButton
import com.example.ui.theme.LocalKromaAccents
import com.example.ui.theme.OrganicHeroShape
import com.example.ui.theme.PillShape
import com.example.ui.viewmodel.MediaDraft
import com.example.ui.viewmodel.StudioViewModel
import kotlinx.coroutines.delay

enum class StudioCaptureMode(val labelRes: Int) {
    VIDEO(R.string.capture_mode_video),
    PHOTO(R.string.capture_mode_photo),
    LIVE(R.string.capture_mode_live),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatorStudioScreen(
    viewModel: StudioViewModel,
    openPublishSheetSignal: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val accents = LocalKromaAccents.current

    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val activeLiveStream by viewModel.activeLiveStream.collectAsStateWithLifecycle()
    val liveChat by viewModel.activeLiveChat.collectAsStateWithLifecycle()

    var captureMode by remember { mutableStateOf(StudioCaptureMode.VIDEO) }
    var isVerticalAspect by remember { mutableStateOf(true) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var showPublishSheet by remember { mutableStateOf(false) }
    var liveTitleInput by remember { mutableStateOf("Estudio en Vivo · Kroma") }
    var chatInput by remember { mutableStateOf("") }
    var localNotice by remember { mutableStateOf<String?>(null) }

    val installedEditors = remember(context) {
        ExternalEditorBridge.detectInstalledEditors(context)
    }

    var hasMediaPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasMediaPermissions = grants[Manifest.permission.CAMERA] == true &&
            grants[Manifest.permission.RECORD_AUDIO] == true
    }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: ""
            val type = if (mime.startsWith("image/")) MediaType.IMAGE else MediaType.VIDEO
            val safeUri = ExternalEditorBridge.copyToStudioFileProviderUri(context, uri, type) ?: uri
            viewModel.setDraftMedia(safeUri, type, editedExternally = false)
        }
    }

    LaunchedEffect(openPublishSheetSignal) {
        if (openPublishSheetSignal > 0 && draft != null) {
            showPublishSheet = true
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordingSeconds += 1
            }
        }
    }

    // CameraX use cases
    val imageCapture = remember { ImageCapture.Builder().build() }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        VideoCapture.withOutput(recorder)
    }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(hasMediaPermissions, useFrontCamera) {
        if (!hasMediaPermissions) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture,
                    videoCapture,
                )
            } catch (e: Exception) {
                localNotice = "Vista previa de cámara no disponible en este dispositivo: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header + Mode Selector Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.studio_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Captura · Edita fuera · Monetiza",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Aspect ratio switch (Vertical 9:16 vs Horizontal 16:9)
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(PillShape)
                        .minimumInteractiveComponentSize()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isVerticalAspect = !isVerticalAspect
                        }
                        .testTag("aspect_ratio_toggle"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (isVerticalAspect) Icons.Filled.CropPortrait else Icons.Filled.CropLandscape,
                            contentDescription = "Cambiar orientación",
                            tint = accents.cobalt,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isVerticalAspect) "9:16" else "16:9",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Expressive Mode Switcher (Video / Foto / En Vivo)
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StudioCaptureMode.entries.forEach { mode ->
                        val selected = mode == captureMode
                        val bgColor = when {
                            selected && mode == StudioCaptureMode.LIVE -> accents.coral
                            selected -> accents.violet
                            else -> Color.Transparent
                        }
                        val fgColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PillShape)
                                .background(bgColor)
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    captureMode = mode
                                }
                                .testTag("mode_tab_${mode.name.lowercase()}")
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(mode.labelRes),
                                style = MaterialTheme.typography.labelLarge,
                                color = fgColor,
                            )
                        }
                    }
                }
            }

            // CameraX Viewport OR Permission Request Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isVerticalAspect) {
                            Modifier.height(340.dp)
                        } else {
                            Modifier.aspectRatio(16f / 9f)
                        }
                    )
                    .clip(OrganicHeroShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                if (hasMediaPermissions) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_live_backdrop),
                        contentDescription = "Fondo de estudio",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x990E091B))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = accents.violet,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = stringResource(R.string.permission_camera_rationale),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                            )
                            MorphingPillButton(
                                text = stringResource(R.string.grant_permissions),
                                icon = Icons.Filled.PhotoCamera,
                                containerColor = accents.violet,
                                testTag = "grant_camera_permissions_button",
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.CAMERA,
                                            Manifest.permission.RECORD_AUDIO,
                                        )
                                    )
                                },
                            )
                        }
                    }
                }

                // Top viewport overlay (REC / LIVE status + Switch Camera)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRecording || activeLiveStream != null) {
                        Surface(
                            shape = PillShape,
                            color = accents.coral,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FiberManualRecord,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = if (activeLiveStream != null) {
                                        "EN VIVO · ${activeLiveStream!!.viewerCount} viendo"
                                    } else {
                                        "REC ${"%02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (hasMediaPermissions) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x880E091B),
                        ) {
                            IconButton(
                                onClick = { useFrontCamera = !useFrontCamera },
                                modifier = Modifier.testTag("switch_camera_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Cameraswitch,
                                    contentDescription = "Cambiar cámara",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                // Bottom Capture Controls Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC0E091B))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Zero-Permission Photo Picker button
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        shape = PillShape,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("gallery_picker_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = stringResource(R.string.pick_from_gallery),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Galería",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    // Primary Morphing Capture / Live Trigger
                    if (captureMode != StudioCaptureMode.LIVE) {
                        val shutterSize by animateDpAsState(
                            targetValue = if (isRecording) 30.dp else 56.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "shutter_morph",
                        )
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                                .clickable(enabled = hasMediaPermissions) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (captureMode == StudioCaptureMode.PHOTO) {
                                        val file = ExternalEditorBridge.createRecordingDestinationFile(
                                            context,
                                            MediaType.IMAGE,
                                        )
                                        val output = ImageCapture.OutputFileOptions.Builder(file).build()
                                        imageCapture.takePicture(
                                            output,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    val uri = ExternalEditorBridge.uriForStudioFile(context, file)
                                                    viewModel.setDraftMedia(uri, MediaType.IMAGE)
                                                    localNotice = "Foto capturada lista para editar o publicar."
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    localNotice = "Error al capturar foto: ${exception.localizedMessage}"
                                                }
                                            },
                                        )
                                    } else {
                                        if (!isRecording) {
                                            val file = ExternalEditorBridge.createRecordingDestinationFile(
                                                context,
                                                MediaType.VIDEO,
                                            )
                                            val output = FileOutputOptions.Builder(file).build()
                                            isRecording = true
                                            activeRecording = videoCapture.output
                                                .prepareRecording(context, output)
                                                .withAudioEnabled()
                                                .start(ContextCompat.getMainExecutor(context)) { event ->
                                                    if (event is VideoRecordEvent.Finalize) {
                                                        isRecording = false
                                                        if (!event.hasError()) {
                                                            val uri = ExternalEditorBridge.uriForStudioFile(context, file)
                                                            viewModel.setDraftMedia(uri, MediaType.VIDEO)
                                                            localNotice = "Clip grabado (${recordingSeconds}s). Listo para editar fuera o publicar."
                                                        } else {
                                                            localNotice = "Error de grabación (${event.error})."
                                                        }
                                                    }
                                                }
                                        } else {
                                            activeRecording?.stop()
                                            activeRecording = null
                                        }
                                    }
                                }
                                .testTag("capture_shutter_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(shutterSize)
                                    .clip(RoundedCornerShape(if (isRecording) 10.dp else 32.dp))
                                    .background(
                                        if (captureMode == StudioCaptureMode.VIDEO) accents.coral else Color.White
                                    )
                            )
                        }
                    } else {
                        MorphingPillButton(
                            text = if (activeLiveStream == null) "Iniciar Live" else "Terminar Live",
                            icon = if (activeLiveStream == null) Icons.Filled.LiveTv else Icons.Filled.Stop,
                            containerColor = if (activeLiveStream == null) accents.coral else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = Color.White,
                            testTag = "live_toggle_button",
                            onClick = {
                                if (activeLiveStream == null) {
                                    viewModel.startLiveBroadcast(liveTitleInput)
                                } else {
                                    viewModel.stopLiveBroadcast()
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.width(64.dp))
                }
            }

            if (localNotice != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = localNotice!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { localNotice = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar aviso")
                        }
                    }
                }
            }

            // LIVE STREAM PANEL (RTMP Ingest + Real-Time Firestore Chat)
            AnimatedVisibility(visible = captureMode == StudioCaptureMode.LIVE) {
                LiveBroadcastControlPanel(
                    activeStream = activeLiveStream,
                    titleInput = liveTitleInput,
                    onTitleChange = { liveTitleInput = it },
                    chatMessages = liveChat,
                    chatInput = chatInput,
                    onChatInputChange = { chatInput = it },
                    onSendChat = {
                        if (chatInput.isNotBlank()) {
                            viewModel.sendLiveChat(chatInput)
                            chatInput = ""
                        }
                    },
                )
            }

            // EXTERNAL EDITOR INTEROPERABILITY & SHARE-BACK WORKFLOW
            ExternalEditorInteropCard(
                draft = draft,
                installedEditors = installedEditors,
                onOpenExternalEditor = { preferredPkg, displayName ->
                    val currentDraft = draft ?: return@ExternalEditorInteropCard
                    ExternalEditorBridge.launchExternalEditor(
                        context = context,
                        mediaUri = currentDraft.uri,
                        mediaType = currentDraft.mediaType,
                        preferredPackage = preferredPkg,
                    ).onSuccess { launchedTarget ->
                        viewModel.markDraftSentToExternalEditor(displayName ?: launchedTarget)
                        localNotice = "Archivo enviado con FileProvider. Al terminar, usa Compartir -> Kroma Studio para volver con 1 toque."
                    }.onFailure { err ->
                        localNotice = err.localizedMessage
                    }
                },
                onOpenPublishSheet = { showPublishSheet = true },
                onClearDraft = { viewModel.clearDraft() },
                onPickFromGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
            )
        }
    }

    if (showPublishSheet && draft != null) {
        PublishMetadataBottomSheet(
            draft = draft!!,
            onDismiss = { showPublishSheet = false },
            onPublish = { title, desc, tags, visibility, monetization, price ->
                viewModel.publishDraft(title, desc, tags, visibility, monetization, price)
                showPublishSheet = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExternalEditorInteropCard(
    draft: MediaDraft?,
    installedEditors: List<com.example.interop.InstalledEditorApp>,
    onOpenExternalEditor: (String?, String?) -> Unit,
    onOpenPublishSheet: () -> Unit,
    onClearDraft: () -> Unit,
    onPickFromGallery: () -> Unit,
) {
    val accents = LocalKromaAccents.current

    ExpressiveSurfaceCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        borderColor = accents.cobalt.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        tint = accents.cobalt,
                    )
                    Text(
                        text = "Puente con Editor Externo",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (draft != null) {
                    IconButton(onClick = onClearDraft) {
                        Icon(Icons.Filled.Close, contentDescription = "Descartar borrador")
                    }
                }
            }

            if (draft == null) {
                Text(
                    text = "Graba un clip o selecciona desde tu galería para enviarlo directamente a CapCut, VN, InShot o YouCut mediante FileProvider seguro, y recíbelo de vuelta desde el menú Compartir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    installedEditors.forEach { editor ->
                        Surface(
                            shape = PillShape,
                            color = if (editor.installed) {
                                accents.emerald.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                1.dp,
                                if (editor.installed) accents.emerald else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Text(
                                text = if (editor.installed) "${editor.displayName} · Instalado" else editor.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (editor.installed) accents.emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onPickFromGallery,
                    shape = PillShape,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pick_from_gallery))
                }
            } else {
                // Active Draft Preview + External Editor Handoff
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = draft.uri,
                        contentDescription = "Vista previa del clip",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (draft.mediaType == MediaType.VIDEO) "Clip de Video listo" else "Fotografía lista",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (draft.editedExternally) {
                            Surface(
                                shape = PillShape,
                                color = accents.emerald.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    text = "Editado en ${draft.externalEditorName ?: "App Externa"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.emerald,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            Text(
                                text = "Opcional: pulir en CapCut / VN / InShot antes de monetizar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Direct quick-launch chips for installed editors
                val installed = installedEditors.filter { it.installed }
                if (installed.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        installed.forEach { editor ->
                            FilterChip(
                                selected = false,
                                onClick = { onOpenExternalEditor(editor.packageName, editor.displayName) },
                                label = { Text("Abrir en ${editor.displayName}") },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MorphingPillButton(
                        text = stringResource(R.string.edit_external_cta),
                        icon = Icons.Filled.AutoFixHigh,
                        containerColor = accents.cobalt,
                        contentColor = Color.White,
                        testTag = "external_editor_button",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenExternalEditor(null, "Editor Externo") },
                    )
                    MorphingPillButton(
                        text = stringResource(R.string.publish_now_cta),
                        icon = Icons.Filled.Publish,
                        containerColor = accents.violet,
                        contentColor = Color.White,
                        testTag = "open_publish_sheet_button",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPublishSheet,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBroadcastControlPanel(
    activeStream: LiveStream?,
    titleInput: String,
    onTitleChange: (String) -> Unit,
    chatMessages: List<LiveChatMessage>,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    onSendChat: () -> Unit,
) {
    val accents = LocalKromaAccents.current
    ExpressiveSurfaceCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        borderColor = accents.coral.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Transmisión RTMP / WebRTC & Chat en Vivo",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (activeStream == null) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = onTitleChange,
                    label = { Text("Título del Live") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Pulsa 'Iniciar Live' sobre el visor para generar tu llave RTMP y activar el chat en tiempo real en Cloud Firestore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "RTMP Ingest: ${activeStream.rtmpIngestUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.cobalt,
                        )
                        Text(
                            text = "HLS Playback: ${activeStream.playbackUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Real-time chat feed
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                ) {
                    if (chatMessages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Chat en vivo conectado. Envía un saludo a tu audiencia.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(chatMessages, key = { it.id.ifBlank { it.hashCode().toString() } }) { msg ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "${msg.senderName}:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accents.coral,
                                    )
                                    Text(
                                        text = msg.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = onChatInputChange,
                        placeholder = { Text("Escribe en el chat en vivo...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_chat_input"),
                    )
                    IconButton(
                        onClick = onSendChat,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accents.coral)
                            .minimumInteractiveComponentSize()
                            .testTag("live_chat_send_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar mensaje",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PublishMetadataBottomSheet(
    draft: MediaDraft,
    onDismiss: () -> Unit,
    onPublish: (
        title: String,
        description: String,
        tagsRaw: String,
        visibility: PostVisibility,
        monetization: MonetizationType,
        priceDollars: String,
    ) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val accents = LocalKromaAccents.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(if (draft.editedExternally) "Clip Editado · Kroma" else "Nuevo Lanzamiento") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("creator, studio, exclusive") }
    var visibility by remember { mutableStateOf(PostVisibility.PUBLIC) }
    var monetization by remember { mutableStateOf(MonetizationType.FREE_ADS) }
    var priceDollars by remember { mutableStateOf("4.99") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Publicar & Monetizar",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120) },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("publish_title_input"),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(600) },
                label = { Text("Descripción") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Etiquetas (separadas por coma)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Visibilidad",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PostVisibility.PUBLIC to "Público",
                    PostVisibility.SUBSCRIBERS to "Solo Suscriptores",
                    PostVisibility.PRIVATE to "Privado",
                ).forEach { (option, label) ->
                    FilterChip(
                        selected = visibility == option,
                        onClick = { visibility = option },
                        label = { Text(label) },
                    )
                }
            }

            Text(
                text = "Modelo de Monetización",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MonetizationType.FREE_ADS to "Gratis con Anuncios",
                    MonetizationType.PAY_PER_VIEW to "Pago por Ver (PPV)",
                    MonetizationType.LIVE_TIP to "Propinas",
                ).forEach { (option, label) ->
                    FilterChip(
                        selected = monetization == option,
                        onClick = { monetization = option },
                        label = { Text(label) },
                    )
                }
            }

            if (monetization == MonetizationType.PAY_PER_VIEW) {
                OutlinedTextField(
                    value = priceDollars,
                    onValueChange = { priceDollars = it },
                    label = { Text("Precio de desbloqueo (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MorphingPillButton(
                text = "Comprimir y Subir en Segundo Plano",
                icon = Icons.Filled.Publish,
                enabled = title.isNotBlank(),
                containerColor = accents.violet,
                contentColor = Color.White,
                testTag = "confirm_publish_button",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onPublish(title, description, tags, visibility, monetization, priceDollars)
                },
            )
        }
    }
}
