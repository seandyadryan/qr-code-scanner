package com.deploydulupulangnanti.qrcodescanner

import org.junit.Assert.*
import org.junit.Test
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

class CodeContentTest {
    @Test fun onlyWebSchemesCanOpen() {
        listOf("javascript:alert(1)", "intent://example.com", "file:///etc/passwd", "https://user:pass@example.com", "https://", "https://exa mple.com").forEach { assertNull(it, CodeContent.webUrl(it)) }
        assertEquals("https://example.com/path?q=yes", CodeContent.webUrl("https://example.com/path?q=yes"))
    }
    @Test fun plainWebsiteGainsHttps() { assertEquals("https://example.com", CodeContent.create("Website", "example.com")) }
    @Test(expected = IllegalArgumentException::class) fun invalidWebsiteRejected() { CodeContent.create("Website", "not a website") }
    @Test fun wifiSpecialCharactersEscaped() { assertEquals("WIFI:T:WPA;S:Cafe\\;Guest;P:p\\:a\\,s\\\\ss;H:false;;", CodeContent.wifi("Cafe;Guest", "p:a,s\\ss")) }
    @Test(expected = IllegalArgumentException::class) fun invalidWifiPasswordRejected() { CodeContent.wifi("Guest", "short") }
    @Test fun openWifiOmitsPassword() { assertEquals("WIFI:T:nopass;S:Guest;P:;H:false;;", CodeContent.wifi("Guest", "secret", false)) }
    @Test fun classificationsPreservePlainText() {
        assertEquals(ContentKind.TEXT, CodeContent.kind("hello"))
        assertEquals(ContentKind.PRODUCT, CodeContent.kind("8901234567890", "EAN_13"))
        assertEquals(ContentKind.EMAIL, CodeContent.kind("mailto:hello@example.com"))
        assertEquals(ContentKind.WIFI, CodeContent.kind("WIFI:S:Guest;;"))
        assertEquals(ContentKind.CONTACT, CodeContent.kind("BEGIN:VCARD\nFN:A"))
    }
    @Test fun csvPreventsFormulaInjectionAndQuotes() { assertEquals("\"'=1+1\"", CodeContent.csvCell("=1+1")); assertEquals("\"a,\"\"b\"\"\n\"", CodeContent.csvCell("a,\"b\"\n")) }
    @Test(expected = IllegalArgumentException::class) fun emptyCodeRejected() { CodeContent.create("Text", "  ") }
    @Test(expected = IllegalArgumentException::class) fun emailInjectionRejected() { CodeContent.create("Email", "a@example.com?bcc=other@example.com") }
    @Test fun phoneCreatesDialPayload() { assertEquals("tel:+1 555 0100", CodeContent.create("Phone", "+1 555 0100")) }
    @Test fun unicodeQrRoundTrip() {
        val value = "Hello 世界 · café · مرحبا"
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 400, 400, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val pixels = IntArray(400 * 400) { if (matrix[it % 400, it / 400]) 0xff000000.toInt() else 0xffffffff.toInt() }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(400, 400, pixels))))
        assertEquals(value, decoded.text)
    }
}
