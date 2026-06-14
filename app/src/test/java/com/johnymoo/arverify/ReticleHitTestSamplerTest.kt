package com.johnymoo.arverify

import com.johnymoo.arverify.capture.ReticleHitTestSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReticleHitTestSamplerTest {
    @Test fun samplesCenterAndInteriorReticleGrid() {
        val points = ReticleHitTestSampler.samplePoints(width = 1000, height = 2000)

        assertEquals(9, points.size)
        assertTrue(points.any { it.x == 500f && it.y == 1000f })
        assertTrue(points.any { it.x == 320f && it.y == 820f })
        assertTrue(points.any { it.x == 680f && it.y == 1180f })
        assertTrue(points.all { it.x in 0f..999f && it.y in 0f..1999f })
    }

    @Test fun returnsEmptyListForInvalidViewport() {
        assertTrue(ReticleHitTestSampler.samplePoints(width = 0, height = 2000).isEmpty())
        assertTrue(ReticleHitTestSampler.samplePoints(width = 1000, height = 0).isEmpty())
    }
}
