package com.johnymoo.arverify

import com.google.gson.JsonParser
import com.johnymoo.arverify.capture.DepthDiagnostics
import com.johnymoo.arverify.capture.DepthFrameDiagnostics
import com.johnymoo.arverify.capture.QualityReason
import com.johnymoo.arverify.depth.DepthRangeMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthDiagnosticsTest {
    @Test fun summarizesCenterRoiAndConfiguredRangeCoverage() {
        val grid = IntArray(100 * 100) { 1_800 }
        for (y in 45 until 55) {
            for (x in 45 until 55) {
                grid[y * 100 + x] = 240
            }
        }

        val summary = DepthDiagnostics.summarize(
            depthMm = grid,
            width = 100,
            height = 100,
            range = DepthRangeMm(150, 700),
            roiFraction = 0.56,
        )

        assertEquals(3_136, summary.roiTotalPixels)
        assertEquals(3_136, summary.roiValidDepthPixels)
        assertEquals(100, summary.roiInRangePixels)
        assertEquals(0.0319, summary.roiInRangeCoverage, 0.0005)
        assertEquals(240, summary.percentilesMm.p01)
        assertEquals(1_800, summary.percentilesMm.p50)
        assertEquals(1_800, summary.percentilesMm.p90)
    }

    @Test fun explainsWhyDepthCannotLock() {
        val grid = IntArray(20 * 20) { 2_800 }

        val reason = DepthDiagnostics.lockFailureReason(
            depthMm = grid,
            width = 20,
            height = 20,
            range = DepthRangeMm(150, 700),
            roiFraction = 0.56,
        )

        assertEquals("NO_IN_RANGE_DEPTH", reason)
    }

    @Test fun serializesUnavailableDepthReasonForBugReports() {
        val report = DepthFrameDiagnostics(
            createdAtEpochMs = 123_456L,
            deviceModel = "PLG110",
            arcoreVersion = "1.54",
            trackingState = "TRACKING",
            qualityReason = QualityReason.NO_DEPTH.name,
            targetLocked = false,
            distanceM = 0.0,
            distanceSource = "NONE",
            fallbackDistanceM = null,
            scaleDistanceM = 0.0,
            arcoreDistanceM = 0.0,
            arcoreDistanceSource = "NONE",
            visualDistanceM = null,
            visualReferenceWidthPx = null,
            visualReferenceWidthMm = 33.84,
            visualReferenceStatus = "RGB_UNAVAILABLE",
            distanceSourceForScale = "ARCORE_DISTANCE",
            distanceConfidence = "LOW",
            sharpness = 73.0,
            depthAvailable = false,
            depthUnavailableReason = "NotYetAvailableException",
            depthWidth = 0,
            depthHeight = 0,
            depthRangeMinMm = 150,
            depthRangeMaxMm = 700,
            roiFraction = 0.56,
            lockFailureReason = "DEPTH_NOT_AVAILABLE",
            depthSummary = null,
        )

        val obj = JsonParser.parseString(DepthDiagnostics.toJson(report)).asJsonObject

        assertFalse(obj["depth_available"].asBoolean)
        assertEquals("NotYetAvailableException", obj["depth_unavailable_reason"].asString)
        assertTrue(obj["depth_summary"].isJsonNull)
        assertEquals("NO_DEPTH", obj["quality_reason"].asString)
        assertEquals("NONE", obj["distance_source"].asString)
        assertTrue(obj["fallback_distance_m"].isJsonNull)
        assertEquals(0.0, obj["scale_distance_m"].asDouble, 0.0)
        assertEquals(0.0, obj["arcore_distance_m"].asDouble, 0.0)
        assertEquals("NONE", obj["arcore_distance_source"].asString)
        assertTrue(obj["visual_distance_m"].isJsonNull)
        assertTrue(obj["visual_reference_width_px"].isJsonNull)
        assertEquals(33.84, obj["visual_reference_width_mm"].asDouble, 0.0)
        assertEquals("RGB_UNAVAILABLE", obj["visual_reference_status"].asString)
        assertEquals("ARCORE_DISTANCE", obj["distance_source_for_scale"].asString)
        assertEquals("LOW", obj["distance_confidence"].asString)
        assertEquals("DEPTH_NOT_AVAILABLE", obj["lock_failure_reason"].asString)
    }
}
