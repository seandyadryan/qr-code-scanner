package com.deploydulupulangnanti.qrcodescanner

/** Rotates tightly packed luminance by 90 degrees; the output width is [height]. */
object FrameRotation {
    fun clockwise(data: ByteArray, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0 && data.size == width * height)
        val rotated = ByteArray(data.size)
        for (y in 0 until height) for (x in 0 until width) {
            rotated[x * height + height - y - 1] = data[y * width + x]
        }
        return rotated
    }
}
