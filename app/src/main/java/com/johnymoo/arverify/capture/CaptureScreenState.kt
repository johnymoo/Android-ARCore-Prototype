package com.johnymoo.arverify.capture

import android.graphics.Bitmap
import com.johnymoo.arverify.session.CapturedFrame
import kotlinx.coroutines.flow.MutableStateFlow

enum class CaptureModeUi { GUIDED, FREE }

data class CaptureUiState(
    val modeUi: CaptureModeUi = CaptureModeUi.GUIDED,
    val depthOn: Boolean = true,
    val qualityReason: QualityReason = QualityReason.NOT_TRACKING,
    val distanceM: Double = 0.0,
    val distanceSource: TargetDistanceSource = TargetDistanceSource.NONE,
    val fallbackDistanceM: Double? = null,
    val scaleDistanceM: Double = 0.0,
    val visualDistanceM: Double? = null,
    val visualReferenceWidthPx: Double? = null,
    val visualReferenceWidthMm: Double = VisualScaleEstimator.VISUAL_REFERENCE_WIDTH_MM,
    val visualReferenceStatus: String = "NOT_EVALUATED",
    val distanceSourceForScale: DistanceSourceForScale = DistanceSourceForScale.ARCORE_DISTANCE,
    val distanceConfidence: DistanceConfidence = DistanceConfidence.LOW,
    val referenceModeEnabled: Boolean = false,
    val manualMeasurementRecommended: Boolean = true,
    val targetLocked: Boolean = false,
    val targetCoverage: Double = 0.0,
    val sharpness: Double = 0.0,
    val frames: List<CapturedFrame> = emptyList(),
    val wizardStep: WizardStep = WizardStep.NEED_TOP,
    val canFinish: Boolean = false,
    val sessionDir: String = "",
    val topReady: Boolean = false,
)

class CaptureHolder {
    val state = MutableStateFlow(CaptureUiState())
    val depthBitmap = MutableStateFlow<Bitmap?>(null)

    fun toggleDepth() { state.value = state.value.copy(depthOn = !state.value.depthOn) }
    fun setMode(m: CaptureModeUi) { state.value = state.value.copy(modeUi = m) }
}
