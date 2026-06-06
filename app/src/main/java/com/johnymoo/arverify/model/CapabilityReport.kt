package com.johnymoo.arverify.model

/** Normalized ARCore availability status (mapped from ArCoreApk.Availability). */
enum class ArCoreStatus {
    SUPPORTED_INSTALLED,
    SUPPORTED_NOT_INSTALLED,
    SUPPORTED_APK_TOO_OLD,
    UNSUPPORTED,
    UNKNOWN,
}

/** The full device compatibility report; serializable to JSON and human-readable text. */
data class CapabilityReport(
    val deviceInfo: DeviceInfo,
    val arcoreStatus: ArCoreStatus,
    val depthAutomaticSupported: Boolean,
    val rawDepthSupported: Boolean,
    val recommendedRoute: RecommendedRoute,
    val notes: List<String>,
    val timestampIso: String,
) {
    val arcoreUsable: Boolean get() = arcoreStatus == ArCoreStatus.SUPPORTED_INSTALLED

    fun toJson(): String = buildString {
        append("{\n")
        append("  \"timestamp\": \"").append(esc(timestampIso)).append("\",\n")
        append("  \"device\": {\n")
        append("    \"manufacturer\": \"").append(esc(deviceInfo.manufacturer)).append("\",\n")
        append("    \"model\": \"").append(esc(deviceInfo.model)).append("\",\n")
        append("    \"device\": \"").append(esc(deviceInfo.device)).append("\",\n")
        append("    \"androidRelease\": \"").append(esc(deviceInfo.androidRelease)).append("\",\n")
        append("    \"sdkInt\": ").append(deviceInfo.sdkInt).append(",\n")
        append("    \"fingerprint\": \"").append(esc(deviceInfo.fingerprint)).append("\",\n")
        append("    \"glesVersion\": \"").append(esc(deviceInfo.glesVersion)).append("\"\n")
        append("  },\n")
        append("  \"arcoreStatus\": \"").append(arcoreStatus.name).append("\",\n")
        append("  \"depthAutomaticSupported\": ").append(depthAutomaticSupported).append(",\n")
        append("  \"rawDepthSupported\": ").append(rawDepthSupported).append(",\n")
        append("  \"recommendedRoute\": \"").append(recommendedRoute.name).append("\",\n")
        append("  \"notes\": [")
        append(notes.joinToString(", ") { "\"" + esc(it) + "\"" })
        append("]\n")
        append("}\n")
    }

    fun toReadableText(): String = buildString {
        appendLine("ARCore 兼容性报告")
        appendLine("时间: $timestampIso")
        appendLine("设备: ${deviceInfo.manufacturer} ${deviceInfo.model} (${deviceInfo.device})")
        appendLine("Android: ${deviceInfo.androidRelease} (SDK ${deviceInfo.sdkInt})")
        appendLine("OpenGL: ${deviceInfo.glesVersion}")
        appendLine("ARCore 状态: ${arcoreStatus.name}")
        appendLine("Depth (AUTOMATIC): ${if (depthAutomaticSupported) "支持" else "不支持"}")
        appendLine("Raw Depth: ${if (rawDepthSupported) "支持" else "不支持"}")
        appendLine("推荐路线: ${recommendedRoute.labelZh}")
        if (notes.isNotEmpty()) {
            appendLine("备注:")
            notes.forEach { appendLine("  - $it") }
        }
    }

    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
}
