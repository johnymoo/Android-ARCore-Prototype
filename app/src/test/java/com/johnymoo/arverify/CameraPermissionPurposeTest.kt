package com.johnymoo.arverify

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionPurposeTest {
    @Test fun depthPermissionDenialExplainsDepthProbe() {
        assertEquals("需要相机权限才能探测 Depth API", CameraPermissionPurpose.CHECK_DEPTH.deniedToast)
    }

    @Test fun capturePermissionDenialExplainsCapture() {
        assertEquals("需要相机权限才能采集", CameraPermissionPurpose.CAPTURE.deniedToast)
    }
}
