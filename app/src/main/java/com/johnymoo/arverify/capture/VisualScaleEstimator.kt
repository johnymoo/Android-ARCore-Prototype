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
)

object VisualScaleEstimator {
    const val VISUAL_REFERENCE_WIDTH_MM = RedBrickReferenceDetector.REFERENCE_WIDTH_MM

    fun estimate(
        image: RgbImage?,
        intrinsics: CameraIntrinsics,
        arcoreDistanceM: Double,
        arcoreDistanceSource: TargetDistanceSource,
    ): ScaleDistanceEstimate {
        if (image == null) {
            return fallback(arcoreDistanceM, arcoreDistanceSource, "RGB_UNAVAILABLE")
        }

        return when (val detected = RedBrickReferenceDetector.detect(image)) {
            is VisualReferenceDetection.Detected -> {
                val visualDistance = ScaleMath.distanceFromKnownWidthMeters(
                    referenceWidthMeters = detected.referenceWidthMm / 1000.0,
                    focalLengthPx = intrinsics.fx,
                    detectedWidthPx = detected.referenceWidthPx,
                )
                if (visualDistance > 0.0) {
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
                    )
                } else {
                    fallback(arcoreDistanceM, arcoreDistanceSource, "INVALID_INTRINSICS")
                }
            }
            is VisualReferenceDetection.Failed ->
                fallback(arcoreDistanceM, arcoreDistanceSource, detected.reason)
        }
    }

    fun fallback(
        arcoreDistanceM: Double,
        arcoreDistanceSource: TargetDistanceSource,
        visualReferenceStatus: String,
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
        )
}
