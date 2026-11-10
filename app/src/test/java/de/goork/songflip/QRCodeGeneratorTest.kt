package de.goork.songflip

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QRCodeGeneratorTest {

    @Test
    fun testQrCodeMatrixGeneration() {
        val content = "https://songflip.link/s/abc123xyz"
        val hints = mapOf(
            com.google.zxing.EncodeHintType.MARGIN to 1,
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256, hints)
        assertNotNull(bitMatrix)
        assertTrue(bitMatrix.width > 0)
        assertTrue(bitMatrix.height > 0)
    }
}
