package com.johnymoo.arverify

import com.johnymoo.arverify.imaging.Depth16PngWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class Depth16PngWriterTest {
    @Test fun encodesValidPngSignature() {
        val png = Depth16PngWriter.encode(intArrayOf(0, 1), 2, 1)
        val sig = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        assertArrayEquals(sig, png.copyOfRange(0, 8))
    }

    @Test fun roundTripsExactMillimeterValuesVia16BitPng() {
        val mm = intArrayOf(0, 1000, 2000, 65535)
        val png = Depth16PngWriter.encode(mm, 2, 2)
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(2, img.width)
        assertEquals(2, img.height)
        val r = img.raster
        assertEquals(0, r.getSample(0, 0, 0))
        assertEquals(1000, r.getSample(1, 0, 0))
        assertEquals(2000, r.getSample(0, 1, 0))
        assertEquals(65535, r.getSample(1, 1, 0))
    }

    @Test fun clampsOutOfRangeValues() {
        val png = Depth16PngWriter.encode(intArrayOf(-5, 70000), 2, 1)
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(0, img.raster.getSample(0, 0, 0))
        assertEquals(65535, img.raster.getSample(1, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSizeMismatch() {
        Depth16PngWriter.encode(intArrayOf(1, 2, 3), 2, 2)
    }
}
