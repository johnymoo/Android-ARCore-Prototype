package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.net.RecognitionResult
import com.johnymoo.arverify.net.RecognitionStatus
import com.johnymoo.arverify.net.Recognized
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureLibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun session(partId: String, createdAt: Long, status: SessionStatus = SessionStatus.PENDING_UPLOAD) =
        CaptureSession(
            partId = partId, mode = CaptureMode.RECOGNITION, createdAtEpochMs = createdAt,
            deviceModel = "PLG110", status = status,
            frames = listOf(CapturedFrame(CaptureSlot.TOP, "rgb0.jpg", "depth0.png")),
        )

    @Test fun writeThenListReturnsNewestFirst() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        repo.writeManifest(File(root, "part-old"), session("part-old", 1_000L))
        repo.writeManifest(File(root, "part-new"), session("part-new", 2_000L))

        val entries = repo.listSessions()
        assertEquals(2, entries.size)
        assertEquals("part-new", entries[0].session.partId)
        assertEquals("part-old", entries[1].session.partId)
    }

    @Test fun writeIsAtomicNoTmpLeftAndOverwrites() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        val dir = File(root, "part-x")
        repo.writeManifest(dir, session("part-x", 1_000L, SessionStatus.PENDING_UPLOAD))
        repo.writeManifest(dir, session("part-x", 1_000L, SessionStatus.RECOGNIZED))

        assertFalse(File(dir, "manifest.json.tmp").exists())
        assertTrue(File(dir, "manifest.json").exists())
        val only = repo.listSessions().single()
        assertEquals(SessionStatus.RECOGNIZED, only.session.status)
    }

    @Test fun listSkipsDirsWithoutValidManifest() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        repo.writeManifest(File(root, "good"), session("good", 1_000L))
        File(root, "empty").mkdirs()
        File(File(root, "bad").apply { mkdirs() }, "manifest.json").writeText("garbage")

        assertEquals(1, repo.listSessions().size)
    }

    @Test fun deleteRemovesSessionDir() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        val dir = File(root, "part-del")
        repo.writeManifest(dir, session("part-del", 1_000L))
        File(dir, "rgb0.jpg").writeBytes(byteArrayOf(1, 2, 3))

        val entry = repo.listSessions().single()
        assertTrue(repo.delete(entry))
        assertFalse(dir.exists())
        assertTrue(repo.listSessions().isEmpty())
    }

    @Test fun uploadResultUpdatesManifestStatusAndRecognizedSummary() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        val dir = File(root, "part-needs-measurement")
        repo.writeManifest(dir, session("part-needs-measurement", 1_000L, SessionStatus.PENDING_UPLOAD))

        val updated = repo.updateUploadResult(
            dir,
            RecognitionResult(
                captureId = "cap-1",
                jobId = null,
                status = RecognitionStatus.NEEDS_MEASUREMENT,
                recognized = Recognized(
                    system = null,
                    kind = "brick",
                    unitsX = 22,
                    unitsY = 14,
                    pitchMm = 163.668,
                    confidence = 0.0,
                ),
                needsMeasurement = null,
            ),
        )

        assertEquals(SessionStatus.NEEDS_MEASUREMENT, updated!!.status)
        assertEquals("brick", updated.recognized!!.kind)
        assertEquals(22, updated.recognized!!.unitsX)
        assertEquals(SessionStatus.NEEDS_MEASUREMENT, repo.listSessions().single().session.status)
    }

    @Test fun missingRootListsEmpty() {
        val repo = CaptureLibraryRepository(File(tmp.root, "does-not-exist"))
        assertTrue(repo.listSessions().isEmpty())
    }
}
