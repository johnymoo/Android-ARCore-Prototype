package com.johnymoo.arverify

import com.google.gson.JsonParser
import com.johnymoo.arverify.capture.DepthFrameDiagnostics
import com.johnymoo.arverify.capture.DiagnosticSnapshotWriter
import com.johnymoo.arverify.capture.QualityReason
import com.johnymoo.arverify.depth.DepthRangeMm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticSnapshotWriterTest {
    @org.junit.Rule @JvmField val tmp = TemporaryFolder()

    @Test fun writesCompleteDiagnosticSnapshot() {
        val root = tmp.newFolder("diagnostics")
        val rgb = byteArrayOf(1, 2, 3, 4)
        val depth = intArrayOf(0, 240, 650, 1_800)
        val report = DepthFrameDiagnostics(
            createdAtEpochMs = 456L,
            deviceModel = "PLG110",
            arcoreVersion = "1.54",
            trackingState = "TRACKING",
            qualityReason = QualityReason.NO_TARGET.name,
            targetLocked = false,
            distanceM = 0.24,
            distanceSource = "HIT_TEST",
            fallbackDistanceM = 0.24,
            scaleDistanceM = 0.22584816,
            arcoreDistanceM = 0.673,
            arcoreDistanceSource = "DEPTH",
            visualDistanceM = 0.22584816,
            visualReferenceWidthPx = 65.0,
            visualReferenceWidthMm = 33.84,
            visualReferenceStatus = "DETECTED",
            distanceSourceForScale = "VISUAL_REFERENCE",
            distanceConfidence = "HIGH",
            referenceModeEnabled = true,
            manualMeasurementRecommended = false,
            sharpness = 73.0,
            depthAvailable = true,
            depthUnavailableReason = null,
            depthWidth = 2,
            depthHeight = 2,
            depthRangeMinMm = 150,
            depthRangeMaxMm = 700,
            roiFraction = 0.56,
            lockFailureReason = "IN_RANGE_COVERAGE_TOO_LOW",
            depthSummary = null,
        )

        val dir = DiagnosticSnapshotWriter(
            root,
            previewWriter = { file, _, _, _, _ -> file.writeBytes(byteArrayOf(9, 8, 7)) },
        ).write(
            report = report,
            rgbJpeg = rgb,
            depthGridMm = depth,
            depthWidth = 2,
            depthHeight = 2,
            depthRange = DepthRangeMm(150, 700),
        )

        assertTrue(dir.name.startsWith("diag-456"))
        assertArrayEquals(rgb, dir.resolve("rgb.jpg").readBytes())
        assertTrue(dir.resolve("depth.png").isFile)
        assertTrue(dir.resolve("depth_preview.png").isFile)
        val json = JsonParser.parseString(dir.resolve("stats.json").readText()).asJsonObject
        assertEquals("PLG110", json["device_model"].asString)
        assertEquals("HIT_TEST", json["distance_source"].asString)
        assertEquals(0.24, json["fallback_distance_m"].asDouble, 0.0)
        assertEquals(0.22584816, json["scale_distance_m"].asDouble, 1e-9)
        assertEquals(0.673, json["arcore_distance_m"].asDouble, 0.0)
        assertEquals("DEPTH", json["arcore_distance_source"].asString)
        assertEquals(65.0, json["visual_reference_width_px"].asDouble, 0.0)
        assertEquals("VISUAL_REFERENCE", json["distance_source_for_scale"].asString)
        assertEquals("HIGH", json["distance_confidence"].asString)
        assertEquals(true, json["reference_mode_enabled"].asBoolean)
        assertEquals(false, json["manual_measurement_recommended"].asBoolean)
        assertTrue(json["depth_available"].asBoolean)
    }
}
