package com.example.interop

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import com.example.data.model.MediaType
import java.io.File

data class InstalledEditorApp(
    val displayName: String,
    val packageName: String,
    val installed: Boolean,
)

data class SharedBackMedia(
    val localUri: Uri,
    val mediaType: MediaType,
    val sourceHint: String,
)

object ExternalEditorBridge {
    private val KNOWN_EDITORS = listOf(
        "CapCut" to "com.lemon.lvoverseas",
        "VN Editor" to "com.frontrow.vlog",
        "InShot" to "com.camerasideas.instashot",
        "YouCut" to "com.camerasideas.trimmer",
    )

    fun detectInstalledEditors(context: Context): List<InstalledEditorApp> {
        val pm = context.packageManager
        return KNOWN_EDITORS.map { (name, pkg) ->
            val isInstalled = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            InstalledEditorApp(displayName = name, packageName = pkg, installed = isInstalled)
        }
    }

    /**
     * Copies any content:// or file:// Uri into app-internal filesDir/studio_media
     * and returns a FileProvider content:// Uri safe for external editor grants.
     */
    fun copyToStudioFileProviderUri(
        context: Context,
        sourceUri: Uri,
        mediaType: MediaType,
    ): Uri? {
        return try {
            val mediaDir = File(context.filesDir, "studio_media").apply { mkdirs() }
            val ext = if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
            val destFile = File(mediaDir, "kroma_clip_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Launches an external free editor (CapCut, VN, InShot, YouCut, or system chooser)
     * using ACTION_EDIT first and falling back cleanly to ACTION_SEND.
     */
    fun launchExternalEditor(
        context: Context,
        mediaUri: Uri,
        mediaType: MediaType,
        preferredPackage: String? = null,
    ): Result<String> {
        val mimeType = if (mediaType == MediaType.VIDEO) "video/*" else "image/*"
        val shareableUri = if (mediaUri.authority == "${context.packageName}.fileprovider") {
            mediaUri
        } else {
            copyToStudioFileProviderUri(context, mediaUri, mediaType) ?: mediaUri
        }

        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(shareableUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (!preferredPackage.isNullOrBlank()) {
                setPackage(preferredPackage)
            }
        }

        val pm = context.packageManager
        val canHandleEdit = editIntent.resolveActivity(pm) != null
        if (canHandleEdit) {
            val chooser = if (preferredPackage == null) {
                Intent.createChooser(editIntent, "Editar en App Externa").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                editIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            context.startActivity(chooser)
            return Result.success(preferredPackage ?: "Editor del sistema")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareableUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!preferredPackage.isNullOrBlank()) {
                setPackage(preferredPackage)
            }
        }

        return if (sendIntent.resolveActivity(pm) != null) {
            val chooser = Intent.createChooser(sendIntent, "Enviar a Editor Externo (CapCut / VN / InShot)")
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
            Result.success(preferredPackage ?: "Menú Compartir")
        } else {
            Result.failure(
                IllegalStateException(
                    "No se detectó un editor externo compatible instalado (ej. CapCut, VN, InShot o YouCut)."
                )
            )
        }
    }

    /**
     * Parses inbound ACTION_SEND intents when the user exports from CapCut/VN/InShot back to Kroma Studio.
     */
    fun extractSharedMedia(context: Context, intent: Intent?): SharedBackMedia? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val mimeType = intent.type ?: return null
        val mediaType = when {
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("image/") -> MediaType.IMAGE
            else -> return null
        }

        val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: return null

        val persistedUri = copyToStudioFileProviderUri(context, streamUri, mediaType) ?: streamUri
        return SharedBackMedia(
            localUri = persistedUri,
            mediaType = mediaType,
            sourceHint = "Editor Externo",
        )
    }

    fun createRecordingDestinationFile(context: Context, mediaType: MediaType): File {
        val dir = File(context.filesDir, "studio_media").apply { mkdirs() }
        val ext = if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
        return File(dir, "kroma_capture_${System.currentTimeMillis()}.$ext")
    }

    fun uriForStudioFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
