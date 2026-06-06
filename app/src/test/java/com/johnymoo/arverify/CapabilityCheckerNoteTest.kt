package com.johnymoo.arverify

import com.johnymoo.arverify.ar.CapabilityChecker
import com.johnymoo.arverify.model.ArCoreStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityCheckerNoteTest {
    @Test fun unsupportedDeviceNoteDoesNotSayArcoreIsMissing() {
        val note = CapabilityChecker.depthProbeSkippedNote(ArCoreStatus.UNSUPPORTED)

        assertTrue(note.contains("设备不支持 ARCore"))
        assertFalse(note.contains("未安装"))
    }
}
