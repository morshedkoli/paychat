package com.paychat.paychat.feature.profile

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates QR Code bitmaps using ZXing for personal PayChat links and contacts.
 */
object QrCodeHelper {

    /**
     * Generates an [android.graphics.Bitmap] containing the QR code for [content].
     *
     * @param content The string payload (e.g. paychat://pay?phone=... or plain phone)
     * @param size The pixel width and height of the resulting square bitmap
     * @param foreground The ARGB color of the QR code modules (default black)
     * @param background The ARGB color of the background (default white)
     */
    fun generateQrBitmap(
        content: String,
        size: Int = 512,
        foreground: Int = android.graphics.Color.BLACK,
        background: Int = android.graphics.Color.WHITE,
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) foreground else background)
                }
            }
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Creates a standardized PayChat payment/chat link.
     */
    fun createPayChatUri(phone: String, name: String): String {
        val safePhone = phone.trim()
        val encodedName = java.net.URLEncoder.encode(name.trim(), "UTF-8")
        return "paychat://pay?phone=$safePhone&name=$encodedName"
    }
}
