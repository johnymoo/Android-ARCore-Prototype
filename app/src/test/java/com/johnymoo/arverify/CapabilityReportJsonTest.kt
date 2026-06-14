package com.johnymoo.arverify

import com.johnymoo.arverify.model.ArCoreStatus
import com.johnymoo.arverify.model.CapabilityReport
import com.johnymoo.arverify.model.DeviceInfo
import com.johnymoo.arverify.model.RecommendedRoute
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityReportJsonTest {
    private fun sample() = CapabilityReport(
        deviceInfo = DeviceInfo(
            manufacturer = "OPPO", model = "PLG110", device = "x9pro",
            androidRelease = "15", sdkInt = 35,
            fingerprint = "oppo/PLG110/x9pro", glesVersion = "OpenGL ES 3.2"
        ),
        arcoreStatus = ArCoreStatus.SUPPORTED_INSTALLED,
        depthAutomaticSupported = true,
        rawDepthSupported = false,
        recommendedRoute = RecommendedRoute.DEPTH_CAPTURE,
        notes = listOf("ok \"quote\" test"),
        timestampIso = "2026-06-06T10:30:00",
    )

    @Test fun jsonContainsKeyFields() {
        val j = sample().toJson()
        assertTrue(j.contains("\"model\": \"PLG110\""))
        assertTrue(j.contains("\"arcoreStatus\": \"SUPPORTED_INSTALLED\""))
        assertTrue(j.contains("\"depthAutomaticSupported\": true"))
        assertTrue(j.contains("\"recommendedRoute\": \"DEPTH_CAPTURE\""))
    }

    @Test fun jsonEscapesQuotes() {
        assertTrue(sample().toJson().contains("\\\"quote\\\""))
    }

    @Test fun readableTextHasChineseRoute() {
        assertTrue(sample().toReadableText().contains("深度采集"))
    }
}
