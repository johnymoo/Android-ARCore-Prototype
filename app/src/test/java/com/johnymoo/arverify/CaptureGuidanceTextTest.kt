package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureGuidanceText
import com.johnymoo.arverify.capture.CaptureUiState
import com.johnymoo.arverify.capture.QualityReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGuidanceTextTest {
    @Test fun farLockedTargetUsesActionAsPrimaryPrompt() {
        val state = CaptureUiState(
            qualityReason = QualityReason.TOO_FAR,
            distanceM = 0.84,
            targetLocked = true,
            sharpness = 166.0,
        )

        assertEquals("目标已找到，靠近一点", CaptureGuidanceText.primaryPrompt(state))
        assertTrue(CaptureGuidanceText.statusLine(state).contains("目标锁定"))
        assertFalse(CaptureGuidanceText.primaryPrompt(state).contains("锁定"))
    }

    @Test fun missingTargetTellsUserToUseReticle() {
        val state = CaptureUiState(qualityReason = QualityReason.NO_TARGET)

        assertEquals("把积木主体放进取景框", CaptureGuidanceText.primaryPrompt(state))
    }
}
