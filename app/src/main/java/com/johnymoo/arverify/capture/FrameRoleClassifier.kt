package com.johnymoo.arverify.capture

import kotlin.math.abs

object FrameRoleClassifier {
    const val TOP_MAX_PITCH_DEG = -60.0
    const val SIDE_ABS_PITCH_DEG = 30.0

    fun classify(pitchDeg: Double): CaptureSlot = when {
        pitchDeg <= TOP_MAX_PITCH_DEG -> CaptureSlot.TOP
        abs(pitchDeg) <= SIDE_ABS_PITCH_DEG -> CaptureSlot.SIDE
        else -> CaptureSlot.ANGLE
    }
}
