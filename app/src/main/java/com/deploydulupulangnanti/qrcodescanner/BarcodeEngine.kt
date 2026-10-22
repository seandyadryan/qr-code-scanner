package com.deploydulupulangnanti.qrcodescanner

import android.graphics.Bitmap
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object BarcodeEngine {
    fun reader() = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODABAR, BarcodeFormat.ITF, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417)))
    }
    fun decode(bitmap: Bitmap): Result? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val reader = reader()
        fun attempt(input: LuminanceSource) = try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(input))) } catch (_: ReaderException) { null } finally { reader.reset() }
        val original = attempt(source) ?: attempt(source.invert())
        if (original != null) return original
        val rotated = FrameRotation.clockwise(source.matrix, bitmap.width, bitmap.height)
        val rotatedSource = PlanarYUVLuminanceSource(rotated, bitmap.height, bitmap.width, 0, 0, bitmap.height, bitmap.width, false)
        return attempt(rotatedSource) ?: attempt(rotatedSource.invert())
    }
    fun qr(value: String, size: Int = 1024): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) android.graphics.Color.rgb(26, 29, 27) else android.graphics.Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
