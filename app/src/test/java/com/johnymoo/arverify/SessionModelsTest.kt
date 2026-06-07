package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionModelsTest {
    @Test fun hasDepthReflectsDepthFileName() {
        val withDepth = CapturedFrame(CaptureSlot.TOP, "rgb0.jpg", depthFileName = "depth0.png")
        val noDepth = CapturedFrame(CaptureSlot.ANGLE, "rgb1.jpg")
        assertTrue(withDepth.hasDepth)
        assertFalse(noDepth.hasDepth)
    }

    @Test fun sessionHoldsFramesAndMode() {
        val s = CaptureSession(
            partId = "part-abc",
            mode = CaptureMode.RECOGNITION,
            createdAtEpochMs = 1_000L,
            deviceModel = "PLG110",
            status = SessionStatus.PENDING_UPLOAD,
            frames = listOf(CapturedFrame(CaptureSlot.TOP, "rgb0.jpg", "depth0.png")),
        )
        assertEquals(1, s.frames.size)
        assertEquals(CaptureMode.RECOGNITION, s.mode)
        assertEquals(SessionStatus.PENDING_UPLOAD, s.status)
        assertEquals(null, s.recognized)
    }
}
