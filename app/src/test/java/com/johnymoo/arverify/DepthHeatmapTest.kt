package com.johnymoo.arverify

import com.johnymoo.arverify.depth.DepthHeatmap
import com.johnymoo.arverify.depth.DepthRangeMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthHeatmapTest {
    private val range = DepthRangeMm(150, 700)

    @Test fun invalidDepthIsTransparent() {
        val argb = DepthHeatmap.argb(intArrayOf(0), 1, 1, range)
        assertEquals(0, argb[0])
    }

    @Test fun nearIsRedishFarIsBluish() {
        val near = DepthHeatmap.argb(intArrayOf(160), 1, 1, range)[0]
        val far = DepthHeatmap.argb(intArrayOf(690), 1, 1, range)[0]
        val nearR = (near shr 16) and 0xFF
        val nearB = near and 0xFF
        val farR = (far shr 16) and 0xFF
        val farB = far and 0xFF
        assertTrue("near should be warmer", nearR > nearB)
        assertTrue("far should be cooler", farB > farR)
    }

    @Test fun sizeMatchesGrid() {
        val argb = DepthHeatmap.argb(IntArray(6) { 300 }, 3, 2, range)
        assertEquals(6, argb.size)
    }
}
