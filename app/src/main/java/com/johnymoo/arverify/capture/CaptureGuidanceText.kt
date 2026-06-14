package com.johnymoo.arverify.capture

import java.util.Locale

object CaptureGuidanceText {
    fun primaryPrompt(state: CaptureUiState): String = when (state.qualityReason) {
        QualityReason.OK -> "可以拍摄"
        QualityReason.NOT_TRACKING -> "缓慢移动手机，稳定追踪"
        QualityReason.NO_DEPTH -> "等待深度稳定"
        QualityReason.NO_TARGET -> "把积木主体放进取景框"
        QualityReason.DEPTH_FOCUS_MISMATCH -> "让取景框覆盖积木主体"
        QualityReason.TOO_CLOSE -> "目标已找到，离远一点"
        QualityReason.TOO_FAR -> "目标已找到，靠近一点"
        QualityReason.BLURRY -> "目标已找到，保持稳定"
    }

    fun statusLine(state: CaptureUiState): String {
        val gateDistance = when {
            state.distanceM > 0 -> String.format(Locale.US, "%.2fm", state.distanceM)
            else -> "--"
        }
        val scaleDistance = when {
            state.scaleDistanceM > 0 -> String.format(Locale.US, "%.2fm", state.scaleDistanceM)
            else -> "--"
        }
        val gateLabel = when (state.distanceSource) {
            TargetDistanceSource.HIT_TEST -> "平面"
            TargetDistanceSource.DEPTH -> "深度"
            TargetDistanceSource.NONE -> "无"
        }
        val confidence = when (state.distanceConfidence) {
            DistanceConfidence.HIGH -> "高可信"
            DistanceConfidence.LOW -> "低可信"
        }
        val target = if (state.targetLocked) "目标锁定" else "未锁定"
        return String.format(
            Locale.US,
            "$target · 尺度 $scaleDistance · 门控 $gateLabel $gateDistance · $confidence · 清晰度 %.0f",
            state.sharpness,
        )
    }
}
