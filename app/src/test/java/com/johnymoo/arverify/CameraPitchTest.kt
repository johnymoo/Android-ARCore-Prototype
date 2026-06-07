package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CameraPitch
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPitchTest {
    @Test fun identityIsLevel() {
        assertEquals(0.0, CameraPitch.fromQuaternion(0.0, 0.0, 0.0, 1.0), 0.5)
    }

    @Test fun rotatedDownIsNegative90() {
        assertEquals(-90.0, CameraPitch.fromQuaternion(-0.7071, 0.0, 0.0, 0.7071), 0.5)
    }

    @Test fun rotatedUpIsPositive90() {
        assertEquals(90.0, CameraPitch.fromQuaternion(0.7071, 0.0, 0.0, 0.7071), 0.5)
    }
}
