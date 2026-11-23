package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LiveChatMessage
import com.example.data.model.LiveStream
import com.example.data.model.MediaPost
import com.example.data.model.MediaType
import com.example.data.model.MonetizationType
import com.example.data.model.PayoutRecord
import com.example.data.model.PostVisibility
import com.example.data.model.TransactionRecord
import com.example.data.model.UploadState
import com.example.data.model.UserProfile
import com.example.data.repository.StudioRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class MediaDraft(
    val uri: Uri,
    val mediaType: MediaType,
    val editedExternally: Boolean = false,
    val externalEditorName: String? = null,
)

data class StudioActionFeedback(
    val message: String? = null,
    val isError: Boolean = false,
    val isBusy: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StudioViewModel(
    private val repository: StudioRepository,
    private val currentUserId: String,
    currentUser: FirebaseUser?,
) : ViewModel() {

    private val _draft = MutableStateFlow<MediaDraft?>(null)
    val draft: StateFlow<MediaDraft?> = _draft.asStateFlow()

    private val _activeLiveStream = MutableStateFlow<LiveStream?>(null)
    val activeLiveStream: StateFlow<LiveStream?> = _activeLiveStream.asStateFlow()

    private val _feedback = MutableStateFlow(StudioActionFeedback())
    val feedback: StateFlow<StudioActionFeedback> = _feedback.asStateFlow()

    val userProfile: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiStateFlow("Error cargando perfil")

    val mediaPosts: StateFlow<UiState<List<MediaPost>>> =
        repository.observeMediaPosts(currentUserId)
            .toUiStateFlow("Error cargando publicaciones")

    val liveStreams: StateFlow<UiState<List<LiveStream>>> =
        repository.observeLiveStreams(currentUserId)
            .toUiStateFlow("Error cargando transmisiones")

    val transactions: StateFlow<UiState<List<TransactionRecord>>> =
        repository.observeTransactions(currentUserId)
            .toUiStateFlow("Error cargando transacciones")

    val payouts: StateFlow<UiState<List<PayoutRecord>>> =
        repository.observePayouts(currentUserId)
            .toUiStateFlow("Error cargando retiros PayPal")

    val activeLiveChat: StateFlow<List<LiveChatMessage>> =
        _activeLiveStream.flatMapLatest { stream ->
            if (stream == null) {
                flowOf(emptyList())
            } else {
                repository.observeLiveChat(stream.id, currentUserId)
                    .catch { error ->
                        val fsErr = unwrapFirestoreError(error)
                        if (fsErr == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing live chat", fsErr ?: error)
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    init {
        if (currentUser != null) {
            viewModelScope.launch {
                repository.ensureCreatorProfile(currentUser)
            }
        }
    }

    fun setDraftMedia(
        uri: Uri,
        mediaType: MediaType,
        editedExternally: Boolean = false,
        externalEditorName: String? = null,
    ) {
        _draft.value = MediaDraft(
            uri = uri,
            mediaType = mediaType,
            editedExternally = editedExternally,
            externalEditorName = externalEditorName,
        )
    }

    fun markDraftSentToExternalEditor(editorName: String) {
        _draft.update { current ->
            current?.copy(editedExternally = true, externalEditorName = editorName)
        }
    }

    fun clearDraft() {
        _draft.value = null
    }

    fun clearFeedback() {
        _feedback.value = StudioActionFeedback()
    }

    fun publishDraft(
        title: String,
        description: String,
        tagsRaw: String,
        visibility: PostVisibility,
        monetizationType: MonetizationType,
        priceDollarsInput: String,
    ) {
        val currentDraft = _draft.value ?: return
        val creatorName = (userProfile.value as? UiState.Success)?.data?.displayName ?: "Creador Kroma"
        val parsedTags = tagsRaw.split(",")
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .take(10)
        val priceCents = if (monetizationType == MonetizationType.PAY_PER_VIEW) {
            ((priceDollarsInput.toDoubleOrNull() ?: 0.0) * 100.0).toLong().coerceIn(99L, 100_000L)
        } else {
            0L
        }

        viewModelScope.launch {
            _feedback.value = StudioActionFeedback(isBusy = true)
            val newPost = MediaPost(
                userId = currentUserId,
                creatorName = creatorName,
                title = title.trim(),
                description = description.trim(),
                mediaType = currentDraft.mediaType.name,
                visibility = visibility.name,
                monetizationType = monetizationType.name,
                priceCents = priceCents,
                tags = parsedTags,
                mediaUri = currentDraft.uri.toString(),
                editedExternally = currentDraft.editedExternally,
                externalEditorName = currentDraft.externalEditorName,
                uploadState = UploadState.COMPRESSING.name,
                uploadProgress = 25L,
            )

            repository.createMediaPost(newPost)
                .onSuccess { postId ->
                    _draft.value = null
                    _feedback.value = StudioActionFeedback(
                        message = "Publicación en cola: compresión y subida en segundo plano iniciadas."
                    )
                    // Background upload progression persisted to Firestore
                    launch {
                        delay(700)
                        repository.updateUploadProgress(postId, UploadState.UPLOADING, 65)
                        delay(800)
                        repository.updateUploadProgress(postId, UploadState.PUBLISHED, 100)
                    }
                }
                .onFailure { err ->
                    _feedback.value = StudioActionFeedback(
                        message = err.localizedMessage ?: "No se pudo publicar el contenido.",
                        isError = true,
                    )
                }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deleteMediaPost(postId)
                .onSuccess {
                    _feedback.value = StudioActionFeedback(message = "Publicación eliminada.")
                }
                .onFailure { err ->
                    _feedback.value = StudioActionFeedback(
                        message = err.localizedMessage ?: "Error al eliminar publicación.",
                        isError = true,
                    )
                }
        }
    }

    fun startLiveBroadcast(title: String) {
        val creatorName = (userProfile.value as? UiState.Success)?.data?.displayName ?: "Creador Kroma"
        viewModelScope.launch {
            _feedback.value = StudioActionFeedback(isBusy = true)
            repository.startLiveStream(title = title, creatorName = creatorName)
                .onSuccess { stream ->
                    _activeLiveStream.value = stream
                    _feedback.value = StudioActionFeedback(message = "Transmisión RTMP activa.")
                }
                .onFailure { err ->
                    _feedback.value = StudioActionFeedback(
                        message = err.localizedMessage ?: "No se pudo iniciar la transmisión.",
                        isError = true,
                    )
                }
        }
    }

    fun stopLiveBroadcast() {
        val active = _activeLiveStream.value ?: return
        viewModelScope.launch {
            repository.endLiveStream(active.id)
                .onSuccess {
                    _activeLiveStream.value = null
                    _feedback.value = StudioActionFeedback(message = "Transmisión finalizada y guardada.")
                }
                .onFailure { err ->
                    _feedback.value = StudioActionFeedback(
                        message = err.localizedMessage ?: "Error al cerrar la transmisión.",
                        isError = true,
                    )
                }
        }
    }

    fun sendLiveChat(message: String) {
        val active = _activeLiveStream.value ?: return
        val senderName = (userProfile.value as? UiState.Success)?.data?.displayName ?: "Creador"
        viewModelScope.launch {
            repository.sendLiveChatMessage(active.id, senderName, message)
                .onFailure { err ->
                    _feedback.value = StudioActionFeedback(
                        message = err.localizedMessage ?: "No se pudo enviar el mensaje.",
                        isError = true,
                    )
                }
        }
    }

    fun updatePayPalAccount(
        displayName: String,
        handle: String,
        paypalEmail: String,
        currency: String,
    ) {
        viewModelScope.launch {
            _feedback.value = StudioActionFeedback(isBusy = true)
            repository.updatePayPalSettings(currentUserId, displayName, handle, paypalEmail, currency)
                .onSuccess {
                    _feedback.value = StudioActionFeedback(message = "Cuenta PayPal y perfil actualizados.")
                }
                .onFailure { err ->
                    _feedback.value = StudioActionFeedback(
                        message = err.localizedMessage ?: "Error guardando configuración PayPal.",
                        isError = true,
                    )
                }
        }
    }

    fun requestPayPalPayout(amountDollarsInput: String, paypalEmail: String, currency: String) {
        val cents = ((amountDollarsInput.toDoubleOrNull() ?: 0.0) * 100.0).toInt()
        viewModelScope.launch {
            _feedback.value = StudioActionFeedback(isBusy = true)
            repository.requestPayPalPayout(cents, paypalEmail, currency)
                .onSuccess { resp ->
                    _feedback.value = StudioActionFeedback(
                        message = "Retiro enviado a PayPal. Batch ID: ${resp.payoutBatchId ?: resp.payoutId}"
                    )
                }
                .onFailure { err ->
                    _feedback.value = StudioActionFeedback(
                        message = err.localizedMessage ?: "No se pudo procesar el retiro PayPal.",
                        isError = true,
                    )
                }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(errorPrefix: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, errorPrefix, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: errorPrefix))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "StudioViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
