package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LiveStream
import com.example.data.model.MediaPost
import com.example.data.model.MediaType
import com.example.data.model.MonetizationType
import com.example.data.model.PostVisibility
import com.example.data.model.UploadState
import com.example.data.model.formatCentsUsd
import com.example.interop.ExternalEditorBridge
import com.example.ui.components.ExpressiveOrganicLoader
import com.example.ui.components.ExpressiveSurfaceCard
import com.example.ui.components.MorphingPillButton
import com.example.ui.theme.LocalKromaAccents
import com.example.ui.theme.PillShape
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun MediaLibraryScreen(
    viewModel: StudioViewModel,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accents = LocalKromaAccents.current
    val postsState by viewModel.mediaPosts.collectAsStateWithLifecycle()
    val streamsState by viewModel.liveStreams.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.nav_library),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Videos, fotos exclusivas, progreso de subida y transmisiones archivadas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val state = postsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ExpressiveOrganicLoader(size = 44.dp, color = accents.violet)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        ExpressiveSurfaceCard(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item {
                            ExpressiveSurfaceCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesomeMotion,
                                        contentDescription = null,
                                        tint = accents.violet,
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Text(
                                        text = "Tu estudio aún está vacío",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Graba un clip o importa una foto desde el Estudio, edítala en tu app externa favorita y publícala para empezar a monetizar.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    MorphingPillButton(
                                        text = "Ir al Estudio",
                                        icon = Icons.Filled.PlayCircle,
                                        containerColor = accents.violet,
                                        onClick = onGoToStudio,
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.data, key = { it.id }) { post ->
                            MediaPostCard(
                                post = post,
                                onEditExternal = {
                                    ExternalEditorBridge.launchExternalEditor(
                                        context = context,
                                        mediaUri = Uri.parse(post.mediaUri),
                                        mediaType = post.parsedMediaType,
                                    )
                                },
                                onDelete = { viewModel.deletePost(post.id) },
                            )
                        }
                    }
                }
            }

            if (streamsState is UiState.Success) {
                val streams = (streamsState as UiState.Success<List<LiveStream>>).data
                if (streams.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Historial de Transmisiones en Vivo",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    items(streams, key = { it.id }) { stream ->
                        ExpressiveSurfaceCard(
                            borderColor = accents.coral.copy(alpha = 0.4f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LiveTv,
                                        contentDescription = null,
                                        tint = accents.coral,
                                    )
                                    Column {
                                        Text(
                                            text = stream.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "${stream.parsedStatus.name} · ${stream.viewerCount} espectadores",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = formatCentsUsd(stream.tipsEarnedCents),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accents.emerald,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaPostCard(
    post: MediaPost,
    onEditExternal: () -> Unit,
    onDelete: () -> Unit,
) {
    val accents = LocalKromaAccents.current
    ExpressiveSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = Uri.parse(post.mediaUri),
                    contentDescription = post.title,
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
                        text = post.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (post.description.isNotBlank()) {
                        Text(
                            text = post.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Text(
                        text = "${post.viewsCount} vistas · ${formatCentsUsd(post.earningsCents)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.emerald,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_post_${post.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar publicación",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (post.parsedUploadState != UploadState.PUBLISHED) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = when (post.parsedUploadState) {
                            UploadState.QUEUED -> "En cola..."
                            UploadState.COMPRESSING -> "Comprimiendo media (${post.uploadProgress}%)..."
                            UploadState.UPLOADING -> "Subiendo a la nube (${post.uploadProgress}%)..."
                            UploadState.PUBLISHED -> "Publicado"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.cobalt,
                    )
                    LinearProgressIndicator(
                        progress = { (post.uploadProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(PillShape),
                        color = accents.violet,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTagChip(
                    text = if (post.parsedMediaType == MediaType.VIDEO) "VIDEO" else "FOTO",
                    color = accents.violet,
                )
                StatusTagChip(
                    text = when (post.parsedVisibility) {
                        PostVisibility.PUBLIC -> "Público"
                        PostVisibility.SUBSCRIBERS -> "Suscriptores"
                        PostVisibility.PRIVATE -> "Privado"
                    },
                    color = accents.cobalt,
                )
                StatusTagChip(
                    text = when (post.parsedMonetizationType) {
                        MonetizationType.FREE_ADS -> "Anuncios"
                        MonetizationType.PAY_PER_VIEW -> "PPV ${formatCentsUsd(post.priceCents)}"
                        MonetizationType.LIVE_TIP -> "Propinas"
                    },
                    color = accents.emerald,
                )
                if (post.editedExternally) {
                    StatusTagChip(
                        text = "Editado: ${post.externalEditorName ?: "Externo"}",
                        color = accents.amber,
                    )
                }
            }

            OutlinedButton(
                onClick = onEditExternal,
                shape = PillShape,
                border = BorderStroke(1.dp, accents.cobalt.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    tint = accents.cobalt,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Re-editar en App Externa", color = accents.cobalt)
            }
        }
    }
}

@Composable
private fun StatusTagChip(text: String, color: Color) {
    Surface(
        shape = PillShape,
        color = color.copy(alpha = 0.16f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
