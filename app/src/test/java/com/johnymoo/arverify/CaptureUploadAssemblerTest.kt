package com.johnymoo.arverify

import com.johnymoo.arverify.net.CaptureUploadAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureUploadAssemblerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun seed(dir: File) {
        dir.mkdirs()
        File(dir, "ar_metadata.json").writeText("""{"device":{"model":"PLG110"}}""")
        File(dir, "recognition_rgb.jpg").writeBytes(byteArrayOf(1, 2))
        File(dir, "recognition_depth.png").writeBytes(byteArrayOf(3, 4))
        File(dir, "frame_0.jpg").writeBytes(byteArrayOf(5))
        File(dir, "frame_1.jpg").writeBytes(byteArrayOf(6))
        File(dir, "depthc_0.png").writeBytes(byteArrayOf(7))
    }

    @Test fun assemblesPackageFromDir() {
        val dir = tmp.newFolder("part-x_123")
        seed(dir)
        val pkg = CaptureUploadAssembler.fromDir(dir, partId = "part-x", kind = "brick", systemHint = null)!!
        assertEquals("part-x", pkg.partId)
        assertEquals("brick", pkg.kind)
        assertTrue(pkg.arMetadataJson.contains("PLG110"))
        assertEquals(2, pkg.recognitionRgb.bytes.size)
        assertEquals(2, pkg.recognitionDepth.bytes.size)
        assertEquals(listOf("frame_0.jpg", "frame_1.jpg"), pkg.images.map { it.filename })
    }

    @Test fun missingContractFilesReturnsNull() {
        val dir = tmp.newFolder("empty_1")
        assertNull(CaptureUploadAssembler.fromDir(dir, "p", "brick", null))
    }
}
