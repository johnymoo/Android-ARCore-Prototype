package com.johnymoo.arverify

import com.johnymoo.arverify.export.PlyWriter
import org.junit.Assert.assertTrue
import org.junit.Test

class PlyWriterTest {
    @Test fun writesHeaderAndOneVertex() {
        val ply = PlyWriter.toAsciiPly(floatArrayOf(1f, 2f, 3f, 0.5f))
        assertTrue(ply.startsWith("ply"))
        assertTrue(ply.contains("format ascii 1.0"))
        assertTrue(ply.contains("element vertex 1"))
        assertTrue(ply.contains("property float x"))
        assertTrue(ply.contains("property float confidence"))
        assertTrue(ply.contains("end_header"))
        assertTrue(ply.contains("1.000000 2.000000 3.000000 0.500000"))
    }

    @Test fun emptyInputHasZeroVertices() {
        assertTrue(PlyWriter.toAsciiPly(floatArrayOf()).contains("element vertex 0"))
    }

    @Test fun ignoresTrailingPartialPoint() {
        // 5 floats => only 1 complete (x,y,z,confidence) point
        assertTrue(PlyWriter.toAsciiPly(floatArrayOf(1f, 2f, 3f, 1f, 9f)).contains("element vertex 1"))
    }
}
