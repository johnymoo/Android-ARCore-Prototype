package com.johnymoo.arverify.capture

import com.johnymoo.arverify.depth.DepthRangeMm

enum class TargetDistanceSource {
    NONE,
    DEPTH,
    HIT_TEST,
}

data class ResolvedTargetDistance(
    val distanceM: Double,
    val coverage: Double,
    val locked: Boolean,
    val source: TargetDistanceSource,
    val fallbackDistanceM: Double?,
)

object TargetDistanceResolver {
    fun resolve(
        depthTarget: DepthTarget,
        fallbackDistanceM: Double?,
        range: DepthRangeMm,
    ): ResolvedTargetDistance {
        if (depthTarget.locked) {
            return depthTarget.toResolved(
                locked = true,
                source = TargetDistanceSource.DEPTH,
                fallbackDistanceM = fallbackDistanceM,
            )
        }

        if (fallbackDistanceM != null && fallbackDistanceM.inRange(range)) {
            return ResolvedTargetDistance(
                distanceM = fallbackDistanceM,
                coverage = depthTarget.coverage,
                locked = true,
                source = TargetDistanceSource.HIT_TEST,
                fallbackDistanceM = fallbackDistanceM,
            )
        }

        if (depthTarget.distanceM <= 0.0) {
            return depthTarget.toResolved(
                locked = false,
                source = TargetDistanceSource.NONE,
                fallbackDistanceM = fallbackDistanceM,
            )
        }

        return depthTarget.toResolved(
            locked = false,
            source = TargetDistanceSource.DEPTH,
            fallbackDistanceM = fallbackDistanceM,
        )
    }

    private fun DepthTarget.toResolved(
        locked: Boolean,
        source: TargetDistanceSource,
        fallbackDistanceM: Double?,
    ): ResolvedTargetDistance {
        return ResolvedTargetDistance(
            distanceM = distanceM,
            coverage = coverage,
            locked = locked,
            source = source,
            fallbackDistanceM = fallbackDistanceM,
        )
    }

    private fun Double.inRange(range: DepthRangeMm): Boolean {
        val mm = this * 1000.0
        return mm >= range.minMm && mm <= range.maxMm
    }
}
