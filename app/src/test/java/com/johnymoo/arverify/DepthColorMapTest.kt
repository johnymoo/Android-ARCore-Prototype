package com.johnymoo.arverify

import com.johnymoo.arverify.depth.DepthColorMap
import org.junit.Assert.assertEquals
import org.junit.Test

class DepthColorMapTest {
    @Test fun invalidDepthIsTransparent() {
        assertEquals(0, DepthColorMap.toArgb(0, 100, 1000))
    }

    @Test fun nearIsOpaqueRed() {
        val c = DepthColorMap.toArgb(100, 100, 1000)
        assertEquals(0xFF, (c ushr 24) and 0xFF) // opaque
        assertEquals(0xFF, (c ushr 16) and 0xFF) // red max near
        assertEquals(0x00, c and 0xFF)           // blue zero near
    }

    @Test fun farIsBlue() {
        val c = DepthColorMap.toArgb(1000, 100, 1000)
        assertEquals(0x00, (c ushr 16) and 0xFF) // red zero far
        assertEquals(0xFF, c and 0xFF)           // blue max far
    }
}
