package com.johnymoo.arverify

import com.johnymoo.arverify.capture.DepthTarget
import com.johnymoo.arverify.capture.TargetDistanceResolver
import com.johnymoo.arverify.capture.TargetDistanceSource
import com.johnymoo.arverify.depth.DepthRangeMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetDistanceResolverTest {
    private val range = DepthRangeMm(minMm = 150, maxMm = 700)

    @Test fun prefersLockedDepthOverFallback() {
        val resolved = TargetDistanceResolver.resolve(
            depthTarget = DepthTarget(distanceM = 0.42, coverage = 0.18, locked = true),
            fallbackDistanceM = 0.31,
            range = range,
        )

        assertEquals(0.42, resolved.distanceM, 0.0)
        assertEquals(0.18, resolved.coverage, 0.0)
        assertTrue(resolved.locked)
        assertEquals(TargetDistanceSource.DEPTH, resolved.source)
        assertEquals(0.31, resolved.fallbackDistanceM!!, 0.0)
    }

    @Test fun usesInRangeHitTestWhenDepthIsNotLocked() {
        val resolved = TargetDistanceResolver.resolve(
            depthTarget = DepthTarget(distanceM = 1.8, coverage = 0.0, locked = false),
            fallbackDistanceM = 0.5,
            range = range,
        )

        assertEquals(0.5, resolved.distanceM, 0.0)
        assertEquals(0.0, resolved.coverage, 0.0)
        assertTrue(resolved.locked)
        assertEquals(TargetDistanceSource.HIT_TEST, resolved.source)
        assertEquals(0.5, resolved.fallbackDistanceM!!, 0.0)
    }

    @Test fun rejectsOutOfRangeHitTest() {
        val resolved = TargetDistanceResolver.resolve(
            depthTarget = DepthTarget(distanceM = 1.8, coverage = 0.0, locked = false),
            fallbackDistanceM = 0.9,
            range = range,
        )

        assertEquals(1.8, resolved.distanceM, 0.0)
        assertEquals(0.0, resolved.coverage, 0.0)
        assertFalse(resolved.locked)
        assertEquals(TargetDistanceSource.DEPTH, resolved.source)
        assertEquals(0.9, resolved.fallbackDistanceM!!, 0.0)
    }

    @Test fun preservesUnlockedDepthWhenFallbackIsAbsent() {
        val resolved = TargetDistanceResolver.resolve(
            depthTarget = DepthTarget(distanceM = 1.8, coverage = 0.12, locked = false),
            fallbackDistanceM = null,
            range = range,
        )

        assertEquals(1.8, resolved.distanceM, 0.0)
        assertEquals(0.12, resolved.coverage, 0.0)
        assertFalse(resolved.locked)
        assertEquals(TargetDistanceSource.DEPTH, resolved.source)
        assertNull(resolved.fallbackDistanceM)
    }

    @Test fun usesNoneSourceWhenNoDistanceIsAvailable() {
        val resolved = TargetDistanceResolver.resolve(
            depthTarget = DepthTarget(distanceM = 0.0, coverage = 0.0, locked = false),
            fallbackDistanceM = null,
            range = range,
        )

        assertEquals(0.0, resolved.distanceM, 0.0)
        assertFalse(resolved.locked)
        assertEquals(TargetDistanceSource.NONE, resolved.source)
        assertNull(resolved.fallbackDistanceM)
    }
}
