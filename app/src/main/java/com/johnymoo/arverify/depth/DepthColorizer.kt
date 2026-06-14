package com.johnymoo.arverify.depth

import android.graphics.Bitmap
import android.media.Image
import java.nio.ByteOrder

/** Converts an ARCore DEPTH16 image (16-bit millimeters per pixel) to a colorized Bitmap. */
object DepthColorizer {
    private const val MAX_VALID_MM = 8000

    fun colorize(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val shorts = plane.buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val rowStrideShorts = plane.rowStride / 2

        val mmGrid = IntArray(width * height)
        var minMm = Int.MAX_VALUE
        var maxMm = 0
        for (y in 0 until height) {
            val rowStart = y * rowStrideShorts
            for (x in 0 until width) {
                val mm = shorts.get(rowStart + x).toInt() and 0xFFFF
                mmGrid[y * width + x] = mm
                if (mm in 1..MAX_VALID_MM) {
                    if (mm < minMm) minMm = mm
                    if (mm > maxMm) maxMm = mm
                }
            }
        }
        if (minMm == Int.MAX_VALUE) { minMm = 0; maxMm = 1 }

        val pixels = IntArray(width * height)
        for (i in pixels.indices) pixels[i] = DepthColorMap.toArgb(mmGrid[i], minMm, maxMm)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }
}
