package com.johnymoo.arverify

import com.johnymoo.arverify.capture.FrameQualityGate
import com.johnymoo.arverify.capture.QualityReason
import com.johnymoo.arverify.capture.QualityThresholds
import com.johnymoo.arverify.capture.TrackingStateLite
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameQualityGateTest {
    private val gate = FrameQualityGate(QualityThresholds(minDistanceM = 0.15, maxDistanceM = 0.45, minSharpness = 120.0))

    @Test fun notTrackingFailsFirst() {
        assertEquals(QualityReason.NOT_TRACKING, gate.evaluate(TrackingStateLite.PAUSED, 0.25, 999.0))
    }

    @Test fun tooClose() {
        assertEquals(QualityReason.TOO_CLOSE, gate.evaluate(TrackingStateLite.TRACKING, 0.10, 999.0))
    }

    @Test fun tooFar() {
        assertEquals(QualityReason.TOO_FAR, gate.evaluate(TrackingStateLite.TRACKING, 0.60, 999.0))
    }

    @Test fun blurryWhenInRangeButLowSharpness() {
        assertEquals(QualityReason.BLURRY, gate.evaluate(TrackingStateLite.TRACKING, 0.25, 50.0))
    }

    @Test fun okWhenAllPass() {
        assertEquals(QualityReason.OK, gate.evaluate(TrackingStateLite.TRACKING, 0.25, 130.0))
    }

    @Test fun boundariesInclusive() {
        assertEquals(QualityReason.OK, gate.evaluate(TrackingStateLite.TRACKING, 0.15, 120.0))
        assertEquals(QualityReason.OK, gate.evaluate(TrackingStateLite.TRACKING, 0.45, 120.0))
    }
}
