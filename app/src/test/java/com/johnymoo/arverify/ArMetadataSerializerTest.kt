package com.johnymoo.arverify

import com.johnymoo.arverify.metadata.ArMetadata
import com.johnymoo.arverify.metadata.ArMetadataSerializer
import com.johnymoo.arverify.metadata.CameraIntrinsics
import com.johnymoo.arverify.metadata.CameraPose
import com.johnymoo.arverify.metadata.CoarseHints
import com.johnymoo.arverify.metadata.DepthDims
import com.johnymoo.arverify.metadata.DeviceMeta
import com.johnymoo.arverify.metadata.RecognitionFrameMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArMetadataSerializerTest {
    private fun goldenMeta(hints: CoarseHints?) = ArMetadata(
        device = DeviceMeta(model = "PLG110", arcore = "1.54.260890493"),
        recognitionFrame = RecognitionFrameMeta(
            imageIntrinsics = CameraIntrinsics(0.0, 0.0, 0.0, 0.0, 0, 0),
            depth = DepthDims(0, 0),
            cameraPose = CameraPose(t = listOf(0.0, 0.0, 0.0), q = listOf(0.0, 0.0, 0.0, 1.0)),
            distanceM = 0.22584816,
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
        ),
        coarseHints = hints,
    )

    @Test fun matchesContractGoldenExactly() {
        val json = ArMetadataSerializer.toJson(
            goldenMeta(CoarseHints(roughUnitsX = 2, roughUnitsY = 4, roughPitchMm = 16.2))
        )
        val expected = """{"device":{"model":"PLG110","arcore":"1.54.260890493"},""" +
            """"recognition_frame":{"image_intrinsics":{"fx":0,"fy":0,"cx":0,"cy":0,"width":0,"height":0},""" +
            """"depth":{"width":0,"height":0,"format":"DEPTH16_MM"},""" +
            """"camera_pose":{"t":[0,0,0],"q":[0,0,0,1]},""" +
            """"distance_m":0.22584816,"arcore_distance_m":0.673,"arcore_distance_source":"DEPTH",""" +
            """"visual_distance_m":0.22584816,"visual_reference_width_px":65,"visual_reference_width_mm":33.84,""" +
            """"visual_reference_status":"DETECTED","distance_source_for_scale":"VISUAL_REFERENCE","distance_confidence":"HIGH",""" +
            """"reference_mode_enabled":true,"manual_measurement_recommended":false},""" +
            """"coarse_hints":{"rough_units_x":2,"rough_units_y":4,"rough_pitch_mm":16.2}}"""
        assertEquals(expected, json)
    }

    @Test fun omitsCoarseHintsWhenNullButKeepsScaleFields() {
        val json = ArMetadataSerializer.toJson(goldenMeta(null))
        assertFalse(json.contains("coarse_hints"))
        assertTrue(json.contains("\"distance_m\":0.22584816"))
        assertTrue(json.contains("\"arcore_distance_m\":0.673"))
        assertTrue(json.contains("\"visual_reference_width_px\":65"))
        assertTrue(json.contains("\"reference_mode_enabled\":true"))
        assertTrue(json.contains("\"manual_measurement_recommended\":false"))
        assertTrue(json.endsWith("\"manual_measurement_recommended\":false}}"))
    }

    @Test fun realFloatsAndStringEscaping() {
        val json = ArMetadataSerializer.toJson(
            ArMetadata(
                device = DeviceMeta(model = "A\"B", arcore = "1.0"),
                recognitionFrame = RecognitionFrameMeta(
                    imageIntrinsics = CameraIntrinsics(1466.5, 1466.5, 960.0, 540.0, 1920, 1080),
                    depth = DepthDims(160, 90),
                    cameraPose = CameraPose(listOf(0.01, -0.02, 0.3), listOf(0.0, 0.0, 0.0, 1.0)),
                    distanceM = 0.18350163,
                    arcoreDistanceM = 0.5807,
                    arcoreDistanceSource = "DEPTH",
                    visualDistanceM = 0.18350163,
                    visualReferenceWidthPx = 80.0,
                    visualReferenceWidthMm = 33.84,
                    visualReferenceStatus = "DETECTED",
                    distanceSourceForScale = "VISUAL_REFERENCE",
                    distanceConfidence = "HIGH",
                ),
                coarseHints = null,
            )
        )
        assertTrue(json.contains("\"fx\":1466.5"))
        assertTrue(json.contains("\"width\":1920"))
        assertTrue(json.contains("\"distance_m\":0.18350163"))
        assertTrue(json.contains("\"arcore_distance_m\":0.5807"))
        assertTrue(json.contains("\"visual_reference_width_px\":80"))
        assertTrue(json.contains("\"model\":\"A\\\"B\""))
    }

    @Test fun serializesNullVisualFieldsForArcoreFallback() {
        val json = ArMetadataSerializer.toJson(
            ArMetadata(
                device = DeviceMeta(model = "PLG110", arcore = "1.54"),
                recognitionFrame = RecognitionFrameMeta(
                    imageIntrinsics = CameraIntrinsics(433.81, 433.81, 320.0, 240.0, 640, 480),
                    depth = DepthDims(160, 90),
                    cameraPose = CameraPose(listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0, 1.0)),
                    distanceM = 0.673,
                    arcoreDistanceM = 0.673,
                    arcoreDistanceSource = "HIT_TEST",
                    visualDistanceM = null,
                    visualReferenceWidthPx = null,
                    visualReferenceWidthMm = 33.84,
                    visualReferenceStatus = "NO_RED_REFERENCE",
                    distanceSourceForScale = "ARCORE_DISTANCE",
                    distanceConfidence = "LOW",
                    referenceModeEnabled = false,
                    manualMeasurementRecommended = true,
                ),
                coarseHints = null,
            )
        )

        assertTrue(json.contains("\"visual_distance_m\":null"))
        assertTrue(json.contains("\"visual_reference_width_px\":null"))
        assertTrue(json.contains("\"distance_source_for_scale\":\"ARCORE_DISTANCE\""))
        assertTrue(json.contains("\"distance_confidence\":\"LOW\""))
        assertTrue(json.contains("\"reference_mode_enabled\":false"))
        assertTrue(json.contains("\"manual_measurement_recommended\":true"))
    }
}
