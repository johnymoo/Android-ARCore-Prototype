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
            manualMeasurementRecommended = false,
            targetLocked = true,
            sharpness = 210.0,
        )

        val line = CaptureGuidanceText.statusLine(state)

        assertTrue(line.contains("参考尺 0.23m"))
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

            assertTrue(line.contains("尺度估计 0.23m"))
            assertTrue(line.contains("门控 深度 0.67m"))
            assertTrue(line.contains("清晰度 210"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test fun disabledReferenceModeExplainsScaleIsEstimated() {
        val state = CaptureUiState(
            qualityReason = QualityReason.OK,
            distanceM = 0.42,
            distanceSource = TargetDistanceSource.HIT_TEST,
            scaleDistanceM = 0.42,
            visualReferenceStatus = "REFERENCE_MODE_DISABLED",
            distanceSourceForScale = DistanceSourceForScale.ARCORE_DISTANCE,
            distanceConfidence = DistanceConfidence.LOW,
            referenceModeEnabled = false,
            manualMeasurementRecommended = true,
            targetLocked = true,
            sharpness = 210.0,
        )

        val line = CaptureGuidanceText.statusLine(state)

        assertTrue(line.contains("尺度估计"))
        assertTrue(line.contains("建议测量确认"))
        assertFalse(line.contains("高可信"))
    }

    @Test fun enabledReferenceModeShowsReferenceCalibrationHint() {
        val state = CaptureUiState(
            qualityReason = QualityReason.OK,
            distanceM = 0.24,
            distanceSource = TargetDistanceSource.DEPTH,
            scaleDistanceM = 0.22584816,
            visualReferenceStatus = "DETECTED",
            distanceSourceForScale = DistanceSourceForScale.VISUAL_REFERENCE,
            distanceConfidence = DistanceConfidence.HIGH,
            referenceModeEnabled = true,
            manualMeasurementRecommended = false,
            targetLocked = true,
            sharpness = 210.0,
        )

        val line = CaptureGuidanceText.statusLine(state)

        assertTrue(line.contains("参考尺"))
        assertTrue(line.contains("高可信"))
    }
}
