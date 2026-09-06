package com.paychat.paychat.data.media

/**
 * What the signing function hands back. The API secret never appears here, and
 * never reaches the app at all.
 */
data class UploadSignature(
    val cloudName: String,
    val apiKey: String,
    val publicId: String,
    val timestamp: Long,
    val signature: String,
    val resourceType: String,
)

/**
 * Assembles the Cloudinary upload request from a signature.
 *
 * Pure, so the part most likely to be wrong — which fields are sent, and that
 * they match what was signed — is unit tested without a network.
 */
object CloudinaryUpload {

    const val IMAGE = "image"
    const val VIDEO = "video"

    fun url(signature: UploadSignature): String =
        "https://api.cloudinary.com/v1_1/${signature.cloudName}/${signature.resourceType}/upload"

    /**
     * The form fields sent alongside the file.
     *
     * Only `public_id` and `timestamp` are signed, so only those two may be
     * sent as signed parameters. Adding another signed field here without
     * adding it to the function's signature would make Cloudinary reject the
     * upload, which is why the set is defined in one place.
     */
    fun formFields(signature: UploadSignature): Map<String, String> = mapOf(
        "public_id" to signature.publicId,
        "timestamp" to signature.timestamp.toString(),
        "signature" to signature.signature,
        "api_key" to signature.apiKey,
    )

    fun resourceTypeFor(isVoice: Boolean): String = if (isVoice) VIDEO else IMAGE
}
