package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureHolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureHolderTest {
    @Test fun setReferenceModeEnabledUpdatesInitialCaptureState() {
        val holder = CaptureHolder()

        assertFalse(holder.state.value.referenceModeEnabled)

        holder.setReferenceModeEnabled(true)

        assertTrue(holder.state.value.referenceModeEnabled)
    }
}
