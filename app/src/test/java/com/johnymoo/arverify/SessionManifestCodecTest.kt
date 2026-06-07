package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.RecognizedSummary
import com.johnymoo.arverify.session.SessionManifestCodec
import com.johnymoo.arverify.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManifestCodecTest {
    private val sample = CaptureSession(
        partId = "part-9f3a",
        mode = CaptureMode.RECOGNITION,
        createdAtEpochMs = 1_780_000_000_000L,
        deviceModel = "PLG110",
        status = SessionStatus.RECOGNIZED,
        frames = listOf(
            CapturedFrame(CaptureSlot.TOP, "rgb0.jpg", "depth0.png", distanceM = 0.25, sharpness = 182.0),
            CapturedFrame(CaptureSlot.SIDE, "rgb1.jpg"),
        ),
        recognized = RecognizedSummary("feile", "brick", 2, 4, 16.1, 0.93),
    )

    @Test fun roundTripPreservesAllFields() {
        val parsed = SessionManifestCodec.fromJson(SessionManifestCodec.toJson(sample))
        assertEquals(sample, parsed)
    }

    @Test fun jsonCarriesSchemaVersionAndSnakeCaseKeys() {
        val json = SessionManifestCodec.toJson(sample)
        assertTrue(json.contains("\"schema_version\""))
        assertTrue(json.contains("\"part_id\""))
        assertTrue(json.contains("\"created_at_epoch_ms\""))
    }

    @Test fun badJsonReturnsNull() {
        assertNull(SessionManifestCodec.fromJson("not json"))
        assertNull(SessionManifestCodec.fromJson("[]"))
        assertNull(SessionManifestCodec.fromJson("{\"mode\":\"RECOGNITION\"}"))
    }

    @Test fun missingOptionalFieldsTolerated() {
        val json = """{"part_id":"p","mode":"GENERAL","created_at_epoch_ms":5,
            "device_model":"X","status":"EXPORTED","frames":[
              {"slot":"ANGLE","rgb":"a.jpg"},
              {"slot":"BOGUS","rgb":"skip.jpg"},
              {"rgb":"norole.jpg"}
            ]}"""
        val parsed = SessionManifestCodec.fromJson(json)!!
        assertEquals(CaptureMode.GENERAL, parsed.mode)
        assertEquals(1, parsed.frames.size)
        assertEquals(CaptureSlot.ANGLE, parsed.frames[0].slot)
        assertNull(parsed.recognized)
    }
}
