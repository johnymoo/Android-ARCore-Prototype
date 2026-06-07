package com.johnymoo.arverify

import com.johnymoo.arverify.capture.ScaleMath
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleMathTest {
    @Test fun metricSpanUsesPinholeModel() {
        // pixelSpan * distance / fx = 100 * 0.25 / 500 = 0.05 m
        assertEquals(0.05, ScaleMath.metricSpanMeters(100.0, 0.25, 500.0), 1e-9)
    }

    @Test fun metricSpanZeroWhenFocalNonPositive() {
        assertEquals(0.0, ScaleMath.metricSpanMeters(100.0, 0.25, 0.0), 1e-9)
    }

    @Test fun pixelSpanIsInverseOfMetricSpan() {
        assertEquals(100.0, ScaleMath.pixelSpan(0.05, 0.25, 500.0), 1e-9)
    }

    @Test fun pixelSpanZeroWhenDistanceNonPositive() {
        assertEquals(0.0, ScaleMath.pixelSpan(0.05, 0.0, 500.0), 1e-9)
    }

    @Test fun medianCenterDistanceTakesCentralWindowMedianInMeters() {
        // 4x4 grid: border = 5000 mm, central 2x2 = 200,210,220,230 mm.
        // centerFraction 0.5 -> central 2x2 window; median of [200,210,220,230] = 215 mm = 0.215 m
        val grid = IntArray(16) { 5000 }
        grid[1 * 4 + 1] = 200; grid[1 * 4 + 2] = 210
        grid[2 * 4 + 1] = 220; grid[2 * 4 + 2] = 230
        assertEquals(0.215, ScaleMath.medianCenterDistanceMeters(grid, 4, 4, 0.5), 1e-9)
    }

    @Test fun medianCenterDistanceIgnoresInvalidZeros() {
        // central window all zero/invalid -> 0.0 sentinel
        val grid = IntArray(16) { 0 }
        assertEquals(0.0, ScaleMath.medianCenterDistanceMeters(grid, 4, 4, 0.5), 1e-9)
    }
}
