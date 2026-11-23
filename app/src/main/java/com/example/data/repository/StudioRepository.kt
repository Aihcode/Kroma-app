package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.LiveChatMessage
import com.example.data.model.LiveStream
import com.example.data.model.LiveStreamStatus
import com.example.data.model.MediaPost
import com.example.data.model.PayoutRecord
import com.example.data.model.TransactionRecord
import com.example.data.model.UploadState
import com.example.data.model.UserProfile
import com.example.data.remote.PayPalPayoutApiService
import com.example.data.remote.PayoutApiRequest
import com.example.data.remote.PayoutApiResponse
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class StudioRepository(
    private val db: FirebaseFirestore,
    private val payoutApi: PayPalPayoutApiService,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        payoutApi = PayPalPayoutApiService.create(
            context.applicationContext.getString(R.string.cloud_functions_base_url)
        ),
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    suspend fun ensureCreatorProfile(user: FirebaseUser): Result<Unit> {
        val docPath = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val baseName = user.displayName?.trim().takeUnless { it.isNullOrBlank() } ?: "Creador Kroma"
                val sanitizedHandle = "@" + baseName.lowercase()
                    .replace(Regex("[^a-z0-9]"), "")
                    .take(18)
                    .ifBlank { "creator_${uid.take(6)}" }
                val profile = UserProfile(
                    userId = uid,
                    displayName = baseName.take(80),
                    handle = sanitizedHandle,
                    avatarUrl = user.photoUrl?.toString()?.take(500),
                    paypalEmail = user.email?.take(160),
                    preferredPayoutCurrency = "USD",
                )
                docRef.set(profile.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> logAndRethrow(error, OperationType.GET, path, userId) }
        )
    }

    suspend fun updatePayPalSettings(
        userId: String,
        displayName: String,
        handle: String,
        paypalEmail: String,
        currency: String,
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user profile update" }
            val cleanName = displayName.trim().take(80)
            val cleanHandle = handle.trim().let { if (it.startsWith("@")) it else "@$it" }.take(40)
            val cleanEmail = paypalEmail.trim().take(160)
            require(cleanName.isNotEmpty()) { "El nombre del creador no puede estar vacío." }
            require(cleanHandle.length >= 2) { "El handle debe tener al menos 2 caracteres." }
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Ingresa un correo de PayPal válido." }
            require(currency in listOf("USD", "EUR", "MXN")) { "Moneda no soportada." }

            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "handle" to cleanHandle,
                    "paypalEmail" to cleanEmail,
                    "preferredPayoutCurrency" to currency,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeMediaPosts(userId: String): Flow<List<MediaPost>> = flow {
        val path = "media_posts"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MediaPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun createMediaPost(post: MediaPost): Result<String> {
        var docPath = "media_posts"
        return try {
            val uid = requireUserId()
            require(post.title.isNotBlank()) { "El título es obligatorio." }
            require(post.mediaUri.isNotBlank()) { "Debes capturar o seleccionar un archivo multimedia." }
            val docRef = db.collection("media_posts").document()
            docPath = docRef.path
            val payload = post.copy(userId = uid).toCreateMap()
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUploadProgress(
        postId: String,
        state: UploadState,
        progress: Int,
    ): Result<Unit> {
        val docPath = "media_posts/$postId"
        return try {
            requireUserId()
            require(postId.isNotBlank())
            db.document(docPath).update(
                mapOf(
                    "uploadState" to state.name,
                    "uploadProgress" to progress.coerceIn(0, 100),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMediaPost(postId: String): Result<Unit> {
        val docPath = "media_posts/$postId"
        return try {
            requireUserId()
            require(postId.isNotBlank())
            db.document(docPath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeLiveStreams(userId: String): Flow<List<LiveStream>> = flow {
        val path = "live_streams"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(LiveStream::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun startLiveStream(title: String, creatorName: String): Result<LiveStream> {
        var docPath = "live_streams"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120).ifBlank { "Live Studio Session" }
            val docRef = db.collection("live_streams").document()
            docPath = docRef.path
            val streamKey = "kroma_${docRef.id.take(12)}"
            val stream = LiveStream(
                id = docRef.id,
                userId = uid,
                creatorName = creatorName.take(80).ifBlank { "Creator" },
                title = cleanTitle,
                rtmpIngestUrl = "rtmps://live.kroma-ingest.net:443/app/$streamKey",
                playbackUrl = "https://cdn.kroma-stream.net/hls/$streamKey.m3u8",
                status = LiveStreamStatus.LIVE.name,
                viewerCount = 0L,
                tipsEarnedCents = 0L,
            )
            docRef.set(stream.toCreateMap()).await()
            Result.success(stream)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun endLiveStream(streamId: String): Result<Unit> {
        val docPath = "live_streams/$streamId"
        return try {
            requireUserId()
            db.document(docPath).update(
                mapOf(
                    "status" to LiveStreamStatus.ENDED.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeLiveChat(streamId: String, currentUserId: String): Flow<List<LiveChatMessage>> = flow {
        val path = "live_streams/$streamId/chat_messages"
        emitAll(
            db.collection("live_streams").document(streamId).collection("chat_messages")
                .whereEqualTo("streamId", streamId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(LiveChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path, currentUserId) }
        )
    }

    suspend fun sendLiveChatMessage(streamId: String, senderName: String, message: String): Result<Unit> {
        val docPath = "live_streams/$streamId/chat_messages"
        return try {
            val uid = requireUserId()
            val cleanMsg = message.trim().take(300)
            require(cleanMsg.isNotEmpty()) { "El mensaje no puede estar vacío." }
            val docRef = db.collection("live_streams").document(streamId)
                .collection("chat_messages").document()
            docRef.set(
                mapOf(
                    "streamId" to streamId,
                    "userId" to uid,
                    "senderName" to senderName.take(80).ifBlank { "Creator" },
                    "message" to cleanMsg,
                    "tipCents" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun observeTransactions(userId: String): Flow<List<TransactionRecord>> = flow {
        val path = "transactions"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path, userId) }
        )
    }

    fun observePayouts(userId: String): Flow<List<PayoutRecord>> = flow {
        val path = "payouts"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PayoutRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun requestPayPalPayout(
        amountCents: Int,
        paypalEmail: String,
        currency: String,
    ): Result<PayoutApiResponse> {
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("Debes iniciar sesión con Google para solicitar un retiro.")
            require(amountCents >= 1000) { "El monto mínimo de retiro es $10.00 USD." }
            require(paypalEmail.contains("@") && paypalEmail.contains(".")) {
                "Correo de PayPal inválido."
            }

            val tokenResult = user.getIdToken(false).await()
            val idToken = tokenResult.token
                ?: throw IllegalStateException("No se pudo obtener el token de autenticación.")

            val response = payoutApi.requestPayPalPayout(
                bearerToken = "Bearer $idToken",
                request = PayoutApiRequest(
                    amountCents = amountCents,
                    paypalEmail = paypalEmail.trim(),
                    currency = currency,
                ),
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errMsg = response.errorBody()?.string()?.take(220)
                    ?: "Servicio Cloud Functions no desplegado (HTTP ${response.code()}). Despliega functions/src/index.ts con credenciales PayPal."
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun logAndRethrow(
        error: Throwable,
        op: OperationType,
        path: String,
        userId: String,
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }
}
