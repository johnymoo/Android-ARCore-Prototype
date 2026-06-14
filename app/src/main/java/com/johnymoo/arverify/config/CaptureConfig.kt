package com.johnymoo.arverify.config

import com.johnymoo.arverify.capture.QualityThresholds

/** Tunable capture configuration (spec §8). Persisted on-device via AppPrefs. */
data class CaptureConfig(
    val baseUrl: String = "http://192.168.1.100:8000",
    val minDistanceM: Double = 0.15,
    val maxDistanceM: Double = 0.70,
    val minSharpness: Double = 120.0,
    val saveDebugRgbToGallery: Boolean = true,
    val visualReferenceModeEnabled: Boolean = false,
    val visualReferenceMinDistanceM: Double = 0.12,
    val visualReferenceMaxDistanceM: Double = 0.80,
    val visualReferenceMaxDepthDisagreementM: Double = 0.20,
) {
    fun thresholds(): QualityThresholds = QualityThresholds(minDistanceM, maxDistanceM, minSharpness)
}
