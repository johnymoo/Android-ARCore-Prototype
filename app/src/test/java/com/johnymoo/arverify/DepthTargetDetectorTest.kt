package com.johnymoo.arverify

import com.johnymoo.arverify.capture.DepthTargetDetector
import com.johnymoo.arverify.depth.DepthRangeMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthTargetDetectorTest {
    @Test fun picksSmallNearObjectInsteadOfBackgroundMedian() {
        val grid = IntArray(100 * 100) { 1720 }
        for (y in 45 until 55) {
            for (x in 45 until 55) {
                grid[y * 100 + x] = 240
            }
        }

        val target = DepthTargetDetector.detect(grid, 100, 100, roiFraction = 0.56)

        assertTrue(target.locked)
        assertEquals(0.24, target.distanceM, 0.02)
    }

    @Test fun locksObjectInsideConfiguredCaptureRange() {
        val grid = IntArray(100 * 100) { 1720 }
        for (y in 45 until 55) {
            for (x in 45 until 55) {
                grid[y * 100 + x] = 240
            }
        }

        val target = DepthTargetDetector.detect(
            grid,
            100,
            100,
            roiFraction = 0.56,
            targetRange = DepthRangeMm(minMm = 150, maxMm = 700),
        )

        assertTrue(target.locked)
        assertEquals(0.24, target.distanceM, 0.02)
    }

    @Test fun doesNotLockWhenOnlyBackgroundIsVisible() {
        val grid = IntArray(60 * 60) { 1800 }

        val target = DepthTargetDetector.detect(grid, 60, 60, roiFraction = 0.56)

        assertFalse(target.locked)
        assertEquals(1.8, target.distanceM, 0.01)
    }

    @Test fun doesNotLockFarClusterOutsideConfiguredCaptureRange() {
        val grid = IntArray(100 * 100) { 7300 }
        for (y in 40 until 60) {
            for (x in 40 until 60) {
                grid[y * 100 + x] = 6900
            }
        }

        val target = DepthTargetDetector.detect(
            grid,
            100,
            100,
            roiFraction = 0.56,
            targetRange = DepthRangeMm(minMm = 150, maxMm = 700),
        )

        assertFalse(target.locked)
        assertEquals(6.9, target.distanceM, 0.02)
        assertEquals(0.0, target.coverage, 0.0)
    }

    @Test fun ignoresInvalidDepth() {
        val target = DepthTargetDetector.detect(IntArray(20 * 20), 20, 20)

        assertFalse(target.locked)
        assertEquals(0.0, target.distanceM, 0.0)
    }
}
