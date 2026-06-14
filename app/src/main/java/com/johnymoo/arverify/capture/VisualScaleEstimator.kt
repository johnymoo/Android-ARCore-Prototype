package com.johnymoo.arverify.capture

import com.johnymoo.arverify.metadata.CameraIntrinsics

enum class DistanceSourceForScale {
    VISUAL_REFERENCE,
    ARCORE_DISTANCE,
}

enum class DistanceConfidence {
    HIGH,
    LOW,
}

data class ScaleEstimationPolicy(
    val referenceModeEnabled: Boolean = false,
    val visualMinDistanceM: Double = 0.12,
    val visualMaxDistanceM: Double = 0.80,
    val maxDepthDisagreementM: Double = 0.20,
)

data class ScaleDistanceEstimate(
    val distanceM: Double,
    val arcoreDistanceM: Double,
    val arcoreDistanceSource: TargetDistanceSource,
    val visualDistanceM: Double?,
    val visualReferenceWidthPx: Double?,
    val visualReferenceWidthMm: Double,
    val visualReferenceStatus: String,
    val distanceSourceForScale: DistanceSourceForScale,
    val distanceConfidence: DistanceConfidence,
    val referenceModeEnabled: Boolean = false,
    val manualMeasurementRecommended: Boolean = true,
)

object VisualScaleEstimator {
    const val VISUAL_REFERENCE_WIDTH_MM = RedBrickReferenceDetector.REFERENCE_WIDTH_MM

    fun estimate(
        image: RgbImage?,
        intrinsics: CameraIntrinsics,
        arcoreDistanceM: Double,
        arcoreDistanceSource: TargetDistanceSource,
        policy: ScaleEstimationPolicy = ScaleEstimationPolicy(),
    ): ScaleDistanceEstimate {
        if (!policy.referenceModeEnabled) {
            return fallback(
                arcoreDistanceM,
                arcoreDistanceSource,
                "REFERENCE_MODE_DISABLED",
                policy.referenceModeEnabled,
            )
        }
        if (image == null) {
            return fallback(arcoreDistanceM, arcoreDistanceSource, "RGB_UNAVAILABLE", policy.referenceModeEnabled)
        }

        return when (val detected = RedBrickReferenceDetector.detect(image)) {
            is VisualReferenceDetection.Detected -> {
                val visualDistance = ScaleMath.distanceFromKnownWidthMeters(
                    referenceWidthMeters = detected.referenceWidthMm / 1000.0,
                    focalLengthPx = intrinsics.fx,
                    detectedWidthPx = detected.referenceWidthPx,
                )
                if (visualDistance <= 0.0) {
                    fallback(arcoreDistanceM, arcoreDistanceSource, "INVALID_INTRINSICS", policy.referenceModeEnabled)
                } else if (visualDistance < policy.visualMinDistanceM || visualDistance > policy.visualMaxDistanceM) {
                    fallback(
                        arcoreDistanceM,
                        arcoreDistanceSource,
                        "VISUAL_DISTANCE_OUT_OF_RANGE",
                        policy.referenceModeEnabled,
                    )
                } else if (
                    arcoreDistanceM > 0.0 &&
                    arcoreDistanceSource != TargetDistanceSource.NONE &&
                    kotlin.math.abs(arcoreDistanceM - visualDistance) > policy.maxDepthDisagreementM
                ) {
                    fallback(
                        arcoreDistanceM,
                        arcoreDistanceSource,
                        "VISUAL_DEPTH_DISAGREE",
                        policy.referenceModeEnabled,
                    )
                } else {
                    ScaleDistanceEstimate(
                        distanceM = visualDistance,
                        arcoreDistanceM = arcoreDistanceM,
                        arcoreDistanceSource = arcoreDistanceSource,
                        visualDistanceM = visualDistance,
                        visualReferenceWidthPx = detected.referenceWidthPx,
                        visualReferenceWidthMm = detected.referenceWidthMm,
                        visualReferenceStatus = "DETECTED",
                        distanceSourceForScale = DistanceSourceForScale.VISUAL_REFERENCE,
                        distanceConfidence = DistanceConfidence.HIGH,
                        referenceModeEnabled = policy.referenceModeEnabled,
                        manualMeasurementRecommended = false,
                    )
                }
            }
            is VisualReferenceDetection.Failed ->
                fallback(arcoreDistanceM, arcoreDistanceSource, detected.reason, policy.referenceModeEnabled)
        }
    }

    fun fallback(
        arcoreDistanceM: Double,
        arcoreDistanceSource: TargetDistanceSource,
        visualReferenceStatus: String,
        referenceModeEnabled: Boolean = false,
    ): ScaleDistanceEstimate =
        ScaleDistanceEstimate(
            distanceM = arcoreDistanceM.takeIf { it > 0.0 } ?: 0.0,
            arcoreDistanceM = arcoreDistanceM.takeIf { it > 0.0 } ?: 0.0,
            arcoreDistanceSource = arcoreDistanceSource,
            visualDistanceM = null,
            visualReferenceWidthPx = null,
            visualReferenceWidthMm = VISUAL_REFERENCE_WIDTH_MM,
            visualReferenceStatus = visualReferenceStatus,
            distanceSourceForScale = DistanceSourceForScale.ARCORE_DISTANCE,
            distanceConfidence = DistanceConfidence.LOW,
            referenceModeEnabled = referenceModeEnabled,
            manualMeasurementRecommended = true,
        )
}
