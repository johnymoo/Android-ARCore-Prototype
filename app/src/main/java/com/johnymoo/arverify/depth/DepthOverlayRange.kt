package com.johnymoo.arverify.depth

data class DepthRangeMm(val minMm: Int, val maxMm: Int)

object DepthOverlayRange {
    fun fromDistanceBand(minDistanceM: Double, maxDistanceM: Double): DepthRangeMm {
        val lo = (minDistanceM * 1000.0).toInt().coerceAtLeast(1)
        val hi = (maxDistanceM * 1000.0).toInt().coerceAtLeast(lo + 1)
        return DepthRangeMm(lo, hi)
    }
}
