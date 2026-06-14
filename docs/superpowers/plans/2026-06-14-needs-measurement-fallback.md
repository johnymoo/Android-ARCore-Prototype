# needs_measurement 人工测量建模兜底 Implementation Plan (issue #3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bare 5-field measurement form with a 2-phase guided "人工测量建模" flow (建模确认 → 5 步测量向导 → 提交结果) that submits the correct `POST /api/v1/parametric-blocks` multipart contract.

**Architecture:** Heavy logic lives in JVM-unit-tested pure Kotlin (`MeasurementCatalog`, `MeasurementCrossCheck`, `ParametricSubmission`, `RecognitionResultModel` extension). The Compose UI (`MeasurementWizardActivity`, `MeasurementDiagram`) and a small shared UI base (`ScreenScaffold` + tokens) are thin and **device-verified** (this work also seeds the base that Plan 2 reuses app-wide).

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.09.02, Material3), Gson, existing `MultipartBuilder`/`HttpTransport`, JUnit4 + kotlinx-coroutines-test.

---

## ⚠️ Toolchain reality (read first)

This **dev machine has Java 11, no Android SDK, and gradlew does not run here.** You cannot compile or run anything in this environment. Every `./gradlew …` command in this plan must be run on a machine with **JDK 17 + Android SDK** (the user's Mac), and every Compose UI task ends in an **on-device verification checkpoint** (no local test exists for UI). The pure-logic tasks (1–4) are full TDD; run their tests on the toolchain machine before moving on.

**Unit-test run command (toolchain machine):**
`./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.<TestClass>"`
Unit tests use `testOptions.unitTests.isReturnDefaultValues = true` (Android stubs return defaults).

**Contract source of truth (do not guess):** `BrickStudio/apps/api/src/api/v1/parametric_blocks.py`, `BrickStudio/apps/web/src/features/parametric/{api.ts,schema.ts}`. Design proxy for UI: `docs/superpowers/specs/assets/needs-measurement-ui-mockups/`.

---

## File Structure

**Create (pure logic, JVM-tested):**
- `app/src/main/java/com/johnymoo/arverify/measure/MeasurementCatalog.kt` — system/kind/field display data mirroring `schema.ts`; maps backend `fields[]` → ordered wizard steps; unknown-key fallback.
- `app/src/main/java/com/johnymoo/arverify/measure/MeasurementCrossCheck.kt` — two-tier validation (red errors / yellow warnings).
- `app/src/main/java/com/johnymoo/arverify/net/ParametricSubmission.kt` — builds the parametric-blocks multipart + parses the 201.

**Create (Compose, device-verified):**
- `app/src/main/java/com/johnymoo/arverify/ui/components/ScreenScaffold.kt` — shared inset-aware top bar + back (base for Plan 2).
- `app/src/main/java/com/johnymoo/arverify/measure/MeasurementDiagram.kt` — focused per-step caliper diagram (Canvas).
- `app/src/main/java/com/johnymoo/arverify/measure/MeasurementWizardActivity.kt` — the 2-phase wizard.

**Modify:**
- `app/src/main/java/com/johnymoo/arverify/net/RecognitionResultModel.kt` — add `reason` to `NeedsMeasurement`.
- `app/src/main/java/com/johnymoo/arverify/ui/theme/Color.kt` — add border/divider tokens.
- `app/src/main/AndroidManifest.xml` — rename activity entry.
- `app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt`, `capture/CaptureWizardActivity.kt`, `ui/session/SessionDetailScreen.kt` — update class reference.
- `app/src/main/res/values/strings.xml` — wizard strings.

**Delete:**
- `app/src/main/java/com/johnymoo/arverify/measure/MeasurementFormActivity.kt`
- `app/src/main/res/layout/activity_measurement_form.xml`
- `app/src/main/java/com/johnymoo/arverify/measure/MeasurementValidator.kt` + `app/src/test/java/com/johnymoo/arverify/MeasurementValidatorTest.kt` (superseded by `MeasurementCrossCheck`).

---

## Task 1: Parse `needs_measurement.reason`

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/net/RecognitionResultModel.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/RecognitionResultModelTest.kt`

- [ ] **Step 1: Write the failing test** — append to `RecognitionResultModelTest`:

```kotlin
@Test fun parsesNeedsMeasurementReason() {
    val json = """
        {"status":"needs_measurement",
         "recognized":{"system":null,"kind":"brick","units_x":22,"units_y":14,
                       "pitch_mm":163.668,"confidence":0.0},
         "needs_measurement":{
           "fields":["outer_pitch_mm","inner_pitch_mm","stud_diameter_mm",
                     "brick_height_net_mm","brick_height_total_mm"],
           "reason":"pitch did not match a known system within tolerance/confidence"}}
    """.trimIndent()
    val r = RecognitionResultModel.parse(json)
    assertEquals("pitch did not match a known system within tolerance/confidence",
        r.needsMeasurement?.reason)
    assertEquals(5, r.needsMeasurement?.fields?.size)
    assertEquals("brick", r.recognized?.kind)
    assertEquals(0.0, r.recognized?.confidence!!, 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.RecognitionResultModelTest"`
Expected: FAIL — `reason` is not a member of `NeedsMeasurement` (compile error).

- [ ] **Step 3: Add the field + parse it** — in `RecognitionResultModel.kt`, change the data class:

```kotlin
/** Guided-caliper fallback payload. */
data class NeedsMeasurement(val fields: List<MeasurementField>, val guidance: String?, val reason: String?)
```

and the construction site (inside `needsMeasurement = obj(root, "needs_measurement")?.let { nm -> … }`):

```kotlin
                NeedsMeasurement(
                    fields = fields,
                    guidance = str(nm, "guidance"),
                    reason = str(nm, "reason"),
                )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.RecognitionResultModelTest"`
Expected: PASS (all tests in the class, including the existing ones).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/net/RecognitionResultModel.kt \
        app/src/test/java/com/johnymoo/arverify/RecognitionResultModelTest.kt
git commit -m "feat: parse needs_measurement.reason"
```

---

## Task 2: `MeasurementCatalog` (display data + step mapping)

Mirrors `BrickStudio/apps/web/src/features/parametric/schema.ts` so the app, web, and backend agree on labels/order.

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/measure/MeasurementCatalog.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/MeasurementCatalogTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.measure.MeasurementCatalog
import com.johnymoo.arverify.measure.DiagramView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCatalogTest {

    @Test fun canonicalOrderHasFiveFieldsWithCodes() {
        val steps = MeasurementCatalog.stepsFor(emptyList()) // empty backend list -> canonical 5
        assertEquals(5, steps.size)
        assertEquals(listOf("outer_pitch_mm","inner_pitch_mm","stud_diameter_mm",
            "brick_height_net_mm","brick_height_total_mm"), steps.map { it.key })
        assertEquals(listOf("1A","1B","③","②","④"), steps.map { it.code })
    }

    @Test fun diagramViewSplitsTopAndSide() {
        val byKey = MeasurementCatalog.stepsFor(emptyList()).associateBy { it.key }
        assertEquals(DiagramView.TOP, byKey["outer_pitch_mm"]!!.view)
        assertEquals(DiagramView.TOP, byKey["inner_pitch_mm"]!!.view)
        assertEquals(DiagramView.TOP, byKey["stud_diameter_mm"]!!.view)
        assertEquals(DiagramView.SIDE, byKey["brick_height_net_mm"]!!.view)
        assertEquals(DiagramView.SIDE, byKey["brick_height_total_mm"]!!.view)
    }

    @Test fun backendOrderIsHonored() {
        val steps = MeasurementCatalog.stepsFor(listOf("stud_diameter_mm","outer_pitch_mm"))
        assertEquals(listOf("stud_diameter_mm","outer_pitch_mm"), steps.map { it.key })
        assertEquals("③", steps[0].code)
    }

    @Test fun unknownKeyGetsFallbackStep() {
        val steps = MeasurementCatalog.stepsFor(listOf("mystery_mm"))
        assertEquals(1, steps.size)
        assertEquals("mystery_mm", steps[0].key)
        assertEquals("mystery_mm", steps[0].code)        // fallback: code = raw key
        assertTrue(steps[0].label.contains("mystery_mm"))
    }

    @Test fun systemsAndKindsMirrorSchema() {
        assertEquals(listOf("duplo","lego","feile","generic"),
            MeasurementCatalog.SYSTEMS.map { it.value })
        assertEquals(listOf("brick","plate","tile","slope"),
            MeasurementCatalog.KINDS.map { it.value })
        assertEquals("费乐 (FEILE)", MeasurementCatalog.SYSTEMS.first { it.value=="feile" }.label)
        assertEquals(1, MeasurementCatalog.MIN_UNITS)
        assertEquals(16, MeasurementCatalog.MAX_UNITS)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.MeasurementCatalogTest"`
Expected: FAIL — `MeasurementCatalog` does not exist (compile error).

- [ ] **Step 3: Write the implementation**

Create `MeasurementCatalog.kt`:

```kotlin
package com.johnymoo.arverify.measure

/** Which sub-diagram a measurement is shown on. */
enum class DiagramView { TOP, SIDE }

/** A pickable enum value with display text (mirrors schema.ts). */
data class OptionItem(val value: String, val label: String, val hint: String, val icon: String = "")

/** One wizard step: a caliper field + its display + diagram target. */
data class FieldGuide(
    val key: String,
    val code: String,
    val label: String,
    val hint: String,
    val view: DiagramView,
)

/**
 * Display catalog for the parametric-block measurement wizard.
 * Mirrors BrickStudio/apps/web/src/features/parametric/schema.ts so the
 * Android app, the web wizard and the backend agree on labels and order.
 */
object MeasurementCatalog {
    const val MIN_UNITS = 1
    const val MAX_UNITS = 16

    val SYSTEMS = listOf(
        OptionItem("duplo", "得宝 (DUPLO)", "20 mm 节距, 1-5 岁"),
        OptionItem("lego", "乐高 (LEGO)", "8 mm 节距, 6+ 岁"),
        OptionItem("feile", "费乐 (FEILE)", "16 mm 节距, 国产大颗粒"),
        OptionItem("generic", "通用 / 自定义", "未知规格, 按比例缩"),
    )

    val KINDS = listOf(
        OptionItem("brick", "标准砖 (brick)", "凸点在顶, 标准高度", "🧱"),
        OptionItem("plate", "板 (plate)", "1/3 高度, 凸点在顶", "▭"),
        OptionItem("tile", "瓦片 (tile)", "1/3 高度, 平顶", "◼"),
        OptionItem("slope", "斜面 (slope)", "45° 楔形, 沿 X 方向降", "◣"),
    )

    /** The 5 caliper fields in canonical order; keys match _RAW_KEYS in the backend. */
    private val CANONICAL = listOf(
        FieldGuide("outer_pitch_mm", "1A", "外径距 (outer_pitch)",
            "砖块总长，跨两端凸点圆周最远点（卡尺跨外）", DiagramView.TOP),
        FieldGuide("inner_pitch_mm", "1B", "内径距 (inner_pitch)",
            "相邻两凸点之间最窄缝（卡尺插入两凸点之间）", DiagramView.TOP),
        FieldGuide("stud_diameter_mm", "③", "凸点直径 (stud_diameter)",
            "单个凸点直径（任选一个，卡尺卡外径）", DiagramView.TOP),
        FieldGuide("brick_height_net_mm", "②", "砖块净高 (brick_height_net)",
            "底面 → 砖顶，不含凸点", DiagramView.SIDE),
        FieldGuide("brick_height_total_mm", "④", "砖块总高 (brick_height_total)",
            "底面 → 凸点顶，含凸点", DiagramView.SIDE),
    )

    private val BY_KEY = CANONICAL.associateBy { it.key }

    /** All 5 canonical keys (the order/contract the backend always requires). */
    val CANONICAL_KEYS: List<String> = CANONICAL.map { it.key }

    fun codeFor(key: String): String = BY_KEY[key]?.code ?: key

    /**
     * Map the backend's `needs_measurement.fields` (list of keys) to ordered
     * wizard steps. Empty -> the canonical 5. Unknown keys -> a fallback step
     * (raw key as code + label) so the wizard never crashes on a new field.
     */
    fun stepsFor(backendKeys: List<String>): List<FieldGuide> {
        if (backendKeys.isEmpty()) return CANONICAL
        return backendKeys.map { key ->
            BY_KEY[key] ?: FieldGuide(
                key = key, code = key, label = "$key（请按字段名测量）",
                hint = "后端要求的测量项", view = DiagramView.TOP,
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.MeasurementCatalogTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/measure/MeasurementCatalog.kt \
        app/src/test/java/com/johnymoo/arverify/MeasurementCatalogTest.kt
git commit -m "feat: add MeasurementCatalog (systems/kinds/fields mirror schema.ts)"
```

---

## Task 3: `MeasurementCrossCheck` (two-tier validation)

Mirrors backend `_cross_check_raw` (tolerance 0.5mm) but **blocks** on `1A≤1B` / `④≤②` (backend only warns; these are physically impossible for real bricks).

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/measure/MeasurementCrossCheck.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/MeasurementCrossCheckTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
        assertTrue(r.canSubmit)                 // not blocked
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.MeasurementCrossCheckTest"`
Expected: FAIL — `MeasurementCrossCheck` does not exist.

- [ ] **Step 3: Write the implementation**

Create `MeasurementCrossCheck.kt`:

```kotlin
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

        fun valid(k: String): Double? = values[k]?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }

        val outer = valid(OUTER); val inner = valid(INNER)
        val net = valid(NET); val total = valid(TOTAL); val stud = valid(STUD)

        // 2) cross rules (only when the involved values are individually valid)
        if (outer != null && inner != null && outer <= inner)
            errors.add("1A 应大于 1B（外径距 > 内径距）")
        if (net != null && total != null && total <= net)
            errors.add("④ 应大于 ②（总高 > 净高）")

        // 3) stud-diameter cross-check (warning only)
        if (outer != null && inner != null && stud != null) {
            val derived = (outer - inner) / 2.0
            if (abs(derived - stud) > TOL_MM)
                warnings.add("③ 与 (1A−1B)/2 相差 ${"%.2f".format(abs(derived - stud))}mm（>0.5），请核对 1B")
        }
        return CrossCheckResult(errors, warnings)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.MeasurementCrossCheckTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/measure/MeasurementCrossCheck.kt \
        app/src/test/java/com/johnymoo/arverify/MeasurementCrossCheckTest.kt
git commit -m "feat: add MeasurementCrossCheck (two-tier caliper validation)"
```

---

## Task 4: `ParametricSubmission` (multipart build + 201 parse)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/net/ParametricSubmission.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/ParametricSubmissionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.johnymoo.arverify

import com.google.gson.JsonParser
import com.johnymoo.arverify.net.HttpResponse
import com.johnymoo.arverify.net.HttpTransport
import com.johnymoo.arverify.net.ParametricBlockRequest
import com.johnymoo.arverify.net.ParametricOutcome
import com.johnymoo.arverify.net.ParametricSubmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricSubmissionTest {

    private class FakeTransport(val code: Int, val body: String) : HttpTransport {
        var url: String? = null; var contentType: String? = null; var sent: String? = null
        override fun post(url: String, contentType: String, body: ByteArray): HttpResponse {
            this.url = url; this.contentType = contentType; this.sent = String(body, Charsets.UTF_8)
            return HttpResponse(code, this.body)
        }
    }

    private fun req() = ParametricBlockRequest(
        system = "feile", kind = "brick", unitsX = 2, unitsY = 4,
        rawMeasurementsMm = linkedMapOf(
            "outer_pitch_mm" to 31.8, "inner_pitch_mm" to 16.0, "stud_diameter_mm" to 7.9,
            "brick_height_net_mm" to 9.6, "brick_height_total_mm" to 11.4,
        ),
    )

    @Test fun rawMeasurementsJsonHasFiveKeysAndIsValid() {
        val json = ParametricSubmission.rawMeasurementsJson(req().rawMeasurementsMm)
        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals(5, obj.size())
        assertEquals(31.8, obj["outer_pitch_mm"].asDouble, 1e-9)
        assertTrue(obj.has("brick_height_total_mm"))
    }

    @Test fun postsToParametricBlocksEndpointWithAllFormFields() {
        val t = FakeTransport(201, """{"capture_id":"c","job_id":"j","status":"pending"}""")
        ParametricSubmission(t).submit("http://h:5173/api/v1", req())
        assertTrue(t.url!!.endsWith("/api/v1/parametric-blocks"))
        assertTrue(t.contentType!!.startsWith("multipart/form-data"))
        val body = t.sent!!
        listOf("name=\"system\"", "name=\"kind\"", "name=\"units_x\"",
            "name=\"units_y\"", "name=\"raw_measurements_mm\"").forEach {
            assertTrue("missing $it", body.contains(it))
        }
        assertTrue(body.contains("feile")); assertTrue(body.contains("\"outer_pitch_mm\""))
    }

    @Test fun sendsZeroPhotos() {
        val t = FakeTransport(201, """{"status":"pending"}""")
        ParametricSubmission(t).submit("http://h/api/v1", req())
        assertTrue(!t.sent!!.contains("name=\"photos\""))
    }

    @Test fun success201Parsed() {
        val t = FakeTransport(201, """
            {"capture_id":"cap1","job_id":"job1","part_id":"feile-brick-2x4",
             "status":"pending","cross_check_warnings":["w1"]}""".trimIndent())
        val out = ParametricSubmission(t).submit("http://h/api/v1", req())
        assertTrue(out is ParametricOutcome.Success)
        val s = out as ParametricOutcome.Success
        assertEquals("cap1", s.result.captureId)
        assertEquals("job1", s.result.jobId)
        assertEquals(listOf("w1"), s.result.crossCheckWarnings)
    }

    @Test fun http422IsFailureWithBody() {
        val t = FakeTransport(422, """{"detail":"raw_measurements_mm.inner_pitch_mm must be > 0"}""")
        val out = ParametricSubmission(t).submit("http://h/api/v1", req())
        assertTrue(out is ParametricOutcome.Failure)
        assertTrue((out as ParametricOutcome.Failure).message.contains("inner_pitch_mm"))
        assertEquals(422, out.code)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ParametricSubmissionTest"`
Expected: FAIL — `ParametricSubmission` / `ParametricBlockRequest` / `ParametricOutcome` do not exist.

- [ ] **Step 3: Write the implementation**

Create `ParametricSubmission.kt`:

```kotlin
package com.johnymoo.arverify.net

import android.util.Log
import com.google.gson.JsonParser
import com.johnymoo.arverify.measure.MeasurementCatalog
import java.util.UUID

/** Input for POST /api/v1/parametric-blocks (rawMeasurementsMm keyed by the 5 canonical keys). */
data class ParametricBlockRequest(
    val system: String,
    val kind: String,
    val unitsX: Int,
    val unitsY: Int,
    val rawMeasurementsMm: Map<String, Double>,
)

/** Parsed 201 body (tolerant; fields the wizard's result screen needs). */
data class ParametricBlockResult(
    val captureId: String?,
    val jobId: String?,
    val partId: String?,
    val status: String?,
    val crossCheckWarnings: List<String>,
)

sealed class ParametricOutcome {
    data class Success(val result: ParametricBlockResult, val rawBody: String) : ParametricOutcome()
    data class Failure(val message: String, val code: Int?) : ParametricOutcome()
}

/**
 * Builds the parametric-block multipart and POSTs it. Mirrors
 * BrickStudio apps/web .../parametric/api.ts: system/kind/units_x/units_y +
 * raw_measurements_mm (JSON string); zero photos (optional, not used for
 * reconstruction); part_id omitted (backend defaults it).
 */
class ParametricSubmission(
    private val transport: HttpTransport,
    private val boundaryProvider: () -> String = { "param" + UUID.randomUUID().toString().replace("-", "") },
) {
    fun submit(baseUrl: String, req: ParametricBlockRequest): ParametricOutcome {
        val url = baseUrl.trimEnd('/') + ENDPOINT
        val mb = MultipartBuilder(boundaryProvider())
            .addFormField("system", req.system)
            .addFormField("kind", req.kind)
            .addFormField("units_x", req.unitsX.toString())
            .addFormField("units_y", req.unitsY.toString())
            .addFormField("raw_measurements_mm", rawMeasurementsJson(req.rawMeasurementsMm))
        val body = mb.build()
        return try {
            val resp = transport.post(url, mb.contentType(), body)
            when {
                resp.code in 200..299 ->
                    ParametricOutcome.Success(parse(resp.body), resp.body)
                else ->
                    ParametricOutcome.Failure("HTTP ${resp.code}: ${shortBody(resp.body)}", resp.code)
            }
        } catch (e: Exception) {
            Log.w(TAG, "parametric submit failed", e)
            ParametricOutcome.Failure(e.javaClass.simpleName, null)
        }
    }

    companion object {
        const val ENDPOINT = "/api/v1/parametric-blocks"
        private const val TAG = "ParametricSubmission"

        /** Serialize the 5 caliper values in canonical key order as a JSON object string. */
        fun rawMeasurementsJson(values: Map<String, Double>): String =
            MeasurementCatalog.CANONICAL_KEYS.joinToString(
                prefix = "{", postfix = "}", separator = ","
            ) { k -> "\"$k\":${values[k]}" }

        private fun parse(body: String): ParametricBlockResult {
            val root = try {
                JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            } catch (e: Exception) { null }
            fun s(k: String) = root?.get(k)?.takeIf { it.isJsonPrimitive }?.asString
            val warns = root?.get("cross_check_warnings")?.takeIf { it.isJsonArray }
                ?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString } ?: emptyList()
            return ParametricBlockResult(
                captureId = s("capture_id"), jobId = s("job_id"),
                partId = s("part_id"), status = s("status"), crossCheckWarnings = warns,
            )
        }

        private fun shortBody(b: String) =
            b.replace(Regex("\\s+"), " ").trim().take(200).ifBlank { "no response body" }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ParametricSubmissionTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/net/ParametricSubmission.kt \
        app/src/test/java/com/johnymoo/arverify/ParametricSubmissionTest.kt
git commit -m "feat: add ParametricSubmission (parametric-blocks multipart + parse)"
```

---

## Task 5: Shared UI base — tokens + `ScreenScaffold`

Compose; **no local test** — verified on device. This is the base Plan 2 reuses.

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/theme/Color.kt`
- Create: `app/src/main/java/com/johnymoo/arverify/ui/components/ScreenScaffold.kt`

- [ ] **Step 1: Add border/divider tokens** — append to `Color.kt`:

```kotlin
val SfBorder = Color(0xFF243042)
val SfDivider = Color(0xFF1B2433)
```

- [ ] **Step 2: Write `ScreenScaffold.kt`**

```kotlin
package com.johnymoo.arverify.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Shared screen frame: an inset-aware TopAppBar (title + optional back) over a
 * themed background. Scaffold consumes the status-bar inset so content never
 * collides with the system clock/notch (fixes the app-wide overlap + missing
 * back-button problems). Camera/immersive screens do NOT use this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { inner -> content(inner) }
}
```

- [ ] **Step 3: Device verification (no unit test)**

Build + install on device (toolchain machine): `./gradlew :app:installDebug`. Temporarily wrap any existing Compose screen in `ScreenScaffold("测试", onBack = {})` to confirm: (a) the title bar sits **below** the status bar, (b) the back arrow shows and pops. Revert the temporary wrap.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/theme/Color.kt \
        app/src/main/java/com/johnymoo/arverify/ui/components/ScreenScaffold.kt
git commit -m "feat: add ScreenScaffold + border/divider tokens (shared UI base)"
```

---

## Task 6: `MeasurementDiagram` (focused per-step caliper diagram)

Compose Canvas; **no local test** — verified on device. Draws the brick with only the current measurement highlighted (style A). Geometry is self-contained.

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/measure/MeasurementDiagram.kt`

- [ ] **Step 1: Write `MeasurementDiagram.kt`**

```kotlin
package com.johnymoo.arverify.measure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Focused caliper diagram (style A): draws a simple brick and highlights only
 * the measurement for [code]. TOP view for 1A/1B/③, SIDE view for ②/④.
 * Layout proxy: docs/superpowers/specs/assets/needs-measurement-ui-mockups/measurement-model.html
 */
@Composable
fun MeasurementDiagram(view: DiagramView, code: String, modifier: Modifier = Modifier) {
    val brick = Color(0xFFF1F3F6); val brickEdge = Color(0xFFCDD3DC)
    val faded = Color(0xFFEEF1F5); val accent = Color(0xFFE8590C)
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width; val h = size.height
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(accent, Offset(x1, y1), Offset(x2, y2), strokeWidth = 6f)

        if (view == DiagramView.TOP) {
            val left = w * 0.12f; val right = w * 0.88f; val top = h * 0.38f; val bot = h * 0.86f
            drawRect(brick, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(right - left, bot - top))
            drawRect(brickEdge, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(right - left, bot - top), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            val cols = 3; val r = (right - left) / (cols * 2.4f)
            val cyTop = top + (bot - top) * 0.32f; val cyBot = top + (bot - top) * 0.72f
            val xs = (0 until cols).map { left + (right - left) * (it + 0.5f) / cols }
            xs.forEach { cx ->
                drawCircle(faded, r, Offset(cx, cyTop)); drawCircle(brickEdge, r, Offset(cx, cyTop), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                drawCircle(faded, r, Offset(cx, cyBot)); drawCircle(brickEdge, r, Offset(cx, cyBot), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
            when (code) {
                "1A" -> { val y = top * 0.5f; line(xs.first() - r, y, xs.last() + r, y); line(xs.first() - r, y - 8, xs.first() - r, y + 8); line(xs.last() + r, y - 8, xs.last() + r, y + 8) }
                "1B" -> { line(xs[0] + r, cyTop, xs[1] - r, cyTop); line(xs[0] + r, cyTop - 8, xs[0] + r, cyTop + 8); line(xs[1] - r, cyTop - 8, xs[1] - r, cyTop + 8) }
                "③" -> { line(xs[1] - r, cyBot, xs[1] + r, cyBot); line(xs[1] - r, cyBot - 8, xs[1] - r, cyBot + 8); line(xs[1] + r, cyBot - 8, xs[1] + r, cyBot + 8) }
            }
        } else {
            val left = w * 0.30f; val right = w * 0.80f; val bodyTop = h * 0.42f; val base = h * 0.86f
            val studTop = h * 0.24f
            // studs
            val sxs = listOf(left + (right - left) * 0.28f, left + (right - left) * 0.72f)
            sxs.forEach { sx -> drawRect(faded, topLeft = Offset(sx - 14, studTop), size = androidx.compose.ui.geometry.Size(28f, bodyTop - studTop)) }
            drawRect(brick, topLeft = Offset(left, bodyTop), size = androidx.compose.ui.geometry.Size(right - left, base - bodyTop))
            drawRect(brickEdge, topLeft = Offset(left, bodyTop), size = androidx.compose.ui.geometry.Size(right - left, base - bodyTop), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            when (code) {
                "②" -> { val x = left * 0.6f; line(x, bodyTop, x, base); line(x - 8, bodyTop, x + 8, bodyTop); line(x - 8, base, x + 8, base) }
                "④" -> { val x = right + (w - right) * 0.5f; line(x, studTop, x, base); line(x - 8, studTop, x + 8, studTop); line(x - 8, base, x + 8, base) }
            }
        }
    }
}
```

- [ ] **Step 2: Device verification (no unit test)**

After Task 7 wires it in, confirm on device that each step shows the brick with the correct highlight (1A spans outer studs, 1B the inner gap, ③ one stud, ② body height, ④ total height). Adjust geometry constants on device if a line looks off — this is the only purpose of this checkpoint.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/measure/MeasurementDiagram.kt
git commit -m "feat: add MeasurementDiagram (focused per-step caliper diagram)"
```

---

## Task 7: `MeasurementWizardActivity` (2-phase wizard)

Compose; **no local test** — verified on device. Replaces `MeasurementFormActivity`. Reads `UploadResultHolder.outcome`/`baseUrl`, runs CONFIRM → MEASURE(×5) → RESULT, submits via `ParametricSubmission` off the main thread.

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/measure/MeasurementWizardActivity.kt`

- [ ] **Step 1: Write `MeasurementWizardActivity.kt`**

```kotlin
package com.johnymoo.arverify.measure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.johnymoo.arverify.net.*
import com.johnymoo.arverify.ui.components.ScreenScaffold
import com.johnymoo.arverify.ui.theme.*
import java.util.concurrent.Executors

class MeasurementWizardActivity : ComponentActivity() {
    private val io = Executors.newSingleThreadExecutor()
    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val outcome = UploadResultHolder.outcome as? UploadOutcome.Success
        val recognized = outcome?.result?.recognized
        val nm = outcome?.result?.needsMeasurement
        val baseUrl = UploadResultHolder.baseUrl
        val steps = MeasurementCatalog.stepsFor(nm?.fields?.mapNotNull { it.key } ?: emptyList())

        setContent {
            ScanForgeTheme {
                WizardRoot(
                    reason = nm?.reason,
                    recognizedKind = recognized?.kind,
                    recognizedUnits = recognized?.let { (it.unitsX ?: 0) to (it.unitsY ?: 0) },
                    recognizedConfidence = recognized?.confidence,
                    steps = steps,
                    onBack = { finish() },
                    onSubmit = { req, cb ->
                        io.execute {
                            val out = ParametricSubmission(HttpUrlConnectionTransport()).submit(baseUrl, req)
                            runOnUiThread { cb(out) }
                        }
                    },
                )
            }
        }
    }
}

private enum class Phase { CONFIRM, MEASURE, RESULT }

@Composable
private fun WizardRoot(
    reason: String?,
    recognizedKind: String?,
    recognizedUnits: Pair<Int, Int>?,
    recognizedConfidence: Double?,
    steps: List<FieldGuide>,
    onBack: () -> Unit,
    onSubmit: (ParametricBlockRequest, (ParametricOutcome) -> Unit) -> Unit,
) {
    var phase by remember { mutableStateOf(Phase.CONFIRM) }
    var system by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf(recognizedKind) }           // backend suggestion, editable
    var unitsX by remember { mutableStateOf("") }                     // blank; not the untrusted 22
    var unitsY by remember { mutableStateOf("") }
    var stepIndex by remember { mutableStateOf(0) }
    val values = remember { mutableStateMapOf<String, String>() }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ParametricBlockResult?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val title = when (phase) {
        Phase.CONFIRM -> "建模确认"; Phase.MEASURE -> "测量 ${stepIndex + 1}/${steps.size}"; Phase.RESULT -> "提交结果"
    }
    val back = when (phase) {
        Phase.CONFIRM -> onBack
        Phase.MEASURE -> ({ if (stepIndex == 0) phase = Phase.CONFIRM else stepIndex-- })
        Phase.RESULT -> onBack
    }

    ScreenScaffold(title = title, onBack = back) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (phase) {
                Phase.CONFIRM -> ConfirmPhase(
                    reason, recognizedKind, recognizedUnits, recognizedConfidence,
                    system, { system = it }, kind, { kind = it }, unitsX, { unitsX = it }, unitsY, { unitsY = it },
                    canStart = system != null && kind != null && unitsX.toUnit() != null && unitsY.toUnit() != null,
                    onStart = { phase = Phase.MEASURE; stepIndex = 0 },
                )
                Phase.MEASURE -> MeasurePhase(
                    step = steps[stepIndex], stepIndex = stepIndex, total = steps.size,
                    value = values[steps[stepIndex].key] ?: "",
                    onValue = { values[steps[stepIndex].key] = it },
                    crossCheck = MeasurementCrossCheck.check(values.numeric()),
                    onPrev = { if (stepIndex == 0) phase = Phase.CONFIRM else stepIndex-- },
                    onNext = {
                        if (stepIndex < steps.size - 1) stepIndex++
                        else {
                            submitting = true; submitError = null
                            onSubmit(
                                ParametricBlockRequest(system!!, kind!!, unitsX.toUnit()!!, unitsY.toUnit()!!, values.numeric5()),
                            ) { out ->
                                submitting = false
                                when (out) {
                                    is ParametricOutcome.Success -> { result = out.result; phase = Phase.RESULT }
                                    is ParametricOutcome.Failure -> submitError = out.message
                                }
                            }
                        }
                    },
                    submitting = submitting, submitError = submitError, isLast = stepIndex == steps.size - 1,
                )
                Phase.RESULT -> ResultPhase(result, onDone = onBack)
            }
        }
    }
}

private fun String.toUnit(): Int? =
    toIntOrNull()?.takeIf { it in MeasurementCatalog.MIN_UNITS..MeasurementCatalog.MAX_UNITS }
private fun Map<String, String>.numeric(): Map<String, Double?> = mapValues { it.value.toDoubleOrNull() }
private fun Map<String, String>.numeric5(): Map<String, Double> =
    MeasurementCatalog.CANONICAL_KEYS.associateWith { (this[it]?.toDoubleOrNull()) ?: 0.0 }

@Composable
private fun ConfirmPhase(
    reason: String?, recKind: String?, recUnits: Pair<Int, Int>?, recConf: Double?,
    system: String?, onSystem: (String) -> Unit, kind: String?, onKind: (String) -> Unit,
    unitsX: String, onX: (String) -> Unit, unitsY: String, onY: (String) -> Unit,
    canStart: Boolean, onStart: () -> Unit,
) {
    // banner
    Surface(color = SfWaitBg, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text("⚠️ 自动识别不可信", color = SfWait, style = MaterialTheme.typography.titleSmall)
            Text("本次未能可信识别，需人工测量生成参数化模型。", color = SfOnBg, style = MaterialTheme.typography.bodySmall)
            if (!reason.isNullOrBlank())
                Text("后端原因：$reason", color = SfMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
    // dimmed untrusted recognition
    if (recKind != null || recUnits != null) {
        Surface(color = SfSurface, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("本次识别：${recKind ?: "-"} · ${recUnits?.first ?: "-"}×${recUnits?.second ?: "-"}",
                    color = SfMuted, style = MaterialTheme.typography.bodySmall)
                Text("置信度 ${recConf ?: 0.0} · 不可信", color = SfError, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text("① 选择体系", style = MaterialTheme.typography.titleSmall)
    ChipRow(MeasurementCatalog.SYSTEMS.map { it.value to it.label }, system, onSystem)
    Text("② 选择类型" + if (kind == recKind && recKind != null) "（识别建议·请核对）" else "", style = MaterialTheme.typography.titleSmall)
    ChipRow(MeasurementCatalog.KINDS.map { it.value to (it.icon + " " + it.label) }, kind, onKind)
    Text("③ 凸点排数（${MeasurementCatalog.MIN_UNITS}–${MeasurementCatalog.MAX_UNITS}）", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UnitField(unitsX, onX); Text("×", color = SfMuted); UnitField(unitsY, onY)
    }
    Text("识别给的 ${recUnits?.first ?: "-"}×${recUnits?.second ?: "-"} 超出范围或不可信，已忽略，请按实物填写",
        color = SfError, style = MaterialTheme.typography.labelSmall)
    Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("开始测量（5 步）")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun UnitField(value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onValue(it.filter { c -> c.isDigit() }.take(2)) },
        modifier = Modifier.width(96.dp), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("排") },
    )
}

@Composable
private fun MeasurePhase(
    step: FieldGuide, stepIndex: Int, total: Int, value: String, onValue: (String) -> Unit,
    crossCheck: CrossCheckResult, onPrev: () -> Unit, onNext: () -> Unit,
    submitting: Boolean, submitError: String?, isLast: Boolean,
) {
    LinearProgressIndicator(progress = { (stepIndex + 1f) / total }, modifier = Modifier.fillMaxWidth())
    MeasurementDiagram(step.view, step.code, Modifier.padding(vertical = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = SfTeal, shape = MaterialTheme.shapes.small) {
            Text(step.code, color = SfTealOn, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Text(step.label, style = MaterialTheme.typography.titleSmall)
    }
    Text(step.hint, color = SfMuted, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = { Text("mm") }, placeholder = { Text("输入测量值") },
    )
    // current-field hard error (positivity) shown inline
    val fieldErr = crossCheck.errors.firstOrNull { it.contains(step.code) }
    if (value.isNotBlank() && fieldErr != null) Text(fieldErr, color = SfError, style = MaterialTheme.typography.labelSmall)
    crossCheck.warnings.forEach { Text("⚠ $it", color = SfWait, style = MaterialTheme.typography.labelSmall) }
    if (submitError != null) Text("提交失败：$submitError", color = SfError, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f)) { Text("上一步") }
        Button(
            onClick = onNext, modifier = Modifier.weight(1f),
            enabled = !submitting && (if (isLast) crossCheck.canSubmit else value.toDoubleOrNull()?.let { it > 0 } == true),
        ) { Text(if (submitting) "提交中…" else if (isLast) "提交" else "下一步") }
    }
}

@Composable
private fun ResultPhase(result: ParametricBlockResult?, onDone: () -> Unit) {
    Surface(color = SfOkBg, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text("✓ 已提交，后端生成中", color = SfOk, style = MaterialTheme.typography.titleSmall)
            Text("part_id：${result?.partId ?: "-"}", color = SfOnBg, style = MaterialTheme.typography.bodySmall)
            Text("job_id：${result?.jobId ?: "-"}", color = SfMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
    val warns = result?.crossCheckWarnings ?: emptyList()
    if (warns.isNotEmpty()) Surface(color = SfWaitBg, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text("后端互验提醒", color = SfWait, style = MaterialTheme.typography.titleSmall)
            warns.forEach { Text("⚠ $it", color = SfOnBg, style = MaterialTheme.typography.labelSmall) }
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
}
```

- [ ] **Step 2: Device verification (no unit test)**

Defer to Task 9's full device run (the activity needs the wiring in Task 8 to be launchable).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/measure/MeasurementWizardActivity.kt
git commit -m "feat: add MeasurementWizardActivity (2-phase parametric measurement wizard)"
```

---

## Task 8: Wire-up + remove old form

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`, `strings.xml`, `ScanCaptureActivity.kt`, `CaptureWizardActivity.kt`, `SessionDetailScreen.kt`
- Delete: `MeasurementFormActivity.kt`, `activity_measurement_form.xml`, `MeasurementValidator.kt`, `MeasurementValidatorTest.kt`

- [ ] **Step 1: Rename the manifest activity entry** — in `AndroidManifest.xml`, replace:

```xml
        <activity
            android:name=".measure.MeasurementFormActivity"
```
with:
```xml
        <activity
            android:name=".measure.MeasurementWizardActivity"
```
(keep the rest of that `<activity …>` block's attributes unchanged.)

- [ ] **Step 2: Update the 3 call sites** — replace `MeasurementFormActivity` with `MeasurementWizardActivity` in:
  - `app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt` (line ~113, the `NEEDS_MEASUREMENT` Intent)
  - `app/src/main/java/com/johnymoo/arverify/capture/CaptureWizardActivity.kt` (import at line ~28 + usage at line ~379)
  - `app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailScreen.kt` (line ~103, the `NEEDS_MEASUREMENT` Intent)

Example (ScanCaptureActivity):
```kotlin
                com.johnymoo.arverify.net.RecognitionStatus.NEEDS_MEASUREMENT ->
                    startActivity(android.content.Intent(this, com.johnymoo.arverify.measure.MeasurementWizardActivity::class.java))
```

- [ ] **Step 3: Add/clean strings** — in `app/src/main/res/values/strings.xml`, the old `measure_title`/`measure_submit` are no longer referenced (the wizard uses inline Chinese text). Leave them or remove; no new strings required. (If you prefer resource strings, that's optional polish, not required for this task.)

- [ ] **Step 4: Delete the superseded files**

```bash
git rm app/src/main/java/com/johnymoo/arverify/measure/MeasurementFormActivity.kt \
       app/src/main/res/layout/activity_measurement_form.xml \
       app/src/main/java/com/johnymoo/arverify/measure/MeasurementValidator.kt \
       app/src/test/java/com/johnymoo/arverify/MeasurementValidatorTest.kt
```

- [ ] **Step 5: Verify the whole module compiles + all unit tests pass** (toolchain machine)

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all tests, with no reference to the deleted `MeasurementValidator`/`MeasurementFormActivity`. If a stray reference remains, the compile fails — fix the reference.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: route needs_measurement to MeasurementWizardActivity; remove old form"
```

---

## Task 9: End-to-end device verification (issue #3 acceptance)

No code; this is the acceptance gate. Run on Mac + device against the live backend.

- [ ] **Step 1: Build + install** — `./gradlew :app:installDebug` (toolchain machine, device attached).

- [ ] **Step 2: Reproduce `needs_measurement`** — using the fixture `part-e6ac1916_1781407101219` (on device at `/sdcard/Android/data/com.johnymoo.arverify/files/captures/…`; backend at the Settings server address). Capture/re-upload → backend returns `needs_measurement`.

- [ ] **Step 3: Verify the flow + acceptance criteria:**
  - App opens the **建模确认页** (banner + backend reason), NOT a raw field form.
  - Recognized `kind` is pre-selected with "识别建议·请核对"; `system` unselected; `units` blank; the bogus `22×14` is shown dimmed and cannot be entered (stepper clamps 1–16).
  - 5-step wizard: each step shows the focused diagram + 编号 + 中文名 + 提示; you can tell where each value is measured **without docs**.
  - Validation: enter `1A ≤ 1B` or `④ ≤ ②` → 下一步/提交 blocked (red); make `|(1A−1B)/2 − ③| > 0.5` → yellow warning, still submittable.
  - Submit → `POST /api/v1/parametric-blocks` returns 201 → result screen shows `part_id`/`job_id` + any `cross_check_warnings`.
  - Force a 422 (e.g. tamper a value) → the backend's specific field error is shown.
  - Back arrow works on every phase (step → previous step → confirm → exit).

- [ ] **Step 4: Confirm full unit suite green** — `./gradlew :app:testDebugUnitTest` → PASS.

- [ ] **Step 5: Commit any device-tuning fixes** (e.g. diagram geometry tweaks)

```bash
git add -A && git commit -m "fix: device-tuning for measurement wizard (issue #3)"
```

---

## Self-Review (completed)

- **Spec coverage:** confirm page (Task 7 ConfirmPhase) ✓; 5-step wizard + focused diagram (Tasks 6–7) ✓; field text/order mapping (Task 2) ✓; two-tier validation (Task 3) ✓; parametric multipart contract + zero photos (Task 4) ✓; `reason` parse (Task 1) ✓; auto-classify via backend kind pre-select (Task 7) ✓; units clamp/blank (Task 7) ✓; shared ScreenScaffold + back + insets base (Task 5) ✓; routing rename (Task 8) ✓; fixture e2e (Task 9) ✓. **Deferred to Plan 2 (by design):** per-screen redesign of Home/Library/SessionDetail/FrameViewer/Settings/Diagnostics/Capture/Result; best-effort GLB preview button on the result screen (optional; not an issue-#3 acceptance item).
- **Placeholder scan:** no TBD/TODO; every code step has complete code; UI tasks have explicit device-verification checkpoints in lieu of impossible local tests.
- **Type consistency:** `FieldGuide(key,code,label,hint,view)`, `DiagramView{TOP,SIDE}`, `MeasurementCatalog.{SYSTEMS,KINDS,CANONICAL_KEYS,stepsFor,codeFor,MIN_UNITS,MAX_UNITS}`, `CrossCheckResult{errors,warnings,canSubmit}`, `MeasurementCrossCheck.check`, `ParametricBlockRequest{system,kind,unitsX,unitsY,rawMeasurementsMm}`, `ParametricOutcome.{Success(result,rawBody),Failure(message,code)}`, `ParametricBlockResult{captureId,jobId,partId,status,crossCheckWarnings}`, `ParametricSubmission(transport).submit/rawMeasurementsJson` — used consistently across Tasks 2–8.
