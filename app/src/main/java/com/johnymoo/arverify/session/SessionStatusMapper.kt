package com.johnymoo.arverify.session

import com.johnymoo.arverify.net.RecognitionStatus

object SessionStatusMapper {
    fun fromRecognition(status: RecognitionStatus): SessionStatus = when (status) {
        RecognitionStatus.RECOGNIZED -> SessionStatus.RECOGNIZED
        RecognitionStatus.NEEDS_MEASUREMENT -> SessionStatus.NEEDS_MEASUREMENT
        RecognitionStatus.UNKNOWN -> SessionStatus.PENDING_UPLOAD
    }
}
