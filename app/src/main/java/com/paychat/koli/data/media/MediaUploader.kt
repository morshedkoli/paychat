package com.paychat.koli.data.media

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UploadedMedia(
    val secureUrl: String,
    val publicId: String,
)

/**
 * Uploads one attachment to Cloudinary.
 *
 * The app never holds the Cloudinary API secret. It asks a Cloud Function to
 * sign the upload, then sends the file straight to Cloudinary, so the file
 * itself does not pass through Firebase and does not count against its quota.
 */
@Singleton
class MediaUploader @Inject constructor(
    private val functions: FirebaseFunctions,
    private val client: OkHttpClient,
) {

    suspend fun upload(file: File, messageId: String, isVoice: Boolean): Result<UploadedMedia> =
        runCatching {
            require(file.exists()) { "the attachment is no longer on this device" }
            val signature = requestSignature(file, messageId, isVoice)
            send(file, signature)
        }

    private suspend fun requestSignature(
        file: File,
        messageId: String,
        isVoice: Boolean,
    ): UploadSignature {
        val response = functions.getHttpsCallable(SIGN_FUNCTION)
            .call(
                mapOf(
                    "resourceType" to CloudinaryUpload.resourceTypeFor(isVoice),
                    "messageId" to messageId,
                    "bytes" to file.length(),
                )
            )
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = response.getData() as? Map<String, Any?>
            ?: error("the signing function returned nothing")

        return UploadSignature(
            cloudName = data["cloudName"] as String,
            apiKey = data["apiKey"] as String,
            publicId = data["publicId"] as String,
            timestamp = (data["timestamp"] as Number).toLong(),
            signature = data["signature"] as String,
            resourceType = data["resourceType"] as String,
        )
    }

    private suspend fun send(file: File, signature: UploadSignature): UploadedMedia =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .apply {
                    CloudinaryUpload.formFields(signature).forEach { (name, value) ->
                        addFormDataPart(name, value)
                    }
                    addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody(OCTET_STREAM.toMediaType()),
                    )
                }
                .build()

            val request = Request.Builder()
                .url(CloudinaryUpload.url(signature))
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Cloudinary rejected the upload: ${response.code}")
                }
                val json = JSONObject(payload)
                UploadedMedia(
                    secureUrl = json.getString("secure_url"),
                    publicId = json.getString("public_id"),
                )
            }
        }

    private companion object {
        const val SIGN_FUNCTION = "signMediaUpload"
        const val OCTET_STREAM = "application/octet-stream"
    }
}
