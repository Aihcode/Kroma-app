package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MediaType { VIDEO, IMAGE }
enum class PostVisibility { PUBLIC, PRIVATE, SUBSCRIBERS }
enum class MonetizationType { FREE_ADS, PAY_PER_VIEW, LIVE_TIP }
enum class UploadState { QUEUED, COMPRESSING, UPLOADING, PUBLISHED }
enum class LiveStreamStatus { PREPARING, LIVE, ENDED }
enum class RevenueSourceType { VIDEO_VIEWS, EXCLUSIVE_UNLOCK, LIVE_GIFT, PAYOUT_DEBIT }
enum class TransactionStatus { PENDING, AVAILABLE, PAID_OUT }
enum class PayoutStatus { REQUESTED, PENDING, SUCCESS, FAILED }

inline fun <reified T : Enum<T>> safeEnum(raw: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val avatarUrl: String? = null,
    val paypalEmail: String? = null,
    val preferredPayoutCurrency: String = "USD",
    val verifiedCreator: Boolean = false,
    val availableBalanceCents: Long = 0L,
    val pendingBalanceCents: Long = 0L,
    val totalEarnedCents: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "displayName" to displayName.take(80).ifBlank { "Creator" },
            "handle" to handle.take(40).ifBlank { "@creator" },
            "avatarUrl" to avatarUrl?.take(500),
            "paypalEmail" to paypalEmail?.take(160),
            "preferredPayoutCurrency" to preferredPayoutCurrency,
            "verifiedCreator" to false,
            "availableBalanceCents" to 0L,
            "pendingBalanceCents" to 0L,
            "totalEarnedCents" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class MediaPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val creatorName: String = "",
    val title: String = "",
    val description: String = "",
    val mediaType: String = MediaType.VIDEO.name,
    val visibility: String = PostVisibility.PUBLIC.name,
    val monetizationType: String = MonetizationType.FREE_ADS.name,
    val priceCents: Long = 0L,
    val tags: List<String> = emptyList(),
    val mediaUri: String = "",
    val editedExternally: Boolean = false,
    val externalEditorName: String? = null,
    val uploadState: String = UploadState.QUEUED.name,
    val uploadProgress: Long = 0L,
    val viewsCount: Long = 0L,
    val earningsCents: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedMediaType: MediaType get() = safeEnum(mediaType, MediaType.VIDEO)
    val parsedVisibility: PostVisibility get() = safeEnum(visibility, PostVisibility.PUBLIC)
    val parsedMonetizationType: MonetizationType get() = safeEnum(monetizationType, MonetizationType.FREE_ADS)
    val parsedUploadState: UploadState get() = safeEnum(uploadState, UploadState.QUEUED)

    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "creatorName" to creatorName.take(80).ifBlank { "Creator" },
            "title" to title.take(120).ifBlank { "Clip de Estudio" },
            "description" to description.take(600),
            "mediaType" to parsedMediaType.name,
            "visibility" to parsedVisibility.name,
            "monetizationType" to parsedMonetizationType.name,
            "priceCents" to priceCents.coerceIn(0L, 100_000L),
            "tags" to tags.map { it.trim().take(28) }.filter { it.isNotEmpty() }.take(10),
            "mediaUri" to mediaUri.take(800),
            "editedExternally" to editedExternally,
            "externalEditorName" to externalEditorName?.take(80)?.ifBlank { null },
            "uploadState" to parsedUploadState.name,
            "uploadProgress" to uploadProgress.coerceIn(0L, 100L),
            "viewsCount" to 0L,
            "earningsCents" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class LiveStream(
    @DocumentId val id: String = "",
    val userId: String = "",
    val creatorName: String = "",
    val title: String = "",
    val rtmpIngestUrl: String = "",
    val playbackUrl: String = "",
    val status: String = LiveStreamStatus.PREPARING.name,
    val viewerCount: Long = 0L,
    val tipsEarnedCents: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedStatus: LiveStreamStatus get() = safeEnum(status, LiveStreamStatus.PREPARING)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "creatorName" to creatorName.take(80).ifBlank { "Creator" },
        "title" to title.take(120).ifBlank { "Transmisión en Vivo" },
        "rtmpIngestUrl" to rtmpIngestUrl.take(400),
        "playbackUrl" to playbackUrl.take(400),
        "status" to parsedStatus.name,
        "viewerCount" to 0L,
        "tipsEarnedCents" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

@Keep
@IgnoreExtraProperties
data class LiveChatMessage(
    @DocumentId val id: String = "",
    val streamId: String = "",
    val userId: String = "",
    val senderName: String = "",
    val message: String = "",
    val tipCents: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class TransactionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceType: String = RevenueSourceType.VIDEO_VIEWS.name,
    val title: String = "",
    val referenceId: String = "",
    val grossAmountCents: Long = 0L,
    val platformFeeCents: Long = 0L,
    val netAmountCents: Long = 0L,
    val status: String = TransactionStatus.AVAILABLE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    val parsedSourceType: RevenueSourceType get() = safeEnum(sourceType, RevenueSourceType.VIDEO_VIEWS)
    val parsedStatus: TransactionStatus get() = safeEnum(status, TransactionStatus.AVAILABLE)
}

@Keep
@IgnoreExtraProperties
data class PayoutRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val paypalEmail: String = "",
    val amountCents: Long = 0L,
    val currency: String = "USD",
    val payoutBatchId: String = "",
    val status: String = PayoutStatus.REQUESTED.name,
    val failureReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedStatus: PayoutStatus get() = safeEnum(status, PayoutStatus.REQUESTED)
}

fun formatCentsUsd(cents: Long, currency: String = "USD"): String {
    val whole = cents / 100
    val rem = kotlin.math.abs(cents % 100)
    val sign = if (cents < 0 && whole == 0L) "-" else ""
    val formattedWhole = "%,d".format(whole)
    return "$sign$$formattedWhole.${"%02d".format(rem)} $currency"
}
