package com.johnymoo.arverify

import com.johnymoo.arverify.config.CaptureConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureConfigTest {
    @Test fun defaultsAreSensible() {
        val c = CaptureConfig()
        assertEquals("http://192.168.1.100:8000", c.baseUrl)
        assertEquals(0.15, c.minDistanceM, 1e-9)
        assertEquals(0.45, c.maxDistanceM, 1e-9)
        assertEquals(120.0, c.minSharpness, 1e-9)
    }

    @Test fun thresholdsMapFromConfig() {
        val t = CaptureConfig(minDistanceM = 0.2, maxDistanceM = 0.4, minSharpness = 90.0).thresholds()
        assertEquals(0.2, t.minDistanceM, 1e-9)
        assertEquals(0.4, t.maxDistanceM, 1e-9)
        assertEquals(90.0, t.minSharpness, 1e-9)
    }
}
