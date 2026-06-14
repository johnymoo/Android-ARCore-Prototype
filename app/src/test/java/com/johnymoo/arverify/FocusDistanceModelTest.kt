package com.johnymoo.arverify

import com.johnymoo.arverify.capture.FocusDistanceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusDistanceModelTest {
    @Test fun convertsDioptersToMeters() {
        assertEquals(0.20, FocusDistanceModel.metersFromDiopters(5.0f)!!, 1e-9)
        assertEquals(0.50, FocusDistanceModel.metersFromDiopters(2.0f)!!, 1e-9)
    }

    @Test fun zeroDioptersIsUnknownForCloseRangeGuidance() {
        assertNull(FocusDistanceModel.metersFromDiopters(0.0f))
    }

    @Test fun onlyFocusedAfStatesAreTrustedForGate() {
        assertEquals(true, FocusDistanceModel.isFocusedAfState(2))
        assertEquals(true, FocusDistanceModel.isFocusedAfState(4))
        assertEquals(false, FocusDistanceModel.isFocusedAfState(null))
        assertEquals(false, FocusDistanceModel.isFocusedAfState(1))
        assertEquals(false, FocusDistanceModel.isFocusedAfState(5))
    }

    @Test fun focusDistanceCanGuideGateWhenAfStateIsUnknown() {
        assertEquals(0.25, FocusDistanceModel.gateDistanceMeters(0.25, null)!!, 1e-9)
        assertEquals(0.25, FocusDistanceModel.gateDistanceMeters(0.25, 4)!!, 1e-9)
        assertNull(FocusDistanceModel.gateDistanceMeters(0.25, 1))
    }

    @Test fun formatsFocusMetersForDebugUi() {
        assertEquals("焦点 0.20m", FocusDistanceModel.debugText(0.2, null))
        assertEquals("焦点 --", FocusDistanceModel.debugText(null, null))
    }
}
