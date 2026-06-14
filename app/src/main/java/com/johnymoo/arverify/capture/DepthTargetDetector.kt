package com.johnymoo.arverify.capture

import com.johnymoo.arverify.depth.DepthRangeMm

data class DepthTarget(
    val distanceM: Double,
    val coverage: Double,
    val locked: Boolean,
)

object DepthTargetDetector {
    private const val MIN_VALID_MM = 1
    private const val NEAR_BAND_MM = 120
    private const val MIN_COVERAGE = 0.015
    private const val BACKGROUND_SPREAD_MM = 180

    fun detect(
        depthMm: IntArray,
        width: Int,
        height: Int,
        roiFraction: Double = 0.56,
        targetRange: DepthRangeMm? = null,
    ): DepthTarget {
        if (width <= 0 || height <= 0 || depthMm.size < width * height) {
            return DepthTarget(0.0, 0.0, locked = false)
        }
        val samples = roiSamples(depthMm, width, height, roiFraction)
        if (samples.isEmpty()) return DepthTarget(0.0, 0.0, locked = false)

        samples.sort()
        if (targetRange != null) {
            val targetSamples = samples
                .filter { it in targetRange.minMm..targetRange.maxMm }
                .toMutableList()
            if (targetSamples.isEmpty()) {
                val outOfRangeTarget = detectFromSortedSamples(samples)
                return DepthTarget(
                    distanceM = outOfRangeTarget.distanceM,
                    coverage = 0.0,
                    locked = false,
                )
            }
            val distanceMm = median(targetSamples)
            val coverage = targetSamples.size.toDouble() / samples.size.toDouble()
            return DepthTarget(
                distanceM = distanceMm / 1000.0,
                coverage = coverage,
                locked = coverage >= MIN_COVERAGE,
            )
        }

        return detectFromSortedSamples(samples)
    }

    private fun detectFromSortedSamples(samples: MutableList<Int>): DepthTarget {
        val nearAnchor = percentile(samples, 0.01)
        val farAnchor = percentile(samples, 0.90)
        val nearMax = nearAnchor + NEAR_BAND_MM
        val near = samples.filter { it <= nearMax }
        val distanceMm = median(near.ifEmpty { samples })
        val coverage = near.size.toDouble() / samples.size.toDouble()
        val hasForeground = farAnchor - nearAnchor >= BACKGROUND_SPREAD_MM
        return DepthTarget(
            distanceM = distanceMm / 1000.0,
            coverage = coverage,
            locked = hasForeground && coverage >= MIN_COVERAGE,
        )
    }

    private fun roiSamples(
        depthMm: IntArray,
        width: Int,
        height: Int,
        roiFraction: Double,
    ): MutableList<Int> {
        val f = roiFraction.coerceIn(0.05, 1.0)
        val winW = (width * f).toInt().coerceAtLeast(1)
        val winH = (height * f).toInt().coerceAtLeast(1)
        val x0 = (width - winW) / 2
        val y0 = (height - winH) / 2
        val samples = ArrayList<Int>(winW * winH)
        for (y in y0 until y0 + winH) {
            val row = y * width
            for (x in x0 until x0 + winW) {
                val mm = depthMm[row + x]
                if (mm >= MIN_VALID_MM) samples.add(mm)
            }
        }
        return samples
    }

    private fun percentile(sorted: List<Int>, p: Double): Int {
        val idx = ((sorted.size - 1) * p.coerceIn(0.0, 1.0)).toInt()
        return sorted[idx]
    }

    private fun median(sortedInput: List<Int>): Double {
        val sorted = if (sortedInput is MutableList<Int>) sortedInput else sortedInput.toMutableList()
        sorted.sort()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble()
        else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
