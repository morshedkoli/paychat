package com.paychat.paychat.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodeHelperTest {

    @Test
    fun createPayChatUri_formatsExpectedSchemeAndParameters() {
        val uri = QrCodeHelper.createPayChatUri("+8801712345678", "Murshed Alam")
        assertEquals("paychat://pay?phone=+8801712345678&name=Murshed+Alam", uri)
    }

    @Test
    fun generateQrBitmap_withBlankContent_returnsNull() {
        assertNull(QrCodeHelper.generateQrBitmap(""))
        assertNull(QrCodeHelper.generateQrBitmap("   "))
    }
}
