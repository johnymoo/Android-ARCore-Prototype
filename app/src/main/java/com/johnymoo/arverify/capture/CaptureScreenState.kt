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
    val sharpness: Double = 0.0,
    val frames: List<CapturedFrame> = emptyList(),
    val wizardStep: WizardStep = WizardStep.NEED_TOP,
    val canFinish: Boolean = false,
)

class CaptureHolder {
    val state = MutableStateFlow(CaptureUiState())
    val depthBitmap = MutableStateFlow<Bitmap?>(null)

    fun toggleDepth() { state.value = state.value.copy(depthOn = !state.value.depthOn) }
    fun setMode(m: CaptureModeUi) { state.value = state.value.copy(modeUi = m) }
}
