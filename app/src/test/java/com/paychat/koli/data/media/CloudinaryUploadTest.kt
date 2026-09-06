package com.paychat.koli.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun signature(resourceType: String = CloudinaryUpload.IMAGE) = UploadSignature(
    cloudName = "paychat",
    apiKey = "123456789",
    publicId = "paychat/uidMe/msg-1",
    timestamp = 1_700_000_000L,
    signature = "abcdef",
    resourceType = resourceType,
)

class CloudinaryUploadTest {

    @Test
    fun `images and audio go to different endpoints`() {
        assertEquals(
            "https://api.cloudinary.com/v1_1/paychat/image/upload",
            CloudinaryUpload.url(signature(CloudinaryUpload.IMAGE)),
        )
        assertEquals(
            "https://api.cloudinary.com/v1_1/paychat/video/upload",
            CloudinaryUpload.url(signature(CloudinaryUpload.VIDEO)),
        )
    }

    @Test
    fun `a voice note is uploaded as a video resource`() {
        // Cloudinary has no audio resource type; audio is carried by video.
        assertEquals(CloudinaryUpload.VIDEO, CloudinaryUpload.resourceTypeFor(isVoice = true))
        assertEquals(CloudinaryUpload.IMAGE, CloudinaryUpload.resourceTypeFor(isVoice = false))
    }

    @Test
    fun `only the signed parameters are sent`() {
        // The Cloud Function signs public_id and timestamp. Sending any other
        // signed parameter would make Cloudinary reject the upload, so the set
        // is asserted exactly rather than loosely.
        assertEquals(
            setOf("public_id", "timestamp", "signature", "api_key"),
            CloudinaryUpload.formFields(signature()).keys,
        )
    }

    @Test
    fun `the fields carry the values from the signature`() {
        val fields = CloudinaryUpload.formFields(signature())
        assertEquals("paychat/uidMe/msg-1", fields["public_id"])
        assertEquals("1700000000", fields["timestamp"])
        assertEquals("abcdef", fields["signature"])
        assertEquals("123456789", fields["api_key"])
    }

    @Test
    fun `the api secret is never part of the request`() {
        val fields = CloudinaryUpload.formFields(signature())
        assertFalse(fields.keys.any { it.contains("secret", ignoreCase = true) })
        assertTrue(fields.values.none { it.contains("secret", ignoreCase = true) })
    }
}
