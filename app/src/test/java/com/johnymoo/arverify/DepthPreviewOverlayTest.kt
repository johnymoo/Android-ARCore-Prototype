package com.johnymoo.arverify

import com.johnymoo.arverify.depth.DepthPreviewOverlay
import com.johnymoo.arverify.depth.DepthRangeMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthPreviewOverlayTest {
    private val range = DepthRangeMm(150, 700)

    @Test fun liveOverlayOnlyTintsInRangeDepthInsideCenterRoi() {
        val grid = IntArray(5 * 5) { 300 }
        grid[12] = 120 // too close, so it should not become a red rainbow streak

        val argb = DepthPreviewOverlay.argb(grid, 5, 5, range, roiFraction = 0.6)

        assertEquals(0, argb[0]) // outside the center ROI
        assertEquals(0, argb[12]) // inside ROI but outside the accepted distance band
        val tint = argb[13]
        assertTrue("valid preview tint should be translucent", ((tint ushr 24) and 0xFF) in 1..160)
        assertEquals(0x2DD4BF, tint and 0xFFFFFF)
    }

    @Test fun invalidGridReturnsTransparentFrame() {
        val argb = DepthPreviewOverlay.argb(intArrayOf(200), 2, 2, range)

        assertEquals(listOf(0, 0, 0, 0), argb.toList())
    }
}
