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
    NO_DEPTH("未读到取景框深度：把积木放进框里，稍微移动手机等待深度稳定"),
    NO_TARGET("未锁定积木：把积木主体移入取景框，避免只看到背景"),
    DEPTH_FOCUS_MISMATCH("取景框深度不一致：让框覆盖积木主体，避开远处背景"),
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
        targetLocked: Boolean = true,
    ): QualityReason {
        if (tracking != TrackingStateLite.TRACKING) return QualityReason.NOT_TRACKING
        if (distanceM <= 0.0) return QualityReason.NO_DEPTH
        if (!targetLocked) return QualityReason.NO_TARGET
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
