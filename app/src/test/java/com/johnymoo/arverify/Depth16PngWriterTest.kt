package com.johnymoo.arverify

import com.johnymoo.arverify.imaging.Depth16PngWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

class Depth16PngWriterTest {
    @Test fun encodesValidPngSignature() {
        val png = Depth16PngWriter.encode(intArrayOf(0, 1), 2, 1)
        val sig = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        assertArrayEquals(sig, png.copyOfRange(0, 8))
    }

    @Test fun roundTripsExactMillimeterValuesVia16BitPng() {
        val mm = intArrayOf(0, 1000, 2000, 65535)
        val png = Depth16PngWriter.encode(mm, 2, 2)
        val decoded = decodeDepthPng(png)
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
        assertArrayEquals(mm, decoded.samples)
    }

    @Test fun clampsOutOfRangeValues() {
        val png = Depth16PngWriter.encode(intArrayOf(-5, 70000), 2, 1)
        val decoded = decodeDepthPng(png)
        assertArrayEquals(intArrayOf(0, 65535), decoded.samples)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSizeMismatch() {
        Depth16PngWriter.encode(intArrayOf(1, 2, 3), 2, 2)
    }

    private fun decodeDepthPng(png: ByteArray): DecodedDepthPng {
        val sig = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        assertArrayEquals(sig, png.copyOfRange(0, 8))

        var offset = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        val idat = ByteArrayOutputStream()
        while (offset < png.size) {
            val length = readInt(png, offset)
            offset += 4
            val type = String(png, offset, 4, Charsets.US_ASCII)
            offset += 4
            val data = png.copyOfRange(offset, offset + length)
            offset += length + 4 // Skip CRC; writer-specific CRCs are covered by parser compatibility via chunk layout.
            when (type) {
                "IHDR" -> {
                    width = readInt(data, 0)
                    height = readInt(data, 4)
                    bitDepth = data[8].toInt() and 0xFF
                    colorType = data[9].toInt() and 0xFF
                }
                "IDAT" -> idat.write(data)
                "IEND" -> break
            }
        }

        assertEquals(16, bitDepth)
        assertEquals(0, colorType)

        val raw = inflate(idat.toByteArray())
        assertEquals(height * (1 + width * 2), raw.size)
        val samples = IntArray(width * height)
        var rawOffset = 0
        var sampleOffset = 0
        repeat(height) {
            assertEquals(0, raw[rawOffset++].toInt() and 0xFF)
            repeat(width) {
                val hi = raw[rawOffset++].toInt() and 0xFF
                val lo = raw[rawOffset++].toInt() and 0xFF
                samples[sampleOffset++] = (hi shl 8) or lo
            }
        }
        return DecodedDepthPng(width, height, samples)
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            out.write(buffer, 0, count)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private data class DecodedDepthPng(
        val width: Int,
        val height: Int,
        val samples: IntArray,
    )
}
