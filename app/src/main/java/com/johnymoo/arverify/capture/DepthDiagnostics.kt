package com.johnymoo.arverify.capture

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.johnymoo.arverify.depth.DepthRangeMm

data class DepthPercentiles(
    @SerializedName("p01_mm") val p01: Int,
    @SerializedName("p10_mm") val p10: Int,
    @SerializedName("p50_mm") val p50: Int,
    @SerializedName("p90_mm") val p90: Int,
    @SerializedName("p99_mm") val p99: Int,
)

data class DepthSummary(
    @SerializedName("roi_total_pixels") val roiTotalPixels: Int,
    @SerializedName("roi_valid_depth_pixels") val roiValidDepthPixels: Int,
    @SerializedName("roi_in_range_pixels") val roiInRangePixels: Int,
    @SerializedName("roi_valid_coverage") val roiValidCoverage: Double,
    @SerializedName("roi_in_range_coverage") val roiInRangeCoverage: Double,
    @SerializedName("percentiles_mm") val percentilesMm: DepthPercentiles,
)

data class DepthFrameDiagnostics(
    @SerializedName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("arcore_version") val arcoreVersion: String,
    @SerializedName("tracking_state") val trackingState: String,
    @SerializedName("quality_reason") val qualityReason: String,
    @SerializedName("target_locked") val targetLocked: Boolean,
    @SerializedName("distance_m") val distanceM: Double,
    @SerializedName("distance_source") val distanceSource: String,
    @SerializedName("fallback_distance_m") val fallbackDistanceM: Double?,
    @SerializedName("scale_distance_m") val scaleDistanceM: Double = distanceM,
    @SerializedName("arcore_distance_m") val arcoreDistanceM: Double = distanceM,
    @SerializedName("arcore_distance_source") val arcoreDistanceSource: String = distanceSource,
    @SerializedName("visual_distance_m") val visualDistanceM: Double? = null,
    @SerializedName("visual_reference_width_px") val visualReferenceWidthPx: Double? = null,
    @SerializedName("visual_reference_width_mm") val visualReferenceWidthMm: Double = 33.84,
    @SerializedName("visual_reference_status") val visualReferenceStatus: String = "NOT_EVALUATED",
    @SerializedName("distance_source_for_scale") val distanceSourceForScale: String = "ARCORE_DISTANCE",
    @SerializedName("distance_confidence") val distanceConfidence: String = "LOW",
    @SerializedName("sharpness") val sharpness: Double,
    @SerializedName("depth_available") val depthAvailable: Boolean,
    @SerializedName("depth_unavailable_reason") val depthUnavailableReason: String?,
    @SerializedName("depth_width") val depthWidth: Int,
    @SerializedName("depth_height") val depthHeight: Int,
    @SerializedName("depth_range_min_mm") val depthRangeMinMm: Int,
    @SerializedName("depth_range_max_mm") val depthRangeMaxMm: Int,
    @SerializedName("roi_fraction") val roiFraction: Double,
    @SerializedName("lock_failure_reason") val lockFailureReason: String?,
    @SerializedName("depth_summary") val depthSummary: DepthSummary?,
)

object DepthDiagnostics {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    fun summarize(
        depthMm: IntArray,
        width: Int,
        height: Int,
        range: DepthRangeMm,
        roiFraction: Double = 0.56,
    ): DepthSummary {
        if (width <= 0 || height <= 0 || depthMm.size < width * height) {
            return DepthSummary(0, 0, 0, 0.0, 0.0, DepthPercentiles(0, 0, 0, 0, 0))
        }

        val samples = roiSamples(depthMm, width, height, roiFraction)
        val valid = samples.filter { it > 0 }.sorted()
        val inRange = valid.count { it in range.minMm..range.maxMm }
        return DepthSummary(
            roiTotalPixels = samples.size,
            roiValidDepthPixels = valid.size,
            roiInRangePixels = inRange,
            roiValidCoverage = if (samples.isEmpty()) 0.0 else valid.size.toDouble() / samples.size.toDouble(),
            roiInRangeCoverage = if (samples.isEmpty()) 0.0 else inRange.toDouble() / samples.size.toDouble(),
            percentilesMm = if (valid.isEmpty()) DepthPercentiles(0, 0, 0, 0, 0) else DepthPercentiles(
                p01 = percentile(valid, 0.01),
                p10 = percentile(valid, 0.10),
                p50 = percentile(valid, 0.50),
                p90 = percentile(valid, 0.90),
                p99 = percentile(valid, 0.99),
            ),
        )
    }

    fun toJson(report: DepthFrameDiagnostics): String = gson.toJson(report)

    fun lockFailureReason(
        depthMm: IntArray?,
        width: Int,
        height: Int,
        range: DepthRangeMm,
        roiFraction: Double = 0.56,
        minCoverage: Double = 0.015,
    ): String? {
        if (depthMm == null || width <= 0 || height <= 0) return "DEPTH_NOT_AVAILABLE"
        val summary = summarize(depthMm, width, height, range, roiFraction)
        if (summary.roiValidDepthPixels == 0) return "NO_VALID_DEPTH_IN_RETICLE"
        if (summary.roiInRangePixels == 0) return "NO_IN_RANGE_DEPTH"
        if (summary.roiInRangeCoverage < minCoverage) return "IN_RANGE_COVERAGE_TOO_LOW"
        return null
    }

    private fun roiSamples(
        depthMm: IntArray,
        width: Int,
        height: Int,
        roiFraction: Double,
    ): List<Int> {
        val f = roiFraction.coerceIn(0.05, 1.0)
        val winW = (width * f).toInt().coerceAtLeast(1)
        val winH = (height * f).toInt().coerceAtLeast(1)
        val x0 = (width - winW) / 2
        val y0 = (height - winH) / 2
        val samples = ArrayList<Int>(winW * winH)
        for (y in y0 until y0 + winH) {
            val row = y * width
            for (x in x0 until x0 + winW) {
                samples.add(depthMm[row + x])
            }
        }
        return samples
    }

    private fun percentile(sorted: List<Int>, p: Double): Int {
        val idx = ((sorted.size - 1) * p.coerceIn(0.0, 1.0)).toInt()
        return sorted[idx]
    }
}
