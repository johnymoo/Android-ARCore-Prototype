package com.johnymoo.arverify.capture

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
        val distance = if (state.distanceM > 0) "%.2fm".format(state.distanceM) else "--"
        val target = if (state.targetLocked) "目标锁定" else "未锁定"
        return "$target · 深度 $distance · 清晰度 %.0f".format(state.sharpness)
    }
}
