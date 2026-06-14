package com.johnymoo.arverify.depth

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** Live viewfinder overlay: only marks usable depth inside the center reticle. */
object DepthPreviewOverlay {
    private const val TINT = 0x2DD4BF
    private const val ALPHA = 0x66

    fun argb(
        gridMm: IntArray,
        width: Int,
        height: Int,
        range: DepthRangeMm,
        roiFraction: Double = 0.56,
    ): IntArray {
        val total = width * height
        if (width <= 0 || height <= 0 || gridMm.size < total) return IntArray(total.coerceAtLeast(0))

        val out = IntArray(total)
        val frac = roiFraction.coerceIn(0.05, 1.0)
        val roiW = (width * frac).roundToInt().coerceIn(1, width)
        val roiH = (height * frac).roundToInt().coerceIn(1, height)
        val x0 = (width - roiW) / 2
        val y0 = (height - roiH) / 2
        val x1 = x0 + roiW
        val y1 = y0 + roiH

        for (y in y0 until y1) {
            val row = y * width
            for (x in x0 until x1) {
                val mm = gridMm[row + x]
                if (mm in range.minMm..range.maxMm) out[row + x] = (ALPHA shl 24) or TINT
            }
        }
        return out
    }

    fun toBitmap(gridMm: IntArray, width: Int, height: Int, range: DepthRangeMm): Bitmap {
        val pixels = argb(gridMm, width, height, range)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
