package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.capture.CaptureWizardController
import com.johnymoo.arverify.capture.WizardStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureWizardControllerTest {
    @Test fun startsNeedingTopFrame() {
        val c = CaptureWizardController()
        assertEquals(WizardStep.NEED_TOP, c.state.step)
        assertFalse(c.state.canUpload)
    }

    @Test fun topThenSideThenAngles() {
        val c = CaptureWizardController()
        c.record(CaptureSlot.TOP)
        assertEquals(WizardStep.NEED_SIDE, c.state.step)
        c.record(CaptureSlot.SIDE)
        assertEquals(WizardStep.NEED_ANGLES, c.state.step) // total=2, need >=4
        c.record(CaptureSlot.ANGLE)
        assertEquals(WizardStep.NEED_ANGLES, c.state.step) // total=3
        c.record(CaptureSlot.ANGLE)
        assertEquals(WizardStep.READY, c.state.step)        // total=4
        assertTrue(c.state.canUpload)
    }

    @Test fun extraAnglesStayReady() {
        val c = CaptureWizardController()
        c.record(CaptureSlot.TOP); c.record(CaptureSlot.SIDE)
        c.record(CaptureSlot.ANGLE); c.record(CaptureSlot.ANGLE); c.record(CaptureSlot.ANGLE)
        assertEquals(WizardStep.READY, c.state.step)
        assertEquals(5, c.state.totalFrames)
    }

    @Test fun cannotUploadWithoutTopEvenWithManyAngles() {
        val c = CaptureWizardController()
        c.record(CaptureSlot.SIDE)
        repeat(5) { c.record(CaptureSlot.ANGLE) }
        assertFalse(c.state.canUpload)
        assertEquals(WizardStep.NEED_TOP, c.state.step)
    }

    @Test fun recapturingTopDoesNotDoubleCount() {
        val c = CaptureWizardController()
        c.record(CaptureSlot.TOP)
        c.record(CaptureSlot.TOP) // re-take top
        assertEquals(1, c.state.totalFrames)
        assertEquals(WizardStep.NEED_SIDE, c.state.step)
    }

    @Test fun resetClearsState() {
        val c = CaptureWizardController()
        c.record(CaptureSlot.TOP); c.record(CaptureSlot.SIDE)
        c.reset()
        assertEquals(WizardStep.NEED_TOP, c.state.step)
        assertEquals(0, c.state.totalFrames)
    }
}
