package com.johnymoo.arverify.config

import com.johnymoo.arverify.capture.QualityThresholds

/** Tunable capture configuration (spec §8). Persisted on-device via AppPrefs. */
data class CaptureConfig(
    val baseUrl: String = "",
    val minDistanceM: Double = 0.15,
    val maxDistanceM: Double = 0.45,
    val minSharpness: Double = 120.0,
) {
    fun thresholds(): QualityThresholds = QualityThresholds(minDistanceM, maxDistanceM, minSharpness)
}
