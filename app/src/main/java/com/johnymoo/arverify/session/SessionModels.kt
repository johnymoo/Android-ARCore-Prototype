package com.johnymoo.arverify.session

import com.johnymoo.arverify.capture.CaptureSlot

enum class CaptureMode { RECOGNITION, GENERAL }

enum class SessionStatus { PENDING_UPLOAD, RECOGNIZED, NEEDS_MEASUREMENT, EXPORTED, FAILED }

data class CapturedFrame(
    val slot: CaptureSlot,
    val rgbFileName: String,
    val depthFileName: String? = null,
    val distanceM: Double? = null,
    val sharpness: Double? = null,
) {
    val hasDepth: Boolean get() = depthFileName != null
}

data class RecognizedSummary(
    val system: String? = null,
    val kind: String? = null,
    val unitsX: Int? = null,
    val unitsY: Int? = null,
    val pitchMm: Double? = null,
    val confidence: Double? = null,
)

data class CaptureSession(
    val partId: String,
    val mode: CaptureMode,
    val createdAtEpochMs: Long,
    val deviceModel: String,
    val status: SessionStatus,
    val frames: List<CapturedFrame>,
    val recognized: RecognizedSummary? = null,
)
