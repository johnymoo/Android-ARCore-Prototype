package com.johnymoo.arverify.measure

import kotlin.math.abs

/** Result of validating the 5 caliper values. */
data class CrossCheckResult(val errors: List<String>, val warnings: List<String>) {
    val canSubmit: Boolean get() = errors.isEmpty()
}

/**
 * Two-tier validation for the 5 caliper measurements.
 *
 * Red (errors, block submit): every value present/positive/finite;
 *   1A > 1B; ④ > ②. (Backend only warns on the last two; we block them
 *   because they are physically impossible for a real brick.)
 * Yellow (warnings, non-blocking): |(1A-1B)/2 - ③| > 0.5mm
 *   (mirrors backend _CROSS_CHECK_TOL_MM = 0.5).
 */
object MeasurementCrossCheck {
    const val TOL_MM = 0.5

    private const val OUTER = "outer_pitch_mm"
    private const val INNER = "inner_pitch_mm"
    private const val STUD = "stud_diameter_mm"
    private const val NET = "brick_height_net_mm"
    private const val TOTAL = "brick_height_total_mm"

    fun check(values: Map<String, Double?>): CrossCheckResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1) presence + positivity + finiteness for the 5 canonical keys
        for (key in MeasurementCatalog.CANONICAL_KEYS) {
            val v = values[key]
            val code = MeasurementCatalog.codeFor(key)
            if (v == null) { errors.add("请填写：$code"); continue }
            if (v.isNaN() || v.isInfinite() || v <= 0.0) errors.add("数值无效：$code")
        }

        fun valid(k: String): Double? =
            values[k]?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }

        val outer = valid(OUTER); val inner = valid(INNER)
        val net = valid(NET); val total = valid(TOTAL); val stud = valid(STUD)

        // 2) cross rules (only when the involved values are individually valid)
        if (outer != null && inner != null && outer <= inner) {
            errors.add("1A 应大于 1B（外径距 > 内径距）")
        }
        if (net != null && total != null && total <= net) {
            errors.add("④ 应大于 ②（总高 > 净高）")
        }

        // 3) stud-diameter cross-check (warning only)
        if (outer != null && inner != null && stud != null) {
            val derived = (outer - inner) / 2.0
            if (abs(derived - stud) > TOL_MM) {
                warnings.add(
                    "③ 与 (1A−1B)/2 相差 ${"%.2f".format(abs(derived - stud))}mm（>0.5），请核对 1B",
                )
            }
        }
        return CrossCheckResult(errors, warnings)
    }
}
