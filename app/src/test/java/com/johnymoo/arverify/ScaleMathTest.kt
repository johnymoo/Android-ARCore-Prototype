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

    @Test fun distanceFromKnownReferenceWidthMatchesIssueFourExamples() {
        val referenceWidthM = 0.03384
        val fx = 433.81

        assertEquals(0.22584816, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 65.0), 1e-9)
        assertEquals(0.18350163, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 80.0), 1e-9)
        assertEquals(0.146801304, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 100.0), 1e-9)
        assertEquals(0.587205216, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 25.0), 1e-9)
        assertEquals(0.6672786545454547, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 22.0), 1e-9)
    }

    @Test fun distanceFromKnownReferenceWidthRejectsInvalidInputs() {
        assertEquals(0.0, ScaleMath.distanceFromKnownWidthMeters(0.03384, 433.81, 0.0), 1e-9)
        assertEquals(0.0, ScaleMath.distanceFromKnownWidthMeters(0.03384, 0.0, 65.0), 1e-9)
        assertEquals(0.0, ScaleMath.distanceFromKnownWidthMeters(0.0, 433.81, 65.0), 1e-9)
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
