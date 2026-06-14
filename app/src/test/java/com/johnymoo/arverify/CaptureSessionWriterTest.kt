package com.johnymoo.arverify

import com.google.gson.JsonParser
import com.johnymoo.arverify.capture.CaptureSessionWriter
import com.johnymoo.arverify.capture.DistanceConfidence
import com.johnymoo.arverify.capture.DistanceSourceForScale
import com.johnymoo.arverify.capture.ScaleDistanceEstimate
import com.johnymoo.arverify.capture.TargetDistanceSource
import com.johnymoo.arverify.depth.DepthRangeMm
import com.johnymoo.arverify.metadata.CameraIntrinsics
import com.johnymoo.arverify.metadata.CameraPose
import com.johnymoo.arverify.session.CaptureMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureSessionWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writeRecognitionPersistsVisualScaleMetadata() {
        val writer = CaptureSessionWriter(
            rootDir = tmp.newFolder("captures"),
            partId = "part-scale",
            mode = CaptureMode.RECOGNITION,
            createdAtEpochMs = 1234L,
            deviceModel = "PLG110",
            depthRange = DepthRangeMm(150, 700),
        )
        val rgb = byteArrayOf(9, 8, 7)
        val depth = intArrayOf(200, 210, 220, 230)
        val scale = ScaleDistanceEstimate(
            distanceM = 0.22584816,
            arcoreDistanceM = 0.673,
            arcoreDistanceSource = TargetDistanceSource.DEPTH,
            visualDistanceM = 0.22584816,
            visualReferenceWidthPx = 65.0,
            visualReferenceWidthMm = 33.84,
            visualReferenceStatus = "DETECTED",
            distanceSourceForScale = DistanceSourceForScale.VISUAL_REFERENCE,
            distanceConfidence = DistanceConfidence.HIGH,
            referenceModeEnabled = true,
            manualMeasurementRecommended = false,
        )

        writer.writeRecognition(
            intrinsics = CameraIntrinsics(433.81, 433.81, 320.0, 240.0, 640, 480),
            pose = CameraPose(listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0, 1.0)),
            scaleDistance = scale,
            depthGridMm = depth,
            depthW = 2,
            depthH = 2,
            rgbJpeg = rgb,
            arcoreVersion = "1.54",
        )

        assertArrayEquals(rgb, writer.dir.resolve("recognition_rgb.jpg").readBytes())
        assertTrue(writer.dir.resolve("recognition_depth.png").isFile)

        val frame = JsonParser.parseString(writer.dir.resolve("ar_metadata.json").readText())
            .asJsonObject["recognition_frame"].asJsonObject
        assertEquals(0.22584816, frame["distance_m"].asDouble, 1e-9)
        assertEquals(0.673, frame["arcore_distance_m"].asDouble, 0.0)
        assertEquals("DEPTH", frame["arcore_distance_source"].asString)
        assertEquals(65.0, frame["visual_reference_width_px"].asDouble, 0.0)
        assertEquals("VISUAL_REFERENCE", frame["distance_source_for_scale"].asString)
        assertEquals("HIGH", frame["distance_confidence"].asString)
        assertEquals(true, frame["reference_mode_enabled"].asBoolean)
        assertEquals(false, frame["manual_measurement_recommended"].asBoolean)
    }
}
