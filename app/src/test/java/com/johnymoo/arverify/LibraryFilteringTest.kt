package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.ui.library.LibraryFilter
import com.johnymoo.arverify.ui.library.LibraryFiltering
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LibraryFilteringTest {
    private fun entry(id: String, status: SessionStatus) = SessionEntry(
        dir = File("/tmp/$id"),
        session = CaptureSession(
            partId = id, mode = CaptureMode.RECOGNITION, createdAtEpochMs = 1L,
            deviceModel = "PLG110", status = status,
            frames = listOf(CapturedFrame(CaptureSlot.TOP, "rgb.jpg")),
        ),
    )

    private val all = listOf(
        entry("a", SessionStatus.RECOGNIZED),
        entry("b", SessionStatus.PENDING_UPLOAD),
        entry("c", SessionStatus.NEEDS_MEASUREMENT),
        entry("d", SessionStatus.EXPORTED),
    )

    @Test fun allReturnsEverything() {
        assertEquals(4, LibraryFiltering.apply(all, LibraryFilter.ALL).size)
    }

    @Test fun recognizedFilter() {
        val r = LibraryFiltering.apply(all, LibraryFilter.RECOGNIZED)
        assertEquals(listOf("a"), r.map { it.session.partId })
    }

    @Test fun pendingFilterIncludesNeedsMeasurement() {
        val r = LibraryFiltering.apply(all, LibraryFilter.PENDING).map { it.session.partId }
        assertEquals(listOf("b", "c"), r)
    }

    @Test fun allHidesEmptySessions() {
        val empty = SessionEntry(
            dir = File("/tmp/empty"),
            session = CaptureSession(
                partId = "empty",
                mode = CaptureMode.RECOGNITION,
                createdAtEpochMs = 1L,
                deviceModel = "PLG110",
                status = SessionStatus.PENDING_UPLOAD,
                frames = emptyList(),
            ),
        )

        val visible = LibraryFiltering.apply(listOf(empty) + all, LibraryFilter.ALL)

        assertEquals(listOf("a", "b", "c", "d"), visible.map { it.session.partId })
    }
}
