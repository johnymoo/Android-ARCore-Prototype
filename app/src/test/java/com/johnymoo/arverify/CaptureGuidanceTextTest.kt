package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureGuidanceText
import com.johnymoo.arverify.capture.CaptureUiState
import com.johnymoo.arverify.capture.DistanceConfidence
import com.johnymoo.arverify.capture.DistanceSourceForScale
import com.johnymoo.arverify.capture.QualityReason
import com.johnymoo.arverify.capture.TargetDistanceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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

    @Test fun hitTestDistanceUsesPlaneLabel() {
        val state = CaptureUiState(
            qualityReason = QualityReason.OK,
            distanceM = 0.42,
            distanceSource = TargetDistanceSource.HIT_TEST,
            targetLocked = true,
            sharpness = 210.0,
        )

        assertTrue(CaptureGuidanceText.statusLine(state).contains("平面 0.42m"))
    }

    @Test fun statusLineSeparatesScaleDistanceFromGateDistance() {
        val state = CaptureUiState(
            qualityReason = QualityReason.OK,
            distanceM = 0.67,
            distanceSource = TargetDistanceSource.DEPTH,
            scaleDistanceM = 0.22584816,
            distanceSourceForScale = DistanceSourceForScale.VISUAL_REFERENCE,
            distanceConfidence = DistanceConfidence.HIGH,
            targetLocked = true,
            sharpness = 210.0,
        )

        val line = CaptureGuidanceText.statusLine(state)

        assertTrue(line.contains("尺度 0.23m"))
        assertTrue(line.contains("门控 深度 0.67m"))
        assertTrue(line.contains("高可信"))
    }

    @Test fun statusLineUsesDotDecimalsInCommaDecimalLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val state = CaptureUiState(
                qualityReason = QualityReason.OK,
                distanceM = 0.67,
                distanceSource = TargetDistanceSource.DEPTH,
                scaleDistanceM = 0.22584816,
                distanceConfidence = DistanceConfidence.HIGH,
                targetLocked = true,
                sharpness = 210.0,
            )

            val line = CaptureGuidanceText.statusLine(state)

            assertTrue(line.contains("尺度 0.23m"))
            assertTrue(line.contains("门控 深度 0.67m"))
            assertTrue(line.contains("清晰度 210"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
