# A1 A2 Capture Calibration Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make FEILE-focused AR capture trustworthy enough for the next backend recognition loop by separating ordinary part capture from explicit reference calibration, surfacing honest depth/scale diagnostics, supporting continuous capture and local补拍, and letting the backend choose `recognized` or `needs_measurement`.

**Architecture:** Keep backend contracts stable and put the new behavior in small Android-side policy objects. Ordinary recognition capture uses ARCore depth or hit-test distance at low confidence; visual reference scale is only allowed when the user explicitly enables reference mode and the detected reference passes plausibility checks. Diagnostics and UI copy report the actual source and next action instead of overclaiming success. The backend supports repeated POSTs but not appending files to an existing `capture_id`, so补拍 works by appending frames locally and reposting the complete package with the same `part_id`.

**Tech Stack:** Kotlin, Android Compose, ARCore, JUnit4 unit tests, existing `CaptureConfig`/`AppPrefs`, existing capture metadata JSON.

---

## Evidence And Scope

The 2026-06-14 sample review pulled captures and diagnostics from device `PLG110`. It found six usable sample sessions, two empty sessions, and eight diagnostics. The important failures were:

- Colored FEILE parts were detected as the 33.84 mm visual reference, including red long parts and blue/green parts, producing `VISUAL_REFERENCE / HIGH` scale values that were visually wrong.
- Diagnostics often had ARCore depth around 1.802 m with `NO_IN_RANGE_DEPTH` and `NO_TARGET`, while visual scale still reported `HIGH`.
- Some sessions were created with no captured files, and some uploads were pending with missing or weak view coverage.

This plan deliberately does not add a developer-facing parameter console. The app remains an assisted brick-modeling app: capture the part, judge whether the capture is trustworthy, upload when ready, or guide the user into manual measurement.

Backend capability confirmed on x570 `BrickStudio`:

- `POST /api/v1/ar-captures` always creates a new `capture_id`.
- `part_id` is indexed but not unique, so a reposted local补拍 package may reuse the same `part_id`.
- There is no `PATCH`/`PUT` append endpoint for existing AR captures, so the Android app must not depend on server-side append.

## File Structure

- Modify `app/src/main/java/com/johnymoo/arverify/config/CaptureConfig.kt`
  - Add user-facing scale policy flags with conservative defaults.
- Modify `app/src/main/java/com/johnymoo/arverify/config/AppPrefs.kt`
  - Persist the new reference-mode setting.
- Modify `app/src/main/java/com/johnymoo/arverify/capture/VisualScaleEstimator.kt`
  - Add explicit scale policy and plausibility checks.
- Modify `app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt`
  - Carry whether visual reference mode is enabled and whether manual measurement is recommended.
- Modify `app/src/main/java/com/johnymoo/arverify/capture/CaptureGuidanceText.kt`
  - Add honest status copy for low confidence and disabled reference mode.
- Modify `app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt`
  - Pass the policy into scale estimation for capture and diagnostics.
- Modify `app/src/main/java/com/johnymoo/arverify/capture/DepthDiagnostics.kt`
  - Persist reference mode and manual-measurement recommendation in diagnostic JSON.
- Modify `app/src/main/java/com/johnymoo/arverify/metadata/ArMetadata.kt`
  - Add optional, backward-compatible scale quality fields.
- Modify `app/src/main/java/com/johnymoo/arverify/metadata/ArMetadataSerializer.kt`
  - Serialize the new optional metadata fields deterministically.
- Modify `app/src/main/java/com/johnymoo/arverify/net/CaptureUploadAssembler.kt`
  - Report missing images and missing recognition frame before upload.
- Modify `app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt`
  - Use readiness messages before upload, support resume capture from an existing local session, and let backend decide `recognized` or `needs_measurement`.
- Modify `app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt`
  - Support appending frames to an existing local session directory.
- Modify `app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailScreen.kt`
  - Add a补拍 entry point for local sessions.
- Modify `app/src/main/java/com/johnymoo/arverify/result/ResultActivity.kt`
  - Add a next-part capture entry point after recognized results.
- Modify `app/src/main/java/com/johnymoo/arverify/ui/settings/SettingsScreen.kt`
  - Add a restrained "reference calibration" switch with non-technical wording.
- Modify `app/src/main/java/com/johnymoo/arverify/ui/library/LibraryFiltering.kt`
  - Hide empty sessions from the library list.
- Tests:
  - Modify `app/src/test/java/com/johnymoo/arverify/VisualScaleEstimatorTest.kt`
  - Modify `app/src/test/java/com/johnymoo/arverify/CaptureGuidanceTextTest.kt`
  - Modify `app/src/test/java/com/johnymoo/arverify/DepthDiagnosticsTest.kt`
  - Modify `app/src/test/java/com/johnymoo/arverify/ArMetadataSerializerTest.kt`
  - Modify `app/src/test/java/com/johnymoo/arverify/CaptureUploadAssemblerTest.kt`
  - Modify `app/src/test/java/com/johnymoo/arverify/CaptureSessionWriterTest.kt`
  - Modify `app/src/test/java/com/johnymoo/arverify/LibraryFilteringTest.kt`

## Task 1: Explicit Visual Reference Scale Policy

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/config/CaptureConfig.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/config/AppPrefs.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/VisualScaleEstimator.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt`
- Modify: `app/src/test/java/com/johnymoo/arverify/VisualScaleEstimatorTest.kt`

- [ ] **Step 1: Write failing tests for disabled reference mode and plausible reference use**

Add these tests to `VisualScaleEstimatorTest`:

```kotlin
@Test fun visualReferenceIsIgnoredUnlessReferenceModeIsEnabled() {
    val image = redRectImage(width = 120, height = 80, rectWidth = 65)

    val estimate = VisualScaleEstimator.estimate(
        image = image,
        intrinsics = intrinsics,
        arcoreDistanceM = 0.673,
        arcoreDistanceSource = TargetDistanceSource.DEPTH,
        policy = ScaleEstimationPolicy(referenceModeEnabled = false),
    )

    assertEquals(0.673, estimate.distanceM, 0.0)
    assertEquals(0.673, estimate.arcoreDistanceM, 0.0)
    assertNull(estimate.visualDistanceM)
    assertNull(estimate.visualReferenceWidthPx)
    assertEquals(DistanceSourceForScale.ARCORE_DISTANCE, estimate.distanceSourceForScale)
    assertEquals(DistanceConfidence.LOW, estimate.distanceConfidence)
    assertEquals("REFERENCE_MODE_DISABLED", estimate.visualReferenceStatus)
    assertTrue(estimate.manualMeasurementRecommended)
}

@Test fun enabledReferenceModeRejectsImplausiblyCloseVisualScale() {
    val image = redRectImage(width = 320, height = 220, rectWidth = 212)

    val estimate = VisualScaleEstimator.estimate(
        image = image,
        intrinsics = intrinsics,
        arcoreDistanceM = 0.0,
        arcoreDistanceSource = TargetDistanceSource.NONE,
        policy = ScaleEstimationPolicy(referenceModeEnabled = true),
    )

    assertEquals(0.0, estimate.distanceM, 0.0)
    assertEquals("VISUAL_DISTANCE_OUT_OF_RANGE", estimate.visualReferenceStatus)
    assertEquals(DistanceSourceForScale.ARCORE_DISTANCE, estimate.distanceSourceForScale)
    assertEquals(DistanceConfidence.LOW, estimate.distanceConfidence)
    assertTrue(estimate.manualMeasurementRecommended)
}

@Test fun enabledReferenceModeUsesPlausibleVisualReferenceWhenDepthAgrees() {
    val image = redRectImage(width = 120, height = 80, rectWidth = 65)

    val estimate = VisualScaleEstimator.estimate(
        image = image,
        intrinsics = intrinsics,
        arcoreDistanceM = 0.24,
        arcoreDistanceSource = TargetDistanceSource.DEPTH,
        policy = ScaleEstimationPolicy(referenceModeEnabled = true),
    )

    assertEquals(0.22584816, estimate.distanceM, 1e-9)
    assertEquals(0.22584816, estimate.visualDistanceM!!, 1e-9)
    assertEquals(DistanceSourceForScale.VISUAL_REFERENCE, estimate.distanceSourceForScale)
    assertEquals(DistanceConfidence.HIGH, estimate.distanceConfidence)
    assertEquals("DETECTED", estimate.visualReferenceStatus)
    assertFalse(estimate.manualMeasurementRecommended)
}

@Test fun enabledReferenceModeRejectsVisualScaleWhenDepthStronglyDisagrees() {
    val image = redRectImage(width = 120, height = 80, rectWidth = 65)

    val estimate = VisualScaleEstimator.estimate(
        image = image,
        intrinsics = intrinsics,
        arcoreDistanceM = 0.67,
        arcoreDistanceSource = TargetDistanceSource.DEPTH,
        policy = ScaleEstimationPolicy(referenceModeEnabled = true),
    )

    assertEquals(0.67, estimate.distanceM, 0.0)
    assertEquals("VISUAL_DEPTH_DISAGREE", estimate.visualReferenceStatus)
    assertEquals(DistanceSourceForScale.ARCORE_DISTANCE, estimate.distanceSourceForScale)
    assertEquals(DistanceConfidence.LOW, estimate.distanceConfidence)
    assertTrue(estimate.manualMeasurementRecommended)
}
```

Add imports:

```kotlin
import com.johnymoo.arverify.capture.ScaleEstimationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
```

Update the existing `visualReferenceBecomesHighConfidenceScaleDistance` test to pass `policy = ScaleEstimationPolicy(referenceModeEnabled = true, maxDepthDisagreementM = 1.0)` so it continues to document the legacy happy path under explicit reference mode.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.VisualScaleEstimatorTest
```

Expected: FAIL because `ScaleEstimationPolicy` and `manualMeasurementRecommended` do not exist and the estimator has no `policy` parameter.

- [ ] **Step 3: Implement minimal scale policy**

In `CaptureConfig.kt`, add:

```kotlin
val visualReferenceModeEnabled: Boolean = false,
val visualReferenceMinDistanceM: Double = 0.12,
val visualReferenceMaxDistanceM: Double = 0.80,
val visualReferenceMaxDepthDisagreementM: Double = 0.20,
```

In `VisualScaleEstimator.kt`, add:

```kotlin
data class ScaleEstimationPolicy(
    val referenceModeEnabled: Boolean = false,
    val visualMinDistanceM: Double = 0.12,
    val visualMaxDistanceM: Double = 0.80,
    val maxDepthDisagreementM: Double = 0.20,
)
```

Extend `ScaleDistanceEstimate`:

```kotlin
val referenceModeEnabled: Boolean = false,
val manualMeasurementRecommended: Boolean = true,
```

Change `estimate` signature:

```kotlin
fun estimate(
    image: RgbImage?,
    intrinsics: CameraIntrinsics,
    arcoreDistanceM: Double,
    arcoreDistanceSource: TargetDistanceSource,
    policy: ScaleEstimationPolicy = ScaleEstimationPolicy(),
): ScaleDistanceEstimate
```

Before RGB detection, return fallback when reference mode is disabled:

```kotlin
if (!policy.referenceModeEnabled) {
    return fallback(arcoreDistanceM, arcoreDistanceSource, "REFERENCE_MODE_DISABLED", policy.referenceModeEnabled)
}
```

After calculating `visualDistance`, reject implausible values:

```kotlin
if (visualDistance < policy.visualMinDistanceM || visualDistance > policy.visualMaxDistanceM) {
    return fallback(arcoreDistanceM, arcoreDistanceSource, "VISUAL_DISTANCE_OUT_OF_RANGE", policy.referenceModeEnabled)
}
if (
    arcoreDistanceM > 0.0 &&
    arcoreDistanceSource != TargetDistanceSource.NONE &&
    kotlin.math.abs(arcoreDistanceM - visualDistance) > policy.maxDepthDisagreementM
) {
    return fallback(arcoreDistanceM, arcoreDistanceSource, "VISUAL_DEPTH_DISAGREE", policy.referenceModeEnabled)
}
```

Set `manualMeasurementRecommended = false` only in the `HIGH` visual-reference success branch.

Update `fallback` to accept `referenceModeEnabled` and set `manualMeasurementRecommended = true`.

- [ ] **Step 4: Wire policy from config**

In `CaptureRenderer`, create:

```kotlin
private val scalePolicy = ScaleEstimationPolicy(
    referenceModeEnabled = config.visualReferenceModeEnabled,
    visualMinDistanceM = config.visualReferenceMinDistanceM,
    visualMaxDistanceM = config.visualReferenceMaxDistanceM,
    maxDepthDisagreementM = config.visualReferenceMaxDepthDisagreementM,
)
```

Pass `policy = scalePolicy` into both `VisualScaleEstimator.estimate` calls.

When copying scale values into `holder.state`, include:

```kotlin
referenceModeEnabled = scaleDistance.referenceModeEnabled,
manualMeasurementRecommended = scaleDistance.manualMeasurementRecommended,
```

- [ ] **Step 5: Persist the reference mode switch**

In `AppPrefs.kt`, load and persist `visualReferenceModeEnabled`:

```kotlin
visualReferenceModeEnabled = sp.getBoolean(KEY_VISUAL_REFERENCE_MODE, d.visualReferenceModeEnabled),
```

Add:

```kotlin
fun setVisualReferenceModeEnabled(enabled: Boolean) =
    sp.edit().putBoolean(KEY_VISUAL_REFERENCE_MODE, enabled).apply()

private const val KEY_VISUAL_REFERENCE_MODE = "visual_reference_mode"
```

- [ ] **Step 6: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.VisualScaleEstimatorTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/config/CaptureConfig.kt \
  app/src/main/java/com/johnymoo/arverify/config/AppPrefs.kt \
  app/src/main/java/com/johnymoo/arverify/capture/VisualScaleEstimator.kt \
  app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt \
  app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt \
  app/src/test/java/com/johnymoo/arverify/VisualScaleEstimatorTest.kt
git commit -m "fix: require explicit reference mode for visual scale"
```

## Task 2: Honest Diagnostics And Metadata

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/DepthDiagnostics.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/metadata/ArMetadata.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/metadata/ArMetadataSerializer.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt`
- Modify: `app/src/test/java/com/johnymoo/arverify/DepthDiagnosticsTest.kt`
- Modify: `app/src/test/java/com/johnymoo/arverify/ArMetadataSerializerTest.kt`
- Modify: `app/src/test/java/com/johnymoo/arverify/CaptureSessionWriterTest.kt`

- [ ] **Step 1: Write failing diagnostics test**

In `DepthDiagnosticsTest`, update `serializesUnavailableDepthReasonForBugReports` construction to include:

```kotlin
referenceModeEnabled = false,
manualMeasurementRecommended = true,
```

Then assert:

```kotlin
assertFalse(obj["reference_mode_enabled"].asBoolean)
assertTrue(obj["manual_measurement_recommended"].asBoolean)
```

Add another test:

```kotlin
@Test fun serializesDisabledReferenceModeForDepthBugReports() {
    val report = DepthFrameDiagnostics(
        createdAtEpochMs = 123_457L,
        deviceModel = "PLG110",
        arcoreVersion = "1.54",
        trackingState = "TRACKING",
        qualityReason = QualityReason.NO_TARGET.name,
        targetLocked = false,
        distanceM = 1.802,
        distanceSource = "DEPTH",
        fallbackDistanceM = null,
        scaleDistanceM = 1.802,
        arcoreDistanceM = 1.802,
        arcoreDistanceSource = "DEPTH",
        visualDistanceM = null,
        visualReferenceWidthPx = null,
        visualReferenceWidthMm = 33.84,
        visualReferenceStatus = "REFERENCE_MODE_DISABLED",
        distanceSourceForScale = "ARCORE_DISTANCE",
        distanceConfidence = "LOW",
        referenceModeEnabled = false,
        manualMeasurementRecommended = true,
        sharpness = 1354.0,
        depthAvailable = true,
        depthUnavailableReason = null,
        depthWidth = 160,
        depthHeight = 90,
        depthRangeMinMm = 150,
        depthRangeMaxMm = 700,
        roiFraction = 0.56,
        lockFailureReason = "NO_IN_RANGE_DEPTH",
        depthSummary = null,
    )

    val obj = JsonParser.parseString(DepthDiagnostics.toJson(report)).asJsonObject

    assertEquals("REFERENCE_MODE_DISABLED", obj["visual_reference_status"].asString)
    assertEquals("ARCORE_DISTANCE", obj["distance_source_for_scale"].asString)
    assertEquals("LOW", obj["distance_confidence"].asString)
    assertFalse(obj["reference_mode_enabled"].asBoolean)
    assertTrue(obj["manual_measurement_recommended"].asBoolean)
}
```

- [ ] **Step 2: Write failing metadata test**

In `ArMetadataSerializerTest`, update expected JSON for `matchesContractGoldenExactly` by inserting:

```json
"reference_mode_enabled":true,"manual_measurement_recommended":false
```

after `"distance_confidence":"HIGH"`.

Update `goldenMeta` to set:

```kotlin
referenceModeEnabled = true,
manualMeasurementRecommended = false,
```

Update fallback metadata test to set:

```kotlin
referenceModeEnabled = false,
manualMeasurementRecommended = true,
```

and assert those keys exist.

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.DepthDiagnosticsTest --tests com.johnymoo.arverify.ArMetadataSerializerTest
```

Expected: FAIL because diagnostics and metadata fields do not exist.

- [ ] **Step 4: Implement diagnostics fields**

In `DepthFrameDiagnostics`, add:

```kotlin
@SerializedName("reference_mode_enabled") val referenceModeEnabled: Boolean = false,
@SerializedName("manual_measurement_recommended") val manualMeasurementRecommended: Boolean = true,
```

In `CaptureRenderer.saveDiagnostic`, set from `diagnosticScaleDistance`:

```kotlin
referenceModeEnabled = diagnosticScaleDistance.referenceModeEnabled,
manualMeasurementRecommended = diagnosticScaleDistance.manualMeasurementRecommended,
```

- [ ] **Step 5: Implement metadata fields**

In `RecognitionFrameMeta`, add:

```kotlin
val referenceModeEnabled: Boolean = false,
val manualMeasurementRecommended: Boolean = true,
```

In `ArMetadataSerializer.frame`, append after `distance_confidence`:

```kotlin
append("\"distance_confidence\":\"").append(esc(f.distanceConfidence)).append("\",")
append("\"reference_mode_enabled\":").append(f.referenceModeEnabled).append(',')
append("\"manual_measurement_recommended\":").append(f.manualMeasurementRecommended)
```

In `CaptureSessionWriter.writeRecognition`, pass:

```kotlin
referenceModeEnabled = scaleDistance.referenceModeEnabled,
manualMeasurementRecommended = scaleDistance.manualMeasurementRecommended,
```

- [ ] **Step 6: Update writer test**

In `CaptureSessionWriterTest`, update the `ScaleDistanceEstimate` fixture:

```kotlin
referenceModeEnabled = true,
manualMeasurementRecommended = false,
```

Assert:

```kotlin
assertEquals(true, frame["reference_mode_enabled"].asBoolean)
assertEquals(false, frame["manual_measurement_recommended"].asBoolean)
```

- [ ] **Step 7: Run tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.DepthDiagnosticsTest --tests com.johnymoo.arverify.ArMetadataSerializerTest --tests com.johnymoo.arverify.CaptureSessionWriterTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/DepthDiagnostics.kt \
  app/src/main/java/com/johnymoo/arverify/metadata/ArMetadata.kt \
  app/src/main/java/com/johnymoo/arverify/metadata/ArMetadataSerializer.kt \
  app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt \
  app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt \
  app/src/test/java/com/johnymoo/arverify/DepthDiagnosticsTest.kt \
  app/src/test/java/com/johnymoo/arverify/ArMetadataSerializerTest.kt \
  app/src/test/java/com/johnymoo/arverify/CaptureSessionWriterTest.kt
git commit -m "feat: record scale confidence in diagnostics"
```

## Task 3: Non-Technical Capture UI And Settings

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureGuidanceText.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureScreen.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/com/johnymoo/arverify/CaptureGuidanceTextTest.kt`

- [ ] **Step 1: Write failing guidance tests**

Add to `CaptureGuidanceTextTest`:

```kotlin
@Test fun disabledReferenceModeExplainsScaleIsEstimated() {
    val state = CaptureUiState(
        qualityReason = QualityReason.OK,
        distanceM = 0.42,
        distanceSource = TargetDistanceSource.HIT_TEST,
        scaleDistanceM = 0.42,
        visualReferenceStatus = "REFERENCE_MODE_DISABLED",
        distanceSourceForScale = DistanceSourceForScale.ARCORE_DISTANCE,
        distanceConfidence = DistanceConfidence.LOW,
        referenceModeEnabled = false,
        manualMeasurementRecommended = true,
        targetLocked = true,
        sharpness = 210.0,
    )

    val line = CaptureGuidanceText.statusLine(state)

    assertTrue(line.contains("尺度估计"))
    assertTrue(line.contains("建议测量确认"))
    assertFalse(line.contains("高可信"))
}

@Test fun enabledReferenceModeShowsReferenceCalibrationHint() {
    val state = CaptureUiState(
        qualityReason = QualityReason.OK,
        distanceM = 0.24,
        distanceSource = TargetDistanceSource.DEPTH,
        scaleDistanceM = 0.22584816,
        visualReferenceStatus = "DETECTED",
        distanceSourceForScale = DistanceSourceForScale.VISUAL_REFERENCE,
        distanceConfidence = DistanceConfidence.HIGH,
        referenceModeEnabled = true,
        manualMeasurementRecommended = false,
        targetLocked = true,
        sharpness = 210.0,
    )

    val line = CaptureGuidanceText.statusLine(state)

    assertTrue(line.contains("参考尺"))
    assertTrue(line.contains("高可信"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.CaptureGuidanceTextTest
```

Expected: FAIL because UI state fields and copy do not exist.

- [ ] **Step 3: Implement UI state and guidance copy**

In `CaptureUiState`, add:

```kotlin
val referenceModeEnabled: Boolean = false,
val manualMeasurementRecommended: Boolean = true,
```

In `CaptureGuidanceText.statusLine`, keep distance formatting but change the middle label:

```kotlin
val scaleLabel = when (state.distanceSourceForScale) {
    DistanceSourceForScale.VISUAL_REFERENCE -> "参考尺"
    DistanceSourceForScale.ARCORE_DISTANCE -> "尺度估计"
}
val action = if (state.manualMeasurementRecommended) "建议测量确认" else confidence
```

Return:

```kotlin
"$target · $scaleLabel $scaleDistance · 门控 $gateLabel $gateDistance · $action · 清晰度 %.0f"
```

- [ ] **Step 4: Add settings switch**

In `SettingsScreen`, load and save:

```kotlin
var referenceMode by remember { mutableStateOf(initial.visualReferenceModeEnabled) }
```

Add a `SettingRow`:

```kotlin
SettingRow(
    label = "参考尺模式",
    supporting = "只在画面里放了标准参考砖时开启",
    trailing = { Switch(checked = referenceMode, onCheckedChange = { referenceMode = it }) },
)
```

If `SettingRow` has no `supporting` parameter, add only the label:

```kotlin
label = "参考尺模式：放了标准参考砖时开启"
```

In save:

```kotlin
prefs.setVisualReferenceModeEnabled(referenceMode)
```

- [ ] **Step 5: Add capture screen chip**

In `CaptureScreen`, under the depth and diagnostic chips, show a passive status chip:

```kotlin
Surface(color = Color(0x88000000), shape = MaterialTheme.shapes.large) {
    Text(
        if (state.referenceModeEnabled) "参考尺 ● 开" else "参考尺 ○ 关",
        color = Color.White.copy(alpha = 0.9f),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
```

- [ ] **Step 6: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.CaptureGuidanceTextTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt \
  app/src/main/java/com/johnymoo/arverify/capture/CaptureGuidanceText.kt \
  app/src/main/java/com/johnymoo/arverify/capture/CaptureScreen.kt \
  app/src/main/java/com/johnymoo/arverify/ui/settings/SettingsScreen.kt \
  app/src/test/java/com/johnymoo/arverify/CaptureGuidanceTextTest.kt
git commit -m "feat: show honest scale guidance"
```

## Task 4: Upload Readiness And Empty Session Cleanup

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/net/CaptureUploadAssembler.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/library/LibraryFiltering.kt`
- Modify: `app/src/test/java/com/johnymoo/arverify/CaptureUploadAssemblerTest.kt`
- Modify: `app/src/test/java/com/johnymoo/arverify/LibraryFilteringTest.kt`

- [ ] **Step 1: Write failing upload readiness tests**

Add to `CaptureUploadAssemblerTest`:

```kotlin
@Test fun readinessReportsMissingRecognitionFiles() {
    val dir = tmp.newFolder("empty_1")

    val readiness = CaptureUploadAssembler.readiness(dir)

    assertEquals(false, readiness.ready)
    assertEquals("缺少识别帧，请先拍俯视凸点面", readiness.message)
}

@Test fun readinessAllowsLowConfidenceScaleForBackendDecision() {
    val dir = tmp.newFolder("part-low_123")
    seed(dir)
    File(dir, "frame_2.jpg").writeBytes(byteArrayOf(7))
    File(dir, "frame_3.jpg").writeBytes(byteArrayOf(8))
    File(dir, "ar_metadata.json").writeText(
        """{"recognition_frame":{"distance_confidence":"LOW","manual_measurement_recommended":true}}"""
    )

    val readiness = CaptureUploadAssembler.readiness(dir)

    assertEquals(true, readiness.ready)
    assertEquals(null, readiness.message)
}

@Test fun readinessPassesWhenPackageHasEnoughImagesAndScaleConfidence() {
    val dir = tmp.newFolder("part-ready_123")
    seed(dir)
    File(dir, "frame_2.jpg").writeBytes(byteArrayOf(7))
    File(dir, "frame_3.jpg").writeBytes(byteArrayOf(8))
    File(dir, "ar_metadata.json").writeText(
        """{"recognition_frame":{"distance_confidence":"HIGH","manual_measurement_recommended":false}}"""
    )

    val readiness = CaptureUploadAssembler.readiness(dir)

    assertEquals(true, readiness.ready)
    assertEquals(null, readiness.message)
}
```

- [ ] **Step 2: Write failing empty-session filter test**

Add to `LibraryFilteringTest`:

```kotlin
@Test fun allHidesEmptySessions() {
    val empty = SessionEntry(
        dir = File("/tmp/empty"),
        session = CaptureSession(
            partId = "empty",
            mode = CaptureMode.RECOGNITION,
            createdAtEpochMs = 1L,
            deviceModel = "PLG110",
            status = SessionStatus.PENDING_UPLOAD,
            frames = emptyList(),
        ),
    )

    val visible = LibraryFiltering.apply(listOf(empty) + all, LibraryFilter.ALL)

    assertEquals(listOf("a", "b", "c", "d"), visible.map { it.session.partId })
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.CaptureUploadAssemblerTest --tests com.johnymoo.arverify.LibraryFilteringTest
```

Expected: FAIL because readiness does not exist and empty sessions are shown.

- [ ] **Step 4: Implement upload readiness**

In `CaptureUploadAssembler.kt`, add:

```kotlin
data class UploadReadiness(val ready: Boolean, val message: String? = null)

fun readiness(dir: File): UploadReadiness {
    val pkg = fromDir(dir, dir.name.substringBeforeLast('_'), "brick", null)
        ?: return UploadReadiness(false, "缺少识别帧，请先拍俯视凸点面")
    val missing = missingImageCount(pkg)
    if (missing > 0) return UploadReadiness(false, "还需补拍 ${missing} 张角度图后再上传")
    return UploadReadiness(true)
}
```

- [ ] **Step 5: Use readiness in capture upload**

In `ScanCaptureActivity.uploadRecognition`, before building package:

```kotlin
val readiness = com.johnymoo.arverify.net.CaptureUploadAssembler.readiness(dir)
if (!readiness.ready) {
    toast(readiness.message ?: "请先补齐采集")
    return
}
```

Then keep the existing `fromDir` call for the actual upload. Do not block upload because `distance_confidence` is `LOW`; the backend contract already returns either `recognized` or `needs_measurement`.

- [ ] **Step 6: Hide empty sessions**

In `LibraryFiltering.apply`, prefilter:

```kotlin
val nonEmpty = entries.filter { it.session.frames.isNotEmpty() }
return when (filter) {
    LibraryFilter.ALL -> nonEmpty
    LibraryFilter.RECOGNIZED -> nonEmpty.filter { it.session.status == SessionStatus.RECOGNIZED }
    LibraryFilter.PENDING -> nonEmpty.filter {
        it.session.status == SessionStatus.PENDING_UPLOAD ||
            it.session.status == SessionStatus.NEEDS_MEASUREMENT
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.CaptureUploadAssemblerTest --tests com.johnymoo.arverify.LibraryFilteringTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/net/CaptureUploadAssembler.kt \
  app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt \
  app/src/main/java/com/johnymoo/arverify/ui/library/LibraryFiltering.kt \
  app/src/test/java/com/johnymoo/arverify/CaptureUploadAssemblerTest.kt \
  app/src/test/java/com/johnymoo/arverify/LibraryFilteringTest.kt
git commit -m "feat: gate upload on capture readiness"
```

## Task 5: Continue Next Part And Resume Local Capture

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailScreen.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/result/ResultActivity.kt`
- Modify: `app/src/test/java/com/johnymoo/arverify/CaptureSessionWriterTest.kt`

- [ ] **Step 1: Write failing append-writer test**

Add to `CaptureSessionWriterTest`:

```kotlin
@Test fun appendingToExistingSessionContinuesFrameNumberingAndManifest() {
    val root = tmp.newFolder("captures")
    val original = CaptureSessionWriter(
        rootDir = root,
        partId = "part-resume",
        mode = CaptureMode.RECOGNITION,
        createdAtEpochMs = 1234L,
        deviceModel = "PLG110",
        depthRange = DepthRangeMm(150, 700),
    )
    original.addFrame(CaptureSlot.TOP, byteArrayOf(1), null)
    original.addFrame(CaptureSlot.SIDE, byteArrayOf(2), null)

    val resumed = CaptureSessionWriter.resume(
        sessionDir = original.dir,
        depthRange = DepthRangeMm(150, 700),
    )
    val frames = resumed.addFrame(CaptureSlot.ANGLE, byteArrayOf(3), null)

    assertEquals(original.dir, resumed.dir)
    assertTrue(original.dir.resolve("frame_2.jpg").isFile)
    assertEquals(listOf("frame_0.jpg", "frame_1.jpg", "frame_2.jpg"), frames.map { it.rgbFileName })

    val parsed = com.johnymoo.arverify.session.SessionManifestCodec
        .fromJson(original.dir.resolve(com.johnymoo.arverify.session.CaptureLibraryRepository.MANIFEST).readText())!!
    assertEquals("part-resume", parsed.partId)
    assertEquals(3, parsed.frames.size)
}
```

Add imports if absent:

```kotlin
import com.johnymoo.arverify.capture.CaptureSlot
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.CaptureSessionWriterTest
```

Expected: FAIL because `CaptureSessionWriter.resume` does not exist.

- [ ] **Step 3: Implement resumable writer**

In `CaptureSessionWriter`, add a private constructor parameter:

```kotlin
existingDir: File? = null,
initialFrames: List<CapturedFrame> = emptyList(),
```

Change `dir` and `frames` initialization:

```kotlin
val dir: File = existingDir ?: File(rootDir, "${partId}_$createdAtEpochMs").apply { mkdirs() }
private val frames = initialFrames.toMutableList()
```

In `companion object`, add:

```kotlin
fun resume(
    sessionDir: File,
    depthRange: DepthRangeMm,
    repo: CaptureLibraryRepository = CaptureLibraryRepository(sessionDir.parentFile),
): CaptureSessionWriter {
    val manifestFile = File(sessionDir, CaptureLibraryRepository.MANIFEST)
    val session = SessionManifestCodec.fromJson(manifestFile.readText())
        ?: error("Cannot resume damaged capture session")
    return CaptureSessionWriter(
        rootDir = sessionDir.parentFile,
        partId = session.partId,
        mode = session.mode,
        createdAtEpochMs = session.createdAtEpochMs,
        deviceModel = session.deviceModel,
        depthRange = depthRange,
        repo = repo,
        existingDir = sessionDir,
        initialFrames = session.frames,
    )
}
```

Import `SessionManifestCodec`.

- [ ] **Step 4: Wire resume intent into capture activity**

In `ScanCaptureActivity`, add:

```kotlin
const val EXTRA_RESUME_DIR = "resume_dir"
```

In `onCreate`, read:

```kotlin
val resumeDir = intent.getStringExtra(EXTRA_RESUME_DIR)?.let { java.io.File(it) }
```

Build the writer as:

```kotlin
val writer = if (resumeDir != null) {
    CaptureSessionWriter.resume(
        sessionDir = resumeDir,
        depthRange = DepthOverlayRange.fromDistanceBand(config.minDistanceM, config.maxDistanceM),
    )
} else {
    CaptureSessionWriter(
        rootDir = SessionPaths.captureRoot(this),
        partId = "part-" + UUID.randomUUID().toString().take(8),
        mode = mode,
        createdAtEpochMs = System.currentTimeMillis(),
        deviceModel = android.os.Build.MODEL ?: "",
        depthRange = DepthOverlayRange.fromDistanceBand(config.minDistanceM, config.maxDistanceM),
    )
}
```

After creating the renderer, set initial state for resumed sessions:

```kotlin
if (resumeDir != null) {
    holder.state.value = holder.state.value.copy(
        frames = writer.snapshot(com.johnymoo.arverify.session.SessionStatus.PENDING_UPLOAD).frames,
        sessionDir = writer.dir.absolutePath,
    )
}
```

If preserving exact wizard state becomes too broad, keep the minimal implementation: resumed sessions show existing thumbnails and new captures append as angles in free mode. Do not create a new directory for补拍.

- [ ] **Step 5: Add补拍 button in session detail**

In `SessionDetailScreen`, add a button before `重新上传` for recognition sessions:

```kotlin
OutlinedButton(onClick = {
    context.startActivity(
        android.content.Intent(context, com.johnymoo.arverify.capture.ScanCaptureActivity::class.java)
            .putExtra(com.johnymoo.arverify.capture.ScanCaptureActivity.EXTRA_MODE, "RECOGNITION")
            .putExtra(com.johnymoo.arverify.capture.ScanCaptureActivity.EXTRA_RESUME_DIR, dir.absolutePath)
    )
}, modifier = Modifier.weight(1f)) {
    Text("补拍")
}
```

Keep the existing reupload button. If three buttons do not fit, make the row wrap into two rows or use compact labels: `补拍`, `上传`, `删除`.

- [ ] **Step 6: Add next-part button in result screen**

In `ResultActivity`, after the result summary card, add:

```kotlin
Button(
    onClick = {
        startActivity(
            android.content.Intent(this@ResultActivity, com.johnymoo.arverify.capture.ScanCaptureActivity::class.java)
                .putExtra(com.johnymoo.arverify.capture.ScanCaptureActivity.EXTRA_MODE, "RECOGNITION")
        )
        finish()
    },
    modifier = Modifier.fillMaxWidth(),
) {
    Text("继续拍下一块")
}
```

This does not require backend changes because each next part is a fresh `POST /api/v1/ar-captures`.

- [ ] **Step 7: Run focused test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.johnymoo.arverify.CaptureSessionWriterTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt \
  app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt \
  app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailScreen.kt \
  app/src/main/java/com/johnymoo/arverify/result/ResultActivity.kt \
  app/src/test/java/com/johnymoo/arverify/CaptureSessionWriterTest.kt
git commit -m "feat: resume capture and continue next part"
```

## Task 6: Full Verification

**Files:**
- No code changes expected unless verification finds a regression.

- [ ] **Step 1: Run all unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 3: Install on connected device**

Run:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: device `PLG110` is visible and install succeeds.

- [ ] **Step 4: Manual smoke checklist**

On device:

- Open capture screen with default settings and point at the red long FEILE part. Expected: scale status says `尺度估计` or `建议测量确认`; it does not report high-confidence reference scale.
- Open diagnostics and save one diagnostic. Expected: `stats.json` has `reference_mode_enabled:false`, `distance_confidence:"LOW"` unless ARCore has a true high-confidence path, and `manual_measurement_recommended:true` when scale is not trustworthy.
- Enable `参考尺模式` only when a known reference brick is in the image. Expected: high confidence appears only when visual distance is plausible and agrees with depth.
- Capture fewer than four frames and try upload. Expected: app tells the user exactly how many views remain.
- Open a pending session from the library, tap `补拍`, capture another angle, return to detail, and upload. Expected: local files remain in the same session directory and the upload sends the complete package with the same `part_id`.
- After a recognized result, tap `继续拍下一块`. Expected: a fresh recognition capture screen opens with a new local session.

- [ ] **Step 5: Final status**

Run:

```bash
git status --short
git log --oneline -5
```

Expected: clean working tree after commits; latest commits match Tasks 1-5.
