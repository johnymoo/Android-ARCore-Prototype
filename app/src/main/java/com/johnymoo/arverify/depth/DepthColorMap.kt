package com.johnymoo.arverify.depth

import kotlin.math.abs

/** Pure depth(mm) -> ARGB color mapping. Near = warm (red), far = cool (blue). */
object DepthColorMap {
    /** Returns packed ARGB. Invalid depth (mm <= 0) maps to fully transparent (0). */
    fun toArgb(mm: Int, minMm: Int, maxMm: Int): Int {
        if (mm <= 0) return 0
        val lo = minMm.coerceAtLeast(1)
        val hi = maxMm.coerceAtLeast(lo + 1)
        val t = ((mm - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
        val r = ((1f - t) * 255f).toInt().coerceIn(0, 255)
        val b = (t * 255f).toInt().coerceIn(0, 255)
        val g = ((1f - abs(t - 0.5f) * 2f) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
