package com.johnymoo.arverify.depth

import android.graphics.Bitmap

object DepthHeatmap {
    fun argb(gridMm: IntArray, width: Int, height: Int, range: DepthRangeMm): IntArray {
        val out = IntArray(width * height)
        for (i in out.indices) out[i] = DepthColorMap.toArgb(gridMm[i], range.minMm, range.maxMm)
        return out
    }

    fun toBitmap(gridMm: IntArray, width: Int, height: Int, range: DepthRangeMm): Bitmap {
        val pixels = argb(gridMm, width, height, range)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
