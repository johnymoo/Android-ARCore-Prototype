package com.johnymoo.arverify

import com.johnymoo.arverify.measure.MeasurementValidator
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementValidatorTest {
    private val required = listOf("length", "width", "height")

    @Test fun allPresentAndPositiveIsValid() {
        assertNull(MeasurementValidator.firstError(required,
            mapOf("length" to 31.8, "width" to 15.8, "height" to 9.6)))
    }

    @Test fun missingValueReported() {
        val err = MeasurementValidator.firstError(required,
            mapOf("length" to 31.8, "width" to 15.8))
        assertTrue(err!!.contains("height"))
    }

    @Test fun nonPositiveReported() {
        val err = MeasurementValidator.firstError(required,
            mapOf("length" to 0.0, "width" to 15.8, "height" to 9.6))
        assertTrue(err!!.contains("length"))
    }

    @Test fun nanReported() {
        val err = MeasurementValidator.firstError(required,
            mapOf("length" to Double.NaN, "width" to 15.8, "height" to 9.6))
        assertTrue(err!!.contains("length"))
    }
}
