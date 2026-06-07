package com.johnymoo.arverify.imaging

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Encodes a width×height grid of depth millimeters (0..65535) as a lossless 16-bit grayscale PNG. */
object Depth16PngWriter {
    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) // \x89 PNG \r\n \x1a \n

    fun encode(mm: IntArray, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0 && mm.size == width * height) {
            "mm.size=${mm.size} must equal width*height=${width * height}"
        }
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)

        val ihdr = ByteArrayOutputStream()
        writeInt(ihdr, width)
        writeInt(ihdr, height)
        ihdr.write(16) // bit depth
        ihdr.write(0)  // color type: grayscale
        ihdr.write(0)  // compression: deflate
        ihdr.write(0)  // filter: adaptive
        ihdr.write(0)  // interlace: none
        writeChunk(out, "IHDR", ihdr.toByteArray())

        val raw = ByteArrayOutputStream()
        var i = 0
        for (y in 0 until height) {
            raw.write(0) // per-scanline filter type: None
            for (x in 0 until width) {
                val v = mm[i++].coerceIn(0, 0xFFFF)
                raw.write((v ushr 8) and 0xFF) // big-endian high byte
                raw.write(v and 0xFF)          // low byte
            }
        }
        writeChunk(out, "IDAT", deflate(raw.toByteArray()))
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(data); deflater.finish()
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        writeInt(out, data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(out, crc.value.toInt())
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}
