package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureButtonPolicy
import com.johnymoo.arverify.capture.WizardStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureButtonPolicyTest {
    @Test fun captureButtonStaysEnabledWhileWaitingForQuality() {
        assertTrue(CaptureButtonPolicy.isEnabled(WizardStep.NEED_TOP))
        assertTrue(CaptureButtonPolicy.isEnabled(WizardStep.NEED_SIDE))
        assertTrue(CaptureButtonPolicy.isEnabled(WizardStep.NEED_ANGLES))
    }

    @Test fun captureButtonDisablesOnlyWhenReadyToUpload() {
        assertFalse(CaptureButtonPolicy.isEnabled(WizardStep.READY))
    }
}
