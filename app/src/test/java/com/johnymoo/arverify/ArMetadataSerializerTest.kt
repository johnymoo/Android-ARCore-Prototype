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
            distanceM = 0.25,
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
            """"camera_pose":{"t":[0,0,0],"q":[0,0,0,1]},"distance_m":0.25},""" +
            """"coarse_hints":{"rough_units_x":2,"rough_units_y":4,"rough_pitch_mm":16.2}}"""
        assertEquals(expected, json)
    }

    @Test fun omitsCoarseHintsWhenNull() {
        val json = ArMetadataSerializer.toJson(goldenMeta(null))
        assertFalse(json.contains("coarse_hints"))
        assertTrue(json.endsWith("\"distance_m\":0.25}}"))
    }

    @Test fun realFloatsAndStringEscaping() {
        val json = ArMetadataSerializer.toJson(
            ArMetadata(
                device = DeviceMeta(model = "A\"B", arcore = "1.0"),
                recognitionFrame = RecognitionFrameMeta(
                    imageIntrinsics = CameraIntrinsics(1466.5, 1466.5, 960.0, 540.0, 1920, 1080),
                    depth = DepthDims(160, 90),
                    cameraPose = CameraPose(listOf(0.01, -0.02, 0.3), listOf(0.0, 0.0, 0.0, 1.0)),
                    distanceM = 0.273,
                ),
                coarseHints = null,
            )
        )
        assertTrue(json.contains("\"fx\":1466.5"))
        assertTrue(json.contains("\"width\":1920"))
        assertTrue(json.contains("\"distance_m\":0.273"))
        assertTrue(json.contains("\"model\":\"A\\\"B\""))
    }
}
