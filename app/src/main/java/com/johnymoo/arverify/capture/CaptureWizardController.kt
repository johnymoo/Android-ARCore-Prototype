package com.johnymoo.arverify.capture

/** The three coverage roles a captured frame fills (spec §2: 顶/侧/角). */
enum class CaptureSlot { TOP, SIDE, ANGLE }

/** Which prompt the wizard should show next. */
enum class WizardStep { NEED_TOP, NEED_SIDE, NEED_ANGLES, READY }

/** Immutable snapshot of capture coverage. [TOP] and [SIDE] are at-most-one; angles accumulate. */
data class WizardState(
    val topCaptured: Boolean = false,
    val sideCaptured: Boolean = false,
    val angleCount: Int = 0,
) {
    val totalFrames: Int
        get() = (if (topCaptured) 1 else 0) + (if (sideCaptured) 1 else 0) + angleCount

    val canUpload: Boolean
        get() = topCaptured && sideCaptured && totalFrames >= MIN_FRAMES

    val step: WizardStep
        get() = when {
            !topCaptured -> WizardStep.NEED_TOP
            !sideCaptured -> WizardStep.NEED_SIDE
            totalFrames < MIN_FRAMES -> WizardStep.NEED_ANGLES
            else -> WizardStep.READY
        }

    companion object { const val MIN_FRAMES = 4 }
}

/** Pure guided-capture state machine. The UI calls [record] after each successful capture. */
class CaptureWizardController {
    var state: WizardState = WizardState()
        private set

    /** Records a capture for [slot]; TOP/SIDE are idempotent, ANGLE increments. Returns new state. */
    fun record(slot: CaptureSlot): WizardState {
        state = when (slot) {
            CaptureSlot.TOP -> state.copy(topCaptured = true)
            CaptureSlot.SIDE -> state.copy(sideCaptured = true)
            CaptureSlot.ANGLE -> state.copy(angleCount = state.angleCount + 1)
        }
        return state
    }

    fun reset() { state = WizardState() }
}
