package com.johnymoo.arverify.depth

import kotlin.math.roundToInt

data class DepthRangeMm(val minMm: Int, val maxMm: Int)

object DepthOverlayRange {
    fun fromDistanceBand(minDistanceM: Double, maxDistanceM: Double): DepthRangeMm {
        val lo = (minDistanceM * 1000.0).roundToInt().coerceAtLeast(1)
        val hi = (maxDistanceM * 1000.0).roundToInt().coerceAtLeast(lo + 1)
        return DepthRangeMm(lo, hi)
    }
}
