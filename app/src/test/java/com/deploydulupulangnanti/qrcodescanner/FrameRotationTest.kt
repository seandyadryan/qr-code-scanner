package com.deploydulupulangnanti.qrcodescanner

import org.junit.Assert.*
import org.junit.Test
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

class FrameRotationTest {
    @Test fun rectangularFrameRotatesWithoutCropping() {
        assertArrayEquals(byteArrayOf(4,1,5,2,6,3), FrameRotation.clockwise(byteArrayOf(1,2,3,4,5,6),3,2))
    }
    @Test fun fourQuarterTurnsRestorePixels() {
        val original=ByteArray(15) { it.toByte() }
        var current=original
        repeat(4) { turn -> current=FrameRotation.clockwise(current,if(turn%2==0) 5 else 3,if(turn%2==0) 3 else 5) }
        assertArrayEquals(original,current)
    }
    @Test fun sidewaysProductBarcodeDecodesAfterRotation() {
        val value="4006381333931"
        val matrix=MultiFormatWriter().encode(value,BarcodeFormat.EAN_13,400,150)
        val original=ByteArray(400*150) { if(matrix[it%400,it/400]) 0 else 255.toByte() }
        val sideways=FrameRotation.clockwise(original,400,150)
        val restored=FrameRotation.clockwise(sideways,150,400)
        val source=PlanarYUVLuminanceSource(restored,400,150,0,0,400,150,false)
        assertEquals(value,MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
    }
}
