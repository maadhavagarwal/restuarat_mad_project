package com.restaurant.mobile.delivery

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ImageValidationResult(
    val isValid: Boolean,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Enterprise Production Cloud Storage Engine for Admin Restaurant Logos & Custom Dish Images.
 *
 * Responsibilities:
 * 1. Safely streams Android `content://` URIs via [ContentResolver.openInputStream].
 * 2. Validates image MIME types (JPG, PNG, WEBP, GIF, HEIC) and enforced size limits (5MB logo, 10MB dish).
 * 3. Fallback header magic byte sniffing when content resolver does not provide a mime type.
 * 4. Generates unique storage paths preventing object collisions.
 * 5. Emits granular upload progress.
 * 6. Returns permanent HTTPS download URLs (never local content URIs).
 * 7. Safe cancellation and error recovery without leaking streams or leaving partial state.
 */
class FirebaseStorageEngine(
    private val context: Context,
    private val storage: FirebaseStorage? = null
) {
    companion object {
        private const val TAG = "FirebaseStorageEngine"

        const val MAX_LOGO_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
        const val MAX_DISH_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

        val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic"
        )

        /**
         * Pure function for validating image headers and mime types, unit testable without Android ContentResolver.
         */
        fun validateImageBytes(
            headerBytes: ByteArray,
            reportedMimeType: String?,
            sizeBytes: Long,
            maxSizeBytes: Long
        ): ImageValidationResult {
            val detectedMime = reportedMimeType?.lowercase()
                ?.takeIf { it in SUPPORTED_MIME_TYPES }
                ?: detectMimeFromHeader(headerBytes)

            if (detectedMime == null || detectedMime !in SUPPORTED_MIME_TYPES) {
                return ImageValidationResult(
                    isValid = false,
                    mimeType = reportedMimeType,
                    sizeBytes = sizeBytes,
                    errorMessage = "Unsupported file type '${reportedMimeType ?: "unknown"}'. Only JPG, PNG, WEBP, and GIF images are allowed."
                )
            }

            if (sizeBytes > maxSizeBytes) {
                val maxMb = maxSizeBytes / (1024 * 1024)
                val currentMb = String.format(java.util.Locale.US, "%.1f", sizeBytes.toDouble() / (1024 * 1024))
                return ImageValidationResult(
                    isValid = false,
                    mimeType = detectedMime,
                    sizeBytes = sizeBytes,
                    errorMessage = "Image size (${currentMb}MB) exceeds the maximum allowed limit of ${maxMb}MB."
                )
            }

            return ImageValidationResult(
                isValid = true,
                mimeType = detectedMime,
                sizeBytes = sizeBytes
            )
        }

        fun detectMimeFromHeader(header: ByteArray): String? {
            if (header.size < 4) return null
            // JPEG: FF D8 FF
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                return "image/jpeg"
            }
            // PNG: 89 50 4E 47
            if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
                return "image/png"
            }
            // GIF: 47 49 46 38
            if (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x38.toByte()) {
                return "image/gif"
            }
            // WEBP: RIFF .... WEBP
            if (header.size >= 12 &&
                header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
                header[8] == 0x57.toByte() && header[9] == 0x45.toByte() && header[10] == 0x42.toByte() && header[11] == 0x50.toByte()
            ) {
                return "image/webp"
            }
            return null
        }

        fun getExtensionForMimeType(mimeType: String?): String {
            return when (mimeType?.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/heic" -> "heic"
                else -> "jpg"
            }
        }

        fun generateStoragePath(restaurantId: String, type: String, id: String, ext: String): String {
            val timestamp = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString().take(8)
            return if (type == "logo") {
                "restaurants/$restaurantId/logos/logo_${timestamp}_$uuid.$ext"
            } else {
                "restaurants/$restaurantId/dishes/dish_${id}_${timestamp}_$uuid.$ext"
            }
        }
    }

    fun validateImageUri(
        uri: Uri,
        maxSizeBytes: Long
    ): ImageValidationResult {
        val cr = context.contentResolver
        val reportedMime = cr.getType(uri)

        // Read size from OpenableColumns
        val sizeBytes = try {
            cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else null
            } ?: runCatching {
                cr.openInputStream(uri)?.use { it.available().toLong() }
            }.getOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }

        // Read first 12 bytes for header verification
        val header = ByteArray(12)
        try {
            cr.openInputStream(uri)?.use { stream ->
                stream.read(header)
            }
        } catch (e: Exception) {
            return ImageValidationResult(
                isValid = false,
                mimeType = reportedMime,
                sizeBytes = sizeBytes,
                errorMessage = "Cannot read image data: ${e.message}"
            )
        }

        return validateImageBytes(header, reportedMime, sizeBytes, maxSizeBytes)
    }

    suspend fun uploadRestaurantLogo(
        restaurantId: String,
        imageUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val validation = validateImageUri(imageUri, MAX_LOGO_SIZE_BYTES)
        if (!validation.isValid) {
            return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid logo image"))
        }

        val ext = getExtensionForMimeType(validation.mimeType)
        val storagePath = generateStoragePath(restaurantId, "logo", restaurantId, ext)
        uploadStream(imageUri, storagePath, validation.mimeType ?: "image/jpeg", onProgress)
    }

    suspend fun uploadDishImage(
        restaurantId: String,
        dishId: String,
        imageUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val validation = validateImageUri(imageUri, MAX_DISH_SIZE_BYTES)
        if (!validation.isValid) {
            return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid dish image"))
        }

        val ext = getExtensionForMimeType(validation.mimeType)
        val storagePath = generateStoragePath(restaurantId, "dish", dishId, ext)
        uploadStream(imageUri, storagePath, validation.mimeType ?: "image/jpeg", onProgress)
    }

    private suspend fun uploadStream(
        uri: Uri,
        storagePath: String,
        mimeType: String,
        onProgress: (Float) -> Unit
    ): Result<String> {
        val storageInstance = storage ?: try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                return Result.failure(IllegalStateException("Firebase is not initialized on this device."))
            }
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Failed to get Firebase Storage instance: ${e.message}"))
        }

        val stream: InputStream = try {
            context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IOException("ContentResolver returned null stream for URI: $uri"))
        } catch (e: Exception) {
            return Result.failure(IOException("Failed to open stream for URI $uri: ${e.message}"))
        }

        return try {
            val storageRef = storageInstance.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCacheControl("public, max-age=2592000") // 30 days cache
                .build()

            val uploadTask = storageRef.putStream(stream, metadata)

            uploadTask.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0) {
                    val progress = snapshot.bytesTransferred.toFloat() / total.toFloat()
                    onProgress(progress.coerceIn(0f, 1f))
                }
            }

            suspendCancellableCoroutine<UploadTask.TaskSnapshot> { cont ->
                uploadTask.addOnSuccessListener { snap ->
                    if (cont.isActive) cont.resume(snap)
                }
                uploadTask.addOnFailureListener { ex ->
                    if (cont.isActive) cont.resumeWithException(ex)
                }
                uploadTask.addOnCanceledListener {
                    cont.cancel()
                }
                cont.invokeOnCancellation {
                    uploadTask.cancel()
                }
            }

            val downloadUrl = suspendCancellableCoroutine<Uri> { cont ->
                storageRef.downloadUrl.addOnSuccessListener { dUrl ->
                    if (cont.isActive) cont.resume(dUrl)
                }.addOnFailureListener { ex ->
                    if (cont.isActive) cont.resumeWithException(ex)
                }.addOnCanceledListener {
                    cont.cancel()
                }
            }.toString()

            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Storage upload failed for $storagePath: ${e.message}", e)
            Result.failure(e)
        } finally {
            runCatching { stream.close() }
        }
    }
}
