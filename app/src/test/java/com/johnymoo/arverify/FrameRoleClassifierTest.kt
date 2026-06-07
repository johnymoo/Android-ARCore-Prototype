package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.capture.FrameRoleClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRoleClassifierTest {
    @Test fun straightDownIsTop() {
        assertEquals(CaptureSlot.TOP, FrameRoleClassifier.classify(-90.0))
        assertEquals(CaptureSlot.TOP, FrameRoleClassifier.classify(-75.0))
        assertEquals(CaptureSlot.TOP, FrameRoleClassifier.classify(-60.0))
    }

    @Test fun nearHorizontalIsSide() {
        assertEquals(CaptureSlot.SIDE, FrameRoleClassifier.classify(0.0))
        assertEquals(CaptureSlot.SIDE, FrameRoleClassifier.classify(20.0))
        assertEquals(CaptureSlot.SIDE, FrameRoleClassifier.classify(-30.0))
    }

    @Test fun inBetweenIsAngle() {
        assertEquals(CaptureSlot.ANGLE, FrameRoleClassifier.classify(-45.0))
        assertEquals(CaptureSlot.ANGLE, FrameRoleClassifier.classify(-59.9))
        assertEquals(CaptureSlot.ANGLE, FrameRoleClassifier.classify(45.0))
    }
}
