package com.johnymoo.arverify.measure

/** Validates guided-caliper inputs: every required field must be a positive, finite number. */
object MeasurementValidator {
    /** Returns a Chinese error message for the first invalid field, or null if all are valid. */
    fun firstError(required: List<String>, values: Map<String, Double?>): String? {
        for (key in required) {
            val v = values[key]
            if (v == null) return "请填写：$key"
            if (v.isNaN() || v.isInfinite() || v <= 0.0) return "数值无效：$key"
        }
        return null
    }
}
