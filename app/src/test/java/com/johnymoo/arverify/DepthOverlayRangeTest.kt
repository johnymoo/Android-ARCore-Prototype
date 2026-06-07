package com.johnymoo.arverify

import com.johnymoo.arverify.depth.DepthOverlayRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthOverlayRangeTest {
    @Test fun convertsCaptureBandMetersToMillimeters() {
        val r = DepthOverlayRange.fromDistanceBand(0.15, 0.70)
        assertEquals(150, r.minMm)
        assertEquals(700, r.maxMm)
    }

    @Test fun clampsDegenerateBandToValidRange() {
        val r = DepthOverlayRange.fromDistanceBand(0.0, 0.0)
        assertTrue(r.minMm >= 1)
        assertTrue(r.maxMm > r.minMm)
    }
}
