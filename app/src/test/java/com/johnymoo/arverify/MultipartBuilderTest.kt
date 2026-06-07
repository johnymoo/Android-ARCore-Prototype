package com.johnymoo.arverify

import com.johnymoo.arverify.net.MultipartBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultipartBuilderTest {
    @Test fun contentTypeCarriesBoundary() {
        val mb = MultipartBuilder("XB")
        assertEquals("multipart/form-data; boundary=XB", mb.contentType())
    }

    @Test fun buildsFieldAndFilePartsWithCrlfAndClosing() {
        val body = MultipartBuilder("XB")
            .addFormField("part_id", "abc")
            .addFilePart("images[]", "a.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
            .build()
        val text = String(body, Charsets.ISO_8859_1) // 1 byte per char, safe for binary inspection

        assertTrue(text.startsWith("--XB\r\n"))
        assertTrue(text.contains("Content-Disposition: form-data; name=\"part_id\"\r\n\r\nabc\r\n"))
        assertTrue(text.contains(
            "Content-Disposition: form-data; name=\"images[]\"; filename=\"a.jpg\"\r\n" +
                "Content-Type: image/jpeg\r\n\r\n"
        ))
        assertTrue(text.endsWith("--XB--\r\n"))
    }

    @Test fun buildIsRepeatable() {
        val mb = MultipartBuilder("XB").addFormField("k", "v")
        assertArrayEqualsLen(mb.build(), mb.build())
    }

    private fun assertArrayEqualsLen(a: ByteArray, b: ByteArray) {
        assertEquals(a.size, b.size)
        org.junit.Assert.assertArrayEquals(a, b)
    }
}
