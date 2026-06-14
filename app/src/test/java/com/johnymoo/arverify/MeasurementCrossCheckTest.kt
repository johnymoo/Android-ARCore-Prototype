package com.johnymoo.arverify

import com.johnymoo.arverify.measure.MeasurementCrossCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCrossCheckTest {
    // a physically valid LEGO-ish 2x set: 1A=31.8 1B=16.0 ③=7.9 ②=9.6 ④=11.4
    private fun good() = linkedMapOf(
        "outer_pitch_mm" to 31.8, "inner_pitch_mm" to 16.0, "stud_diameter_mm" to 7.9,
        "brick_height_net_mm" to 9.6, "brick_height_total_mm" to 11.4,
    )

    @Test fun cleanInputCanSubmitNoWarnings() {
        val r = MeasurementCrossCheck.check(good())
        assertTrue(r.canSubmit)
        assertTrue(r.errors.isEmpty())
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun missingValueBlocks() {
        val m = good().apply { remove("stud_diameter_mm") }
        val r = MeasurementCrossCheck.check(m)
        assertFalse(r.canSubmit)
        assertTrue(r.errors.any { it.contains("③") })
    }

    @Test fun nonPositiveBlocks() {
        val m = good().apply { put("brick_height_net_mm", 0.0) }
        val r = MeasurementCrossCheck.check(m)
        assertFalse(r.canSubmit)
        assertTrue(r.errors.any { it.contains("②") })
    }

    @Test fun nonFiniteBlocks() {
        val m = good().apply { put("outer_pitch_mm", Double.NaN) }
        val r = MeasurementCrossCheck.check(m)
        assertFalse(r.canSubmit)
        assertTrue(r.errors.any { it.contains("1A") })
    }

    @Test fun outerMustExceedInner_blocks() {
        val m = good().apply { put("inner_pitch_mm", 40.0) } // 1B > 1A
        val r = MeasurementCrossCheck.check(m)
        assertFalse(r.canSubmit)
        assertTrue(r.errors.any { it.contains("1A") && it.contains("1B") })
    }

    @Test fun totalMustExceedNet_blocks() {
        val m = good().apply { put("brick_height_total_mm", 9.0) } // ④ < ②
        val r = MeasurementCrossCheck.check(m)
        assertFalse(r.canSubmit)
        assertTrue(r.errors.any { it.contains("④") && it.contains("②") })
    }

    @Test fun studCrossCheckIsWarningNotBlocking() {
        // (1A-1B)/2 = (31.8-16.0)/2 = 7.9; set ③ = 8.6 -> diff 0.7 > 0.5 -> warn
        val m = good().apply { put("stud_diameter_mm", 8.6) }
        val r = MeasurementCrossCheck.check(m)
        assertTrue(r.canSubmit) // not blocked
        assertEquals(1, r.warnings.size)
        assertTrue(r.warnings[0].contains("③"))
    }

    @Test fun studCrossCheckBoundaryNoWarn() {
        // diff exactly 0.5 -> no warning (backend uses > tol)
        val m = good().apply { put("stud_diameter_mm", 8.4) } // |7.9-8.4| = 0.5
        val r = MeasurementCrossCheck.check(m)
        assertTrue(r.warnings.isEmpty())
    }
}
