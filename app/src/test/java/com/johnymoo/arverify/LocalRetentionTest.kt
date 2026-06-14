package com.johnymoo.arverify

import com.johnymoo.arverify.net.CapturePackage
import com.johnymoo.arverify.net.FilePart
import com.johnymoo.arverify.net.LocalRetention
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalRetentionTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun pkg() = CapturePackage(
        partId = "part/with:bad*chars", kind = "brick", systemHint = null,
        arMetadataJson = """{"k":"v"}""",
        recognitionRgb = FilePart("rgb.jpg", "image/jpeg", byteArrayOf(10, 11)),
        recognitionDepth = FilePart("depth.png", "image/png", byteArrayOf(20, 21)),
        images = listOf(FilePart("a.jpg", "image/jpeg", byteArrayOf(1)),
                        FilePart("b.jpg", "image/jpeg", byteArrayOf(2))),
    )

    @Test fun writesAllPartsIntoSanitizedPackageDir() {
        val dir = LocalRetention(tmp.root).save(pkg(), "20260607_120000")
        assertTrue(dir.isDirectory)
        // part_id sanitized (no slashes/colons/stars in the dir name)
        assertTrue(dir.name.startsWith("part_with_bad_chars_20260607_120000"))
        assertEquals("""{"k":"v"}""", dir.resolve("ar_metadata.json").readText())
        assertArrayEquals(byteArrayOf(10, 11), dir.resolve("recognition_rgb.jpg").readBytes())
        assertArrayEquals(byteArrayOf(20, 21), dir.resolve("recognition_depth.png").readBytes())
        assertArrayEquals(byteArrayOf(1), dir.resolve("image_0_a.jpg").readBytes())
        assertArrayEquals(byteArrayOf(2), dir.resolve("image_1_b.jpg").readBytes())
    }
}
