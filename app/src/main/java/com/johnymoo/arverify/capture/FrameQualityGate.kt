package com.johnymoo.arverify.capture

/** Minimal tracking-state mirror so the gate stays free of ARCore types (mapped on-device). */
enum class TrackingStateLite { TRACKING, PAUSED, STOPPED }

/** Tunable acceptance band (spec §8: distance band + sharpness threshold are configurable). */
data class QualityThresholds(
    val minDistanceM: Double,
    val maxDistanceM: Double,
    val minSharpness: Double,
)

/** Result of a quality check; [OK] means the frame may be captured. Messages are Chinese UI hints. */
enum class QualityReason(val messageZh: String) {
    OK("对准良好，可以采集"),
    NOT_TRACKING("移动手机以稳定追踪"),
    NO_DEPTH("未读到中心深度：把物体放进取景框，稍微移动手机等待深度稳定"),
    DEPTH_FOCUS_MISMATCH("对焦已在近处，但中心深度不一致：把物体对准取景框中心，稍微移动手机"),
    TOO_CLOSE("离远一点"),
    TOO_FAR("靠近一点"),
    BLURRY("保持稳定，画面不够清晰"),
}

/** Pure real-time capture-quality decision. Checks tracking → distance → sharpness, in order. */
class FrameQualityGate(private val thresholds: QualityThresholds) {
    fun evaluate(
        tracking: TrackingStateLite,
        distanceM: Double,
        sharpness: Double,
        focusDistanceM: Double? = null,
    ): QualityReason {
        if (tracking != TrackingStateLite.TRACKING) return QualityReason.NOT_TRACKING
        if (distanceM <= 0.0) return QualityReason.NO_DEPTH
        if (focusDistanceM != null && distanceM > thresholds.maxDistanceM) {
            if (focusDistanceM < thresholds.minDistanceM) return QualityReason.TOO_CLOSE
            if (focusDistanceM <= thresholds.maxDistanceM) return QualityReason.DEPTH_FOCUS_MISMATCH
        }
        if (distanceM < thresholds.minDistanceM) return QualityReason.TOO_CLOSE
        if (distanceM > thresholds.maxDistanceM) return QualityReason.TOO_FAR
        if (sharpness < thresholds.minSharpness) return QualityReason.BLURRY
        return QualityReason.OK
    }
}
