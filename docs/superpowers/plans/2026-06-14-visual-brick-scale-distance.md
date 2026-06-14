# Visual Brick Scale Distance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix GitHub issue #4 by using the known red 2x2 brick width (`33.84mm`) as the recognition scale distance, while keeping ARCore depth/HitTest distance only as capture gate and diagnostic data.

**Architecture:** Add a small pure-Kotlin visual scale pipeline: detect the red reference brick in RGB pixels, convert its detected pixel width to distance with the existing pinhole model, and produce a single scale-distance decision object. Persist both values everywhere they matter: `distance_m` remains the scale distance for backward compatibility, while new explicit fields record ARCore gate distance, visual reference distance, reference pixel width, source, confidence, and detection status.

**Tech Stack:** Kotlin, Android ARCore, existing `ScaleMath`, Gson/JUnit4 JVM tests, hand-rolled `ArMetadataSerializer`, device verification on PLG110 with `adb`.

---

## Issue #4 Context

The issue reports that PLG110 top-down capture can pass depth/HitTest gating while reporting physically impossible distances for scale. The observed red 2x2 brick width is `33.84mm`; with `fx ~= 433.81`, a `65px` wide brick implies `0.22584816m`, `80px` implies `0.18350163m`, and `100px` implies `0.146801304m`. Current ARCore values around `0.580m` to `0.673m` imply only `22px` to `25px`, contradicting the saved recognition image.

Implementation rule: ARCore distance remains useful for capture guidance and diagnostics, but recognition/model scale must prefer the visual reference distance when the red brick is detected.

## Toolchain Reality

Before running Gradle or device commands from Codex shells in this checkout, set the Android environment explicitly:

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export PATH=/opt/homebrew/Cellar/openjdk@17/17.0.19/bin:/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH
```

The task steps below use exact Gradle commands for each test class. For example:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ScaleMathTest"
```

Expected device target for final verification: `PLG110` visible in `adb devices -l`.

## File Structure

**Create:**
- `app/src/main/java/com/johnymoo/arverify/capture/VisualReferenceDetector.kt` — pure RGB red-reference detector, no Android types.
- `app/src/main/java/com/johnymoo/arverify/capture/VisualScaleEstimator.kt` — converts detector output + camera intrinsics + ARCore gate distance into one scale decision.
- `app/src/test/java/com/johnymoo/arverify/VisualReferenceDetectorTest.kt` — synthetic RGB tests for the red reference detector.
- `app/src/test/java/com/johnymoo/arverify/VisualScaleEstimatorTest.kt` — visual-vs-ARCore source selection tests.
- `app/src/test/java/com/johnymoo/arverify/CaptureSessionWriterTest.kt` — proves persisted `ar_metadata.json` contains the new scale fields.

**Modify:**
- `app/src/main/java/com/johnymoo/arverify/capture/ScaleMath.kt` — add known-reference distance helper.
- `app/src/test/java/com/johnymoo/arverify/ScaleMathTest.kt` — lock the issue #4 numeric examples.
- `app/src/main/java/com/johnymoo/arverify/metadata/ArMetadata.kt` — extend recognition metadata fields.
- `app/src/main/java/com/johnymoo/arverify/metadata/ArMetadataSerializer.kt` — serialize new fields deterministically.
- `app/src/test/java/com/johnymoo/arverify/ArMetadataSerializerTest.kt` — update golden JSON.
- `docs/contracts/ar-capture-contract.sample.json` — mirror the new Android request metadata shape.
- `app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt` — write visual scale metadata.
- `app/src/main/java/com/johnymoo/arverify/capture/DepthDiagnostics.kt` — add visual scale diagnostics fields.
- `app/src/test/java/com/johnymoo/arverify/DepthDiagnosticsTest.kt` — assert visual fields in `stats.json`.
- `app/src/test/java/com/johnymoo/arverify/DiagnosticSnapshotWriterTest.kt` — update constructor calls and assertions.
- `app/src/main/java/com/johnymoo/arverify/capture/ArFrameExtractor.kt` — add Android runtime bridge from JPEG bytes to pure `RgbImage`.
- `app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt` — expose scale distance state for UI/diagnostics.
- `app/src/main/java/com/johnymoo/arverify/capture/CaptureGuidanceText.kt` — distinguish gate distance from scale distance.
- `app/src/test/java/com/johnymoo/arverify/CaptureGuidanceTextTest.kt` — assert the distinction.
- `app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt` — active Compose capture path: estimate visual scale for TOP capture and diagnostics.
- `app/src/main/java/com/johnymoo/arverify/capture/CaptureWizardActivity.kt` — legacy declared path: write the same metadata shape if launched.

---

### Task 1: Add Known-Reference Distance Math

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/ScaleMath.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/ScaleMathTest.kt`

- [ ] **Step 1: Write the failing tests**

Append these tests to `ScaleMathTest`:

```kotlin
@Test fun distanceFromKnownReferenceWidthMatchesIssueFourExamples() {
    val referenceWidthM = 0.03384
    val fx = 433.81

    assertEquals(0.22584816, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 65.0), 1e-9)
    assertEquals(0.18350163, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 80.0), 1e-9)
    assertEquals(0.146801304, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 100.0), 1e-9)
    assertEquals(0.587205216, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 25.0), 1e-9)
    assertEquals(0.6672786545454547, ScaleMath.distanceFromKnownWidthMeters(referenceWidthM, fx, 22.0), 1e-9)
}

@Test fun distanceFromKnownReferenceWidthRejectsInvalidInputs() {
    assertEquals(0.0, ScaleMath.distanceFromKnownWidthMeters(0.03384, 433.81, 0.0), 1e-9)
    assertEquals(0.0, ScaleMath.distanceFromKnownWidthMeters(0.03384, 0.0, 65.0), 1e-9)
    assertEquals(0.0, ScaleMath.distanceFromKnownWidthMeters(0.0, 433.81, 65.0), 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ScaleMathTest"
```

Expected: FAIL with unresolved reference `distanceFromKnownWidthMeters`.

- [ ] **Step 3: Implement the math helper**

In `ScaleMath.kt`, add this function inside `object ScaleMath` after `pixelSpan`:

```kotlin
/** Distance (meters) implied by a known physical width projected to [detectedWidthPx]. */
fun distanceFromKnownWidthMeters(
    referenceWidthMeters: Double,
    focalLengthPx: Double,
    detectedWidthPx: Double,
): Double {
    if (referenceWidthMeters <= 0.0 || focalLengthPx <= 0.0 || detectedWidthPx <= 0.0) return 0.0
    return referenceWidthMeters * focalLengthPx / detectedWidthPx
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ScaleMathTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/ScaleMath.kt \
        app/src/test/java/com/johnymoo/arverify/ScaleMathTest.kt
git commit -m "feat: add known-width scale distance math"
```

---

### Task 2: Detect the Red 2x2 Reference Brick in Pure RGB Pixels

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/capture/VisualReferenceDetector.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/VisualReferenceDetectorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `VisualReferenceDetectorTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.capture.RedBrickReferenceDetector
import com.johnymoo.arverify.capture.RgbImage
import com.johnymoo.arverify.capture.VisualReferenceDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReferenceDetectorTest {
    @Test fun detectsLongSideAsReferenceWidthPx() {
        val image = imageWithRedRect(width = 120, height = 80, left = 20, top = 18, rectWidth = 65, rectHeight = 34)

        val result = RedBrickReferenceDetector.detect(image)

        assertTrue(result is VisualReferenceDetection.Detected)
        val detected = result as VisualReferenceDetection.Detected
        assertEquals(65.0, detected.referenceWidthPx, 0.0)
        assertEquals(33.84, detected.referenceWidthMm, 0.0)
        assertEquals(20, detected.bounds.left)
        assertEquals(84, detected.bounds.right)
    }

    @Test fun choosesLargestRedComponent() {
        val image = imageWithRedRect(width = 160, height = 100, left = 30, top = 20, rectWidth = 80, rectHeight = 40)
        val pixels = image.pixels.copyOf()
        paintRect(pixels, image.width, left = 2, top = 2, rectWidth = 10, rectHeight = 10, color = 0xD71920)

        val result = RedBrickReferenceDetector.detect(image.copy(pixels = pixels))

        assertTrue(result is VisualReferenceDetection.Detected)
        assertEquals(80.0, (result as VisualReferenceDetection.Detected).referenceWidthPx, 0.0)
    }

    @Test fun reportsFailureWhenNoRedReferenceExists() {
        val pixels = IntArray(90 * 60) { 0x303030 }

        val result = RedBrickReferenceDetector.detect(RgbImage(90, 60, pixels))

        assertEquals(VisualReferenceDetection.Failed("NO_RED_REFERENCE"), result)
    }

    private fun imageWithRedRect(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        rectWidth: Int,
        rectHeight: Int,
    ): RgbImage {
        val pixels = IntArray(width * height) { 0x282828 }
        paintRect(pixels, width, left, top, rectWidth, rectHeight, 0xD71920)
        return RgbImage(width, height, pixels)
    }

    private fun paintRect(
        pixels: IntArray,
        imageWidth: Int,
        left: Int,
        top: Int,
        rectWidth: Int,
        rectHeight: Int,
        color: Int,
    ) {
        for (y in top until top + rectHeight) {
            for (x in left until left + rectWidth) {
                pixels[y * imageWidth + x] = color
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.VisualReferenceDetectorTest"
```

Expected: FAIL with unresolved references for `RedBrickReferenceDetector`, `RgbImage`, and `VisualReferenceDetection`.

- [ ] **Step 3: Implement the detector**

Create `VisualReferenceDetector.kt`:

```kotlin
package com.johnymoo.arverify.capture

import kotlin.math.max

data class RgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(pixels.size >= width * height) { "pixels must contain width*height values" }
    }
}

data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val widthPx: Int get() = right - left + 1
    val heightPx: Int get() = bottom - top + 1
}

sealed class VisualReferenceDetection {
    data class Detected(
        val referenceWidthPx: Double,
        val referenceWidthMm: Double,
        val bounds: PixelBounds,
        val redPixelCount: Int,
    ) : VisualReferenceDetection()

    data class Failed(val reason: String) : VisualReferenceDetection()
}

object RedBrickReferenceDetector {
    const val REFERENCE_WIDTH_MM = 33.84

    fun detect(image: RgbImage, minPixels: Int = 40): VisualReferenceDetection {
        val total = image.width * image.height
        val visited = BooleanArray(total)
        var best: Component? = null

        for (i in 0 until total) {
            if (visited[i]) continue
            visited[i] = true
            if (!isReferenceRed(image.pixels[i])) continue

            val component = floodFillRedComponent(image, i, visited)
            if (best == null || component.redPixelCount > best.redPixelCount) {
                best = component
            }
        }

        val component = best ?: return VisualReferenceDetection.Failed("NO_RED_REFERENCE")
        if (component.redPixelCount < minPixels) {
            return VisualReferenceDetection.Failed("REFERENCE_TOO_SMALL")
        }
        if (component.longSidePx < 8.0) {
            return VisualReferenceDetection.Failed("REFERENCE_TOO_SMALL")
        }
        if (component.fillRatio < 0.35) {
            return VisualReferenceDetection.Failed("REFERENCE_FRAGMENTED")
        }

        return VisualReferenceDetection.Detected(
            referenceWidthPx = component.longSidePx,
            referenceWidthMm = REFERENCE_WIDTH_MM,
            bounds = component.bounds,
            redPixelCount = component.redPixelCount,
        )
    }

    private fun floodFillRedComponent(image: RgbImage, start: Int, visited: BooleanArray): Component {
        val queue = IntArray(image.width * image.height)
        var head = 0
        var tail = 0
        queue[tail++] = start

        var count = 0
        var minX = image.width
        var maxX = 0
        var minY = image.height
        var maxY = 0

        while (head < tail) {
            val idx = queue[head++]
            val x = idx % image.width
            val y = idx / image.width
            count += 1
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y

            fun enqueue(next: Int) {
                if (next < 0 || next >= image.width * image.height || visited[next]) return
                visited[next] = true
                if (isReferenceRed(image.pixels[next])) queue[tail++] = next
            }

            if (x > 0) enqueue(idx - 1)
            if (x < image.width - 1) enqueue(idx + 1)
            if (y > 0) enqueue(idx - image.width)
            if (y < image.height - 1) enqueue(idx + image.width)
        }

        val bounds = PixelBounds(left = minX, top = minY, right = maxX, bottom = maxY)
        return Component(redPixelCount = count, bounds = bounds)
    }

    private fun isReferenceRed(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val other = max(g, b)
        return r >= 110 && r - other >= 45 && r >= (g * 1.35) && r >= (b * 1.20)
    }

    private data class Component(val redPixelCount: Int, val bounds: PixelBounds) {
        val longSidePx: Double get() = max(bounds.widthPx, bounds.heightPx).toDouble()
        val fillRatio: Double get() = redPixelCount.toDouble() / (bounds.widthPx * bounds.heightPx).toDouble()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.VisualReferenceDetectorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/VisualReferenceDetector.kt \
        app/src/test/java/com/johnymoo/arverify/VisualReferenceDetectorTest.kt
git commit -m "feat: detect visual red brick reference"
```

---

### Task 3: Convert Visual Detection into a Scale-Distance Decision

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/capture/VisualScaleEstimator.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/VisualScaleEstimatorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `VisualScaleEstimatorTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.capture.DistanceConfidence
import com.johnymoo.arverify.capture.DistanceSourceForScale
import com.johnymoo.arverify.capture.RgbImage
import com.johnymoo.arverify.capture.TargetDistanceSource
import com.johnymoo.arverify.capture.VisualScaleEstimator
import com.johnymoo.arverify.metadata.CameraIntrinsics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisualScaleEstimatorTest {
    private val intrinsics = CameraIntrinsics(
        fx = 433.81,
        fy = 433.81,
        cx = 320.0,
        cy = 240.0,
        width = 640,
        height = 480,
    )

    @Test fun visualReferenceBecomesHighConfidenceScaleDistance() {
        val image = redRectImage(width = 120, height = 80, rectWidth = 65)

        val estimate = VisualScaleEstimator.estimate(
            image = image,
            intrinsics = intrinsics,
            arcoreDistanceM = 0.673,
            arcoreDistanceSource = TargetDistanceSource.DEPTH,
        )

        assertEquals(0.22584816, estimate.distanceM, 1e-9)
        assertEquals(0.22584816, estimate.visualDistanceM!!, 1e-9)
        assertEquals(65.0, estimate.visualReferenceWidthPx!!, 0.0)
        assertEquals(33.84, estimate.visualReferenceWidthMm, 0.0)
        assertEquals(0.673, estimate.arcoreDistanceM, 0.0)
        assertEquals(TargetDistanceSource.DEPTH, estimate.arcoreDistanceSource)
        assertEquals(DistanceSourceForScale.VISUAL_REFERENCE, estimate.distanceSourceForScale)
        assertEquals(DistanceConfidence.HIGH, estimate.distanceConfidence)
        assertEquals("DETECTED", estimate.visualReferenceStatus)
    }

    @Test fun fallsBackToArcoreDistanceWithLowConfidenceWhenReferenceMissing() {
        val darkImage = RgbImage(80, 60, IntArray(80 * 60) { 0x202020 })

        val estimate = VisualScaleEstimator.estimate(
            image = darkImage,
            intrinsics = intrinsics,
            arcoreDistanceM = 0.673,
            arcoreDistanceSource = TargetDistanceSource.HIT_TEST,
        )

        assertEquals(0.673, estimate.distanceM, 0.0)
        assertNull(estimate.visualDistanceM)
        assertNull(estimate.visualReferenceWidthPx)
        assertEquals(DistanceSourceForScale.ARCORE_DISTANCE, estimate.distanceSourceForScale)
        assertEquals(DistanceConfidence.LOW, estimate.distanceConfidence)
        assertEquals("NO_RED_REFERENCE", estimate.visualReferenceStatus)
    }

    @Test fun recordsRgbUnavailableAsLowConfidenceArcoreFallback() {
        val estimate = VisualScaleEstimator.estimate(
            image = null,
            intrinsics = intrinsics,
            arcoreDistanceM = 0.5807,
            arcoreDistanceSource = TargetDistanceSource.DEPTH,
        )

        assertEquals(0.5807, estimate.distanceM, 0.0)
        assertEquals("RGB_UNAVAILABLE", estimate.visualReferenceStatus)
        assertEquals(DistanceConfidence.LOW, estimate.distanceConfidence)
    }

    private fun redRectImage(width: Int, height: Int, rectWidth: Int): RgbImage {
        val pixels = IntArray(width * height) { 0x242424 }
        val left = 20
        val top = 18
        for (y in top until top + 34) {
            for (x in left until left + rectWidth) {
                pixels[y * width + x] = 0xD71920
            }
        }
        return RgbImage(width, height, pixels)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.VisualScaleEstimatorTest"
```

Expected: FAIL with unresolved references for `VisualScaleEstimator`, `DistanceSourceForScale`, and `DistanceConfidence`.

- [ ] **Step 3: Implement the estimator**

Create `VisualScaleEstimator.kt`:

```kotlin
package com.johnymoo.arverify.capture

import com.johnymoo.arverify.metadata.CameraIntrinsics

enum class DistanceSourceForScale {
    VISUAL_REFERENCE,
    ARCORE_DISTANCE,
}

enum class DistanceConfidence {
    HIGH,
    LOW,
}

data class ScaleDistanceEstimate(
    val distanceM: Double,
    val arcoreDistanceM: Double,
    val arcoreDistanceSource: TargetDistanceSource,
    val visualDistanceM: Double?,
    val visualReferenceWidthPx: Double?,
    val visualReferenceWidthMm: Double,
    val visualReferenceStatus: String,
    val distanceSourceForScale: DistanceSourceForScale,
    val distanceConfidence: DistanceConfidence,
)

object VisualScaleEstimator {
    const val VISUAL_REFERENCE_WIDTH_MM = RedBrickReferenceDetector.REFERENCE_WIDTH_MM

    fun estimate(
        image: RgbImage?,
        intrinsics: CameraIntrinsics,
        arcoreDistanceM: Double,
        arcoreDistanceSource: TargetDistanceSource,
    ): ScaleDistanceEstimate {
        if (image == null) {
            return fallback(arcoreDistanceM, arcoreDistanceSource, "RGB_UNAVAILABLE")
        }

        return when (val detected = RedBrickReferenceDetector.detect(image)) {
            is VisualReferenceDetection.Detected -> {
                val visualDistance = ScaleMath.distanceFromKnownWidthMeters(
                    referenceWidthMeters = detected.referenceWidthMm / 1000.0,
                    focalLengthPx = intrinsics.fx,
                    detectedWidthPx = detected.referenceWidthPx,
                )
                if (visualDistance > 0.0) {
                    ScaleDistanceEstimate(
                        distanceM = visualDistance,
                        arcoreDistanceM = arcoreDistanceM,
                        arcoreDistanceSource = arcoreDistanceSource,
                        visualDistanceM = visualDistance,
                        visualReferenceWidthPx = detected.referenceWidthPx,
                        visualReferenceWidthMm = detected.referenceWidthMm,
                        visualReferenceStatus = "DETECTED",
                        distanceSourceForScale = DistanceSourceForScale.VISUAL_REFERENCE,
                        distanceConfidence = DistanceConfidence.HIGH,
                    )
                } else {
                    fallback(arcoreDistanceM, arcoreDistanceSource, "INVALID_INTRINSICS")
                }
            }
            is VisualReferenceDetection.Failed ->
                fallback(arcoreDistanceM, arcoreDistanceSource, detected.reason)
        }
    }

    fun fallback(
        arcoreDistanceM: Double,
        arcoreDistanceSource: TargetDistanceSource,
        visualReferenceStatus: String,
    ): ScaleDistanceEstimate =
        ScaleDistanceEstimate(
            distanceM = arcoreDistanceM.takeIf { it > 0.0 } ?: 0.0,
            arcoreDistanceM = arcoreDistanceM.takeIf { it > 0.0 } ?: 0.0,
            arcoreDistanceSource = arcoreDistanceSource,
            visualDistanceM = null,
            visualReferenceWidthPx = null,
            visualReferenceWidthMm = VISUAL_REFERENCE_WIDTH_MM,
            visualReferenceStatus = visualReferenceStatus,
            distanceSourceForScale = DistanceSourceForScale.ARCORE_DISTANCE,
            distanceConfidence = DistanceConfidence.LOW,
        )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.VisualScaleEstimatorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/VisualScaleEstimator.kt \
        app/src/test/java/com/johnymoo/arverify/VisualScaleEstimatorTest.kt
git commit -m "feat: choose visual scale distance"
```

---

### Task 4: Extend `ar_metadata` with Explicit Scale Fields

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/metadata/ArMetadata.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/metadata/ArMetadataSerializer.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/ArMetadataSerializerTest.kt`

- [ ] **Step 1: Write the failing serializer expectations**

Update `goldenMeta` in `ArMetadataSerializerTest.kt` so its `RecognitionFrameMeta` includes the new fields:

```kotlin
recognitionFrame = RecognitionFrameMeta(
    imageIntrinsics = CameraIntrinsics(0.0, 0.0, 0.0, 0.0, 0, 0),
    depth = DepthDims(0, 0),
    cameraPose = CameraPose(t = listOf(0.0, 0.0, 0.0), q = listOf(0.0, 0.0, 0.0, 1.0)),
    distanceM = 0.22584816,
    arcoreDistanceM = 0.673,
    arcoreDistanceSource = "DEPTH",
    visualDistanceM = 0.22584816,
    visualReferenceWidthPx = 65.0,
    visualReferenceWidthMm = 33.84,
    visualReferenceStatus = "DETECTED",
    distanceSourceForScale = "VISUAL_REFERENCE",
    distanceConfidence = "HIGH",
),
```

Replace the `expected` value in `matchesContractGoldenExactly` with:

```kotlin
val expected = """{"device":{"model":"PLG110","arcore":"1.54.260890493"},""" +
    """"recognition_frame":{"image_intrinsics":{"fx":0,"fy":0,"cx":0,"cy":0,"width":0,"height":0},""" +
    """"depth":{"width":0,"height":0,"format":"DEPTH16_MM"},""" +
    """"camera_pose":{"t":[0,0,0],"q":[0,0,0,1]},""" +
    """"distance_m":0.22584816,"arcore_distance_m":0.673,"arcore_distance_source":"DEPTH",""" +
    """"visual_distance_m":0.22584816,"visual_reference_width_px":65,"visual_reference_width_mm":33.84,""" +
    """"visual_reference_status":"DETECTED","distance_source_for_scale":"VISUAL_REFERENCE","distance_confidence":"HIGH"},""" +
    """"coarse_hints":{"rough_units_x":2,"rough_units_y":4,"rough_pitch_mm":16.2}}"""
```

Replace `omitsCoarseHintsWhenNull` with:

```kotlin
@Test fun omitsCoarseHintsWhenNullButKeepsScaleFields() {
    val json = ArMetadataSerializer.toJson(goldenMeta(null))
    assertFalse(json.contains("coarse_hints"))
    assertTrue(json.contains("\"distance_m\":0.22584816"))
    assertTrue(json.contains("\"arcore_distance_m\":0.673"))
    assertTrue(json.contains("\"visual_reference_width_px\":65"))
    assertTrue(json.endsWith("\"distance_confidence\":\"HIGH\"}}"))
}
```

In `realFloatsAndStringEscaping`, update the `RecognitionFrameMeta` constructor:

```kotlin
recognitionFrame = RecognitionFrameMeta(
    imageIntrinsics = CameraIntrinsics(1466.5, 1466.5, 960.0, 540.0, 1920, 1080),
    depth = DepthDims(160, 90),
    cameraPose = CameraPose(listOf(0.01, -0.02, 0.3), listOf(0.0, 0.0, 0.0, 1.0)),
    distanceM = 0.18350163,
    arcoreDistanceM = 0.5807,
    arcoreDistanceSource = "DEPTH",
    visualDistanceM = 0.18350163,
    visualReferenceWidthPx = 80.0,
    visualReferenceWidthMm = 33.84,
    visualReferenceStatus = "DETECTED",
    distanceSourceForScale = "VISUAL_REFERENCE",
    distanceConfidence = "HIGH",
),
```

and update the assertions:

```kotlin
assertTrue(json.contains("\"fx\":1466.5"))
assertTrue(json.contains("\"width\":1920"))
assertTrue(json.contains("\"distance_m\":0.18350163"))
assertTrue(json.contains("\"arcore_distance_m\":0.5807"))
assertTrue(json.contains("\"visual_reference_width_px\":80"))
assertTrue(json.contains("\"model\":\"A\\\"B\""))
```

Add this null visual fallback test:

```kotlin
@Test fun serializesNullVisualFieldsForArcoreFallback() {
    val json = ArMetadataSerializer.toJson(
        ArMetadata(
            device = DeviceMeta(model = "PLG110", arcore = "1.54"),
            recognitionFrame = RecognitionFrameMeta(
                imageIntrinsics = CameraIntrinsics(433.81, 433.81, 320.0, 240.0, 640, 480),
                depth = DepthDims(160, 90),
                cameraPose = CameraPose(listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0, 1.0)),
                distanceM = 0.673,
                arcoreDistanceM = 0.673,
                arcoreDistanceSource = "HIT_TEST",
                visualDistanceM = null,
                visualReferenceWidthPx = null,
                visualReferenceWidthMm = 33.84,
                visualReferenceStatus = "NO_RED_REFERENCE",
                distanceSourceForScale = "ARCORE_DISTANCE",
                distanceConfidence = "LOW",
            ),
            coarseHints = null,
        )
    )

    assertTrue(json.contains("\"visual_distance_m\":null"))
    assertTrue(json.contains("\"visual_reference_width_px\":null"))
    assertTrue(json.contains("\"distance_source_for_scale\":\"ARCORE_DISTANCE\""))
    assertTrue(json.contains("\"distance_confidence\":\"LOW\""))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ArMetadataSerializerTest"
```

Expected: FAIL with constructor parameter errors or missing serialized fields.

- [ ] **Step 3: Extend metadata models**

In `ArMetadata.kt`, replace `RecognitionFrameMeta` with:

```kotlin
/** The recognition (top-down stud) frame's metadata block. */
data class RecognitionFrameMeta(
    val imageIntrinsics: CameraIntrinsics,
    val depth: DepthDims,
    val cameraPose: CameraPose,
    /** Backward-compatible scale distance: visual reference when available, ARCore fallback otherwise. */
    val distanceM: Double,
    /** Explicit ARCore gate distance from depth or HitTest. */
    val arcoreDistanceM: Double = distanceM,
    val arcoreDistanceSource: String = "UNKNOWN",
    val visualDistanceM: Double? = null,
    val visualReferenceWidthPx: Double? = null,
    val visualReferenceWidthMm: Double = 33.84,
    val visualReferenceStatus: String = "NOT_EVALUATED",
    val distanceSourceForScale: String = "ARCORE_DISTANCE",
    val distanceConfidence: String = "LOW",
)
```

- [ ] **Step 4: Serialize the new fields deterministically**

In `ArMetadataSerializer.kt`, replace the final `distance_m` write in `frame(f: RecognitionFrameMeta)`:

```kotlin
append("\"distance_m\":").append(num(f.distanceM))
```

with:

```kotlin
append("\"distance_m\":").append(num(f.distanceM)).append(',')
append("\"arcore_distance_m\":").append(num(f.arcoreDistanceM)).append(',')
append("\"arcore_distance_source\":\"").append(esc(f.arcoreDistanceSource)).append("\",")
append("\"visual_distance_m\":").append(numOrNull(f.visualDistanceM)).append(',')
append("\"visual_reference_width_px\":").append(numOrNull(f.visualReferenceWidthPx)).append(',')
append("\"visual_reference_width_mm\":").append(num(f.visualReferenceWidthMm)).append(',')
append("\"visual_reference_status\":\"").append(esc(f.visualReferenceStatus)).append("\",")
append("\"distance_source_for_scale\":\"").append(esc(f.distanceSourceForScale)).append("\",")
append("\"distance_confidence\":\"").append(esc(f.distanceConfidence)).append('"')
```

Add this helper below `num(d: Double)`:

```kotlin
private fun numOrNull(d: Double?): String = d?.let { num(it) } ?: "null"
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ArMetadataSerializerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/metadata/ArMetadata.kt \
        app/src/main/java/com/johnymoo/arverify/metadata/ArMetadataSerializer.kt \
        app/src/test/java/com/johnymoo/arverify/ArMetadataSerializerTest.kt
git commit -m "feat: serialize visual scale metadata"
```

---

### Task 5: Persist Visual Scale Metadata from the Session Writer

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/CaptureSessionWriterTest.kt`

- [ ] **Step 1: Write the failing persistence test**

Create `CaptureSessionWriterTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.google.gson.JsonParser
import com.johnymoo.arverify.capture.DistanceConfidence
import com.johnymoo.arverify.capture.DistanceSourceForScale
import com.johnymoo.arverify.capture.ScaleDistanceEstimate
import com.johnymoo.arverify.capture.TargetDistanceSource
import com.johnymoo.arverify.capture.CaptureSessionWriter
import com.johnymoo.arverify.depth.DepthRangeMm
import com.johnymoo.arverify.metadata.CameraIntrinsics
import com.johnymoo.arverify.metadata.CameraPose
import com.johnymoo.arverify.session.CaptureMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureSessionWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writeRecognitionPersistsVisualScaleMetadata() {
        val writer = CaptureSessionWriter(
            rootDir = tmp.newFolder("captures"),
            partId = "part-scale",
            mode = CaptureMode.RECOGNITION,
            createdAtEpochMs = 1234L,
            deviceModel = "PLG110",
            depthRange = DepthRangeMm(150, 700),
        )
        val rgb = byteArrayOf(9, 8, 7)
        val depth = intArrayOf(200, 210, 220, 230)
        val scale = ScaleDistanceEstimate(
            distanceM = 0.22584816,
            arcoreDistanceM = 0.673,
            arcoreDistanceSource = TargetDistanceSource.DEPTH,
            visualDistanceM = 0.22584816,
            visualReferenceWidthPx = 65.0,
            visualReferenceWidthMm = 33.84,
            visualReferenceStatus = "DETECTED",
            distanceSourceForScale = DistanceSourceForScale.VISUAL_REFERENCE,
            distanceConfidence = DistanceConfidence.HIGH,
        )

        writer.writeRecognition(
            intrinsics = CameraIntrinsics(433.81, 433.81, 320.0, 240.0, 640, 480),
            pose = CameraPose(listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0, 1.0)),
            scaleDistance = scale,
            depthGridMm = depth,
            depthW = 2,
            depthH = 2,
            rgbJpeg = rgb,
            arcoreVersion = "1.54",
        )

        assertArrayEquals(rgb, writer.dir.resolve("recognition_rgb.jpg").readBytes())
        assertTrue(writer.dir.resolve("recognition_depth.png").isFile)

        val frame = JsonParser.parseString(writer.dir.resolve("ar_metadata.json").readText())
            .asJsonObject["recognition_frame"].asJsonObject
        assertEquals(0.22584816, frame["distance_m"].asDouble, 1e-9)
        assertEquals(0.673, frame["arcore_distance_m"].asDouble, 0.0)
        assertEquals("DEPTH", frame["arcore_distance_source"].asString)
        assertEquals(65.0, frame["visual_reference_width_px"].asDouble, 0.0)
        assertEquals("VISUAL_REFERENCE", frame["distance_source_for_scale"].asString)
        assertEquals("HIGH", frame["distance_confidence"].asString)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureSessionWriterTest"
```

Expected: FAIL because `writeRecognition` has no `scaleDistance` parameter.

- [ ] **Step 3: Add a scale-aware writer overload**

In `CaptureSessionWriter.kt`, replace the existing legacy `writeRecognition` function with this wrapper so current callers continue to compile during this task:

```kotlin
fun writeRecognition(
    intrinsics: CameraIntrinsics, pose: CameraPose, distanceM: Double,
    depthGridMm: IntArray, depthW: Int, depthH: Int,
    rgbJpeg: ByteArray, arcoreVersion: String,
{
    writeRecognition(
        intrinsics = intrinsics,
        pose = pose,
        scaleDistance = VisualScaleEstimator.fallback(
            arcoreDistanceM = distanceM,
            arcoreDistanceSource = TargetDistanceSource.DEPTH,
            visualReferenceStatus = "NOT_EVALUATED",
        ),
        depthGridMm = depthGridMm,
        depthW = depthW,
        depthH = depthH,
        rgbJpeg = rgbJpeg,
        arcoreVersion = arcoreVersion,
    )
}
```

Then add this new overload below it:

```kotlin
fun writeRecognition(
    intrinsics: CameraIntrinsics,
    pose: CameraPose,
    scaleDistance: ScaleDistanceEstimate,
    depthGridMm: IntArray,
    depthW: Int,
    depthH: Int,
    rgbJpeg: ByteArray,
    arcoreVersion: String,
) {
    File(dir, "recognition_rgb.jpg").writeBytes(rgbJpeg)
    File(dir, "recognition_depth.png").writeBytes(Depth16PngWriter.encode(depthGridMm, depthW, depthH))
    val meta = ArMetadata(
        device = DeviceMeta(model = deviceModel, arcore = arcoreVersion),
        recognitionFrame = RecognitionFrameMeta(
            imageIntrinsics = intrinsics,
            depth = DepthDims(depthW, depthH),
            cameraPose = pose,
            distanceM = scaleDistance.distanceM,
            arcoreDistanceM = scaleDistance.arcoreDistanceM,
            arcoreDistanceSource = scaleDistance.arcoreDistanceSource.name,
            visualDistanceM = scaleDistance.visualDistanceM,
            visualReferenceWidthPx = scaleDistance.visualReferenceWidthPx,
            visualReferenceWidthMm = scaleDistance.visualReferenceWidthMm,
            visualReferenceStatus = scaleDistance.visualReferenceStatus,
            distanceSourceForScale = scaleDistance.distanceSourceForScale.name,
            distanceConfidence = scaleDistance.distanceConfidence.name,
        ),
        coarseHints = null,
    )
    File(dir, "ar_metadata.json").writeText(ArMetadataSerializer.toJson(meta))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureSessionWriterTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt \
        app/src/test/java/com/johnymoo/arverify/CaptureSessionWriterTest.kt
git commit -m "feat: persist visual scale capture metadata"
```

---

### Task 6: Add Visual Scale Fields to Diagnostics

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/DepthDiagnostics.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/DepthDiagnosticsTest.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/DiagnosticSnapshotWriterTest.kt`

- [ ] **Step 1: Write the failing diagnostics assertions**

In `DepthDiagnosticsTest.serializesUnavailableDepthReasonForBugReports`, add these constructor arguments to `DepthFrameDiagnostics`:

```kotlin
scaleDistanceM = 0.0,
arcoreDistanceM = 0.0,
arcoreDistanceSource = "NONE",
visualDistanceM = null,
visualReferenceWidthPx = null,
visualReferenceWidthMm = 33.84,
visualReferenceStatus = "RGB_UNAVAILABLE",
distanceSourceForScale = "ARCORE_DISTANCE",
distanceConfidence = "LOW",
```

Then add these assertions:

```kotlin
assertEquals(0.0, obj["scale_distance_m"].asDouble, 0.0)
assertEquals(0.0, obj["arcore_distance_m"].asDouble, 0.0)
assertEquals("NONE", obj["arcore_distance_source"].asString)
assertTrue(obj["visual_distance_m"].isJsonNull)
assertTrue(obj["visual_reference_width_px"].isJsonNull)
assertEquals(33.84, obj["visual_reference_width_mm"].asDouble, 0.0)
assertEquals("RGB_UNAVAILABLE", obj["visual_reference_status"].asString)
assertEquals("ARCORE_DISTANCE", obj["distance_source_for_scale"].asString)
assertEquals("LOW", obj["distance_confidence"].asString)
```

In `DiagnosticSnapshotWriterTest.writesCompleteDiagnosticSnapshot`, add these constructor arguments:

```kotlin
scaleDistanceM = 0.22584816,
arcoreDistanceM = 0.673,
arcoreDistanceSource = "DEPTH",
visualDistanceM = 0.22584816,
visualReferenceWidthPx = 65.0,
visualReferenceWidthMm = 33.84,
visualReferenceStatus = "DETECTED",
distanceSourceForScale = "VISUAL_REFERENCE",
distanceConfidence = "HIGH",
```

Then add these assertions:

```kotlin
assertEquals(0.22584816, json["scale_distance_m"].asDouble, 1e-9)
assertEquals(0.673, json["arcore_distance_m"].asDouble, 0.0)
assertEquals("DEPTH", json["arcore_distance_source"].asString)
assertEquals(65.0, json["visual_reference_width_px"].asDouble, 0.0)
assertEquals("VISUAL_REFERENCE", json["distance_source_for_scale"].asString)
assertEquals("HIGH", json["distance_confidence"].asString)
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.DepthDiagnosticsTest" --tests "com.johnymoo.arverify.DiagnosticSnapshotWriterTest"
```

Expected: FAIL because `DepthFrameDiagnostics` lacks the new constructor parameters.

- [ ] **Step 3: Extend `DepthFrameDiagnostics`**

In `DepthDiagnostics.kt`, add these fields immediately after `fallbackDistanceM`:

```kotlin
@SerializedName("scale_distance_m") val scaleDistanceM: Double = distanceM,
@SerializedName("arcore_distance_m") val arcoreDistanceM: Double = distanceM,
@SerializedName("arcore_distance_source") val arcoreDistanceSource: String = distanceSource,
@SerializedName("visual_distance_m") val visualDistanceM: Double? = null,
@SerializedName("visual_reference_width_px") val visualReferenceWidthPx: Double? = null,
@SerializedName("visual_reference_width_mm") val visualReferenceWidthMm: Double = 33.84,
@SerializedName("visual_reference_status") val visualReferenceStatus: String = "NOT_EVALUATED",
@SerializedName("distance_source_for_scale") val distanceSourceForScale: String = "ARCORE_DISTANCE",
@SerializedName("distance_confidence") val distanceConfidence: String = "LOW",
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.DepthDiagnosticsTest" --tests "com.johnymoo.arverify.DiagnosticSnapshotWriterTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/DepthDiagnostics.kt \
        app/src/test/java/com/johnymoo/arverify/DepthDiagnosticsTest.kt \
        app/src/test/java/com/johnymoo/arverify/DiagnosticSnapshotWriterTest.kt
git commit -m "feat: record visual scale diagnostics"
```

---

### Task 7: Surface Gate-vs-Scale Distance in Capture State and UI Text

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureGuidanceText.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/CaptureGuidanceTextTest.kt`

- [ ] **Step 1: Write the failing UI-text test**

Append this test to `CaptureGuidanceTextTest.kt`:

```kotlin
@Test fun statusLineSeparatesScaleDistanceFromGateDistance() {
    val state = CaptureUiState(
        qualityReason = QualityReason.OK,
        distanceM = 0.67,
        distanceSource = TargetDistanceSource.DEPTH,
        scaleDistanceM = 0.22584816,
        distanceSourceForScale = DistanceSourceForScale.VISUAL_REFERENCE,
        distanceConfidence = DistanceConfidence.HIGH,
        targetLocked = true,
        sharpness = 210.0,
    )

    val line = CaptureGuidanceText.statusLine(state)

    assertTrue(line.contains("尺度 0.23m"))
    assertTrue(line.contains("门控 深度 0.67m"))
    assertTrue(line.contains("高可信"))
}
```

Add these imports:

```kotlin
import com.johnymoo.arverify.capture.DistanceConfidence
import com.johnymoo.arverify.capture.DistanceSourceForScale
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureGuidanceTextTest"
```

Expected: FAIL with unresolved `scaleDistanceM`, `distanceSourceForScale`, and `distanceConfidence`.

- [ ] **Step 3: Extend `CaptureUiState`**

In `CaptureScreenState.kt`, add these properties after `fallbackDistanceM`:

```kotlin
val scaleDistanceM: Double = 0.0,
val visualDistanceM: Double? = null,
val visualReferenceWidthPx: Double? = null,
val visualReferenceWidthMm: Double = VisualScaleEstimator.VISUAL_REFERENCE_WIDTH_MM,
val visualReferenceStatus: String = "NOT_EVALUATED",
val distanceSourceForScale: DistanceSourceForScale = DistanceSourceForScale.ARCORE_DISTANCE,
val distanceConfidence: DistanceConfidence = DistanceConfidence.LOW,
```

- [ ] **Step 4: Update status-line formatting**

In `CaptureGuidanceText.kt`, replace `statusLine` with:

```kotlin
fun statusLine(state: CaptureUiState): String {
    val gateDistance = when {
        state.distanceM > 0 -> "%.2fm".format(state.distanceM)
        else -> "--"
    }
    val scaleDistance = when {
        state.scaleDistanceM > 0 -> "%.2fm".format(state.scaleDistanceM)
        else -> "--"
    }
    val gateLabel = when (state.distanceSource) {
        TargetDistanceSource.HIT_TEST -> "平面"
        TargetDistanceSource.DEPTH -> "深度"
        TargetDistanceSource.NONE -> "无"
    }
    val confidence = when (state.distanceConfidence) {
        DistanceConfidence.HIGH -> "高可信"
        DistanceConfidence.LOW -> "低可信"
    }
    val target = if (state.targetLocked) "目标锁定" else "未锁定"
    return "$target · 尺度 $scaleDistance · 门控 $gateLabel $gateDistance · $confidence · 清晰度 %.0f"
        .format(state.sharpness)
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureGuidanceTextTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt \
        app/src/main/java/com/johnymoo/arverify/capture/CaptureGuidanceText.kt \
        app/src/test/java/com/johnymoo/arverify/CaptureGuidanceTextTest.kt
git commit -m "feat: show capture scale distance separately"
```

---

### Task 8: Wire the Active Compose Capture Flow and Diagnostics

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/ArFrameExtractor.kt`
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt`

- [ ] **Step 1: Add the JPEG-to-RGB runtime bridge**

In `ArFrameExtractor.kt`, add this import:

```kotlin
import android.graphics.BitmapFactory
```

Then add this function after `rgbJpeg`:

```kotlin
/** JPEG bytes -> pure RGB image for visual reference detection. Android runtime bridge only. */
fun rgbPixelsFromJpeg(jpeg: ByteArray): RgbImage? {
    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return RgbImage(bitmap.width, bitmap.height, pixels)
}
```

- [ ] **Step 2: Update TOP capture metadata wiring**

In `CaptureRenderer.capture`, replace this block:

```kotlin
val intr = ArFrameExtractor.intrinsics(frame)
val pose = ArFrameExtractor.pose(frame)
writer.writeRecognition(intr, pose, dist ?: 0.0, gridVals!!, gw, gh, rgb, arcoreVersion)
holder.state.value = holder.state.value.copy(topReady = true)
```

with:

```kotlin
val intr = ArFrameExtractor.intrinsics(frame)
val pose = ArFrameExtractor.pose(frame)
val scaleDistance = VisualScaleEstimator.estimate(
    image = ArFrameExtractor.rgbPixelsFromJpeg(rgb),
    intrinsics = intr,
    arcoreDistanceM = dist ?: 0.0,
    arcoreDistanceSource = resolvedTarget.source,
)
writer.writeRecognition(intr, pose, scaleDistance, gridVals!!, gw, gh, rgb, arcoreVersion)
holder.state.value = holder.state.value.copy(
    topReady = true,
    scaleDistanceM = scaleDistance.distanceM,
    visualDistanceM = scaleDistance.visualDistanceM,
    visualReferenceWidthPx = scaleDistance.visualReferenceWidthPx,
    visualReferenceWidthMm = scaleDistance.visualReferenceWidthMm,
    visualReferenceStatus = scaleDistance.visualReferenceStatus,
    distanceSourceForScale = scaleDistance.distanceSourceForScale,
    distanceConfidence = scaleDistance.distanceConfidence,
)
```

- [ ] **Step 3: Add scale estimation to diagnostics**

In `CaptureRenderer.saveDiagnostic`, after `val state = holder.state.value`, add:

```kotlin
val diagnosticScaleDistance = VisualScaleEstimator.estimate(
    image = rgb?.let { ArFrameExtractor.rgbPixelsFromJpeg(it) },
    intrinsics = ArFrameExtractor.intrinsics(frame),
    arcoreDistanceM = state.distanceM,
    arcoreDistanceSource = state.distanceSource,
)
```

Then add these arguments to the `DepthFrameDiagnostics` constructor:

```kotlin
scaleDistanceM = diagnosticScaleDistance.distanceM,
arcoreDistanceM = diagnosticScaleDistance.arcoreDistanceM,
arcoreDistanceSource = diagnosticScaleDistance.arcoreDistanceSource.name,
visualDistanceM = diagnosticScaleDistance.visualDistanceM,
visualReferenceWidthPx = diagnosticScaleDistance.visualReferenceWidthPx,
visualReferenceWidthMm = diagnosticScaleDistance.visualReferenceWidthMm,
visualReferenceStatus = diagnosticScaleDistance.visualReferenceStatus,
distanceSourceForScale = diagnosticScaleDistance.distanceSourceForScale.name,
distanceConfidence = diagnosticScaleDistance.distanceConfidence.name,
```

Place them immediately after `fallbackDistanceM = state.fallbackDistanceM,`.

- [ ] **Step 4: Run focused compile/test checks**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureSessionWriterTest" --tests "com.johnymoo.arverify.DepthDiagnosticsTest"
```

Expected: PASS.

Then run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/ArFrameExtractor.kt \
        app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt
git commit -m "feat: wire visual scale into capture flow"
```

---

### Task 9: Align the Legacy `CaptureWizardActivity` Upload Path

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureWizardActivity.kt`

- [ ] **Step 1: Add state for scale-distance metadata**

In `CaptureWizardActivity.kt`, after:

```kotlin
private var topDistanceM: Double = 0.0
```

add:

```kotlin
private var topDistanceSource: TargetDistanceSource = TargetDistanceSource.NONE
private var topScaleDistance: ScaleDistanceEstimate =
    VisualScaleEstimator.fallback(0.0, TargetDistanceSource.NONE, "NOT_EVALUATED")
```

- [ ] **Step 2: Capture the resolved source and visual scale**

Inside `doCapture`, replace:

```kotlin
topDistanceM = TargetDistanceResolver
    .resolve(depthTarget, fallbackDistanceM, depthRange)
    .distanceM
topRgb = rgbPart
topIntrinsics = ArFrameExtractor.intrinsics(frame)
topPose = ArFrameExtractor.pose(frame)
```

with:

```kotlin
val resolvedTarget = TargetDistanceResolver.resolve(depthTarget, fallbackDistanceM, depthRange)
topDistanceM = resolvedTarget.distanceM
topDistanceSource = resolvedTarget.source
topRgb = rgbPart
topIntrinsics = ArFrameExtractor.intrinsics(frame)
topPose = ArFrameExtractor.pose(frame)
topScaleDistance = VisualScaleEstimator.estimate(
    image = ArFrameExtractor.rgbPixelsFromJpeg(rgb),
    intrinsics = topIntrinsics!!,
    arcoreDistanceM = topDistanceM,
    arcoreDistanceSource = topDistanceSource,
)
```

- [ ] **Step 3: Write the new metadata fields in legacy upload**

In `startUpload`, replace the `RecognitionFrameMeta` construction:

```kotlin
recognitionFrame = RecognitionFrameMeta(
    imageIntrinsics = intr,
    depth = DepthDims(topDepthW, topDepthH),
    cameraPose = pose,
    distanceM = topDistanceM,
),
```

with:

```kotlin
recognitionFrame = RecognitionFrameMeta(
    imageIntrinsics = intr,
    depth = DepthDims(topDepthW, topDepthH),
    cameraPose = pose,
    distanceM = topScaleDistance.distanceM,
    arcoreDistanceM = topScaleDistance.arcoreDistanceM,
    arcoreDistanceSource = topScaleDistance.arcoreDistanceSource.name,
    visualDistanceM = topScaleDistance.visualDistanceM,
    visualReferenceWidthPx = topScaleDistance.visualReferenceWidthPx,
    visualReferenceWidthMm = topScaleDistance.visualReferenceWidthMm,
    visualReferenceStatus = topScaleDistance.visualReferenceStatus,
    distanceSourceForScale = topScaleDistance.distanceSourceForScale.name,
    distanceConfidence = topScaleDistance.distanceConfidence.name,
),
```

- [ ] **Step 4: Run compile check**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureWizardActivity.kt
git commit -m "fix: align legacy capture scale metadata"
```

---

### Task 10: Update Contract Sample

**Files:**
- Modify: `docs/contracts/ar-capture-contract.sample.json`

- [ ] **Step 1: Update the request sample**

Replace the existing `recognition_frame` block inside `request_ar_metadata` with:

```json
"recognition_frame": {
  "image_intrinsics": {"fx": 433.81, "fy": 433.81, "cx": 320.0, "cy": 240.0, "width": 640, "height": 480},
  "depth": {"width": 160, "height": 90, "format": "DEPTH16_MM"},
  "camera_pose": {"t": [0.01, -0.02, 0.30], "q": [0.0, 0.0, 0.0, 1.0]},
  "distance_m": 0.22584816,
  "arcore_distance_m": 0.673,
  "arcore_distance_source": "DEPTH",
  "visual_distance_m": 0.22584816,
  "visual_reference_width_px": 65.0,
  "visual_reference_width_mm": 33.84,
  "visual_reference_status": "DETECTED",
  "distance_source_for_scale": "VISUAL_REFERENCE",
  "distance_confidence": "HIGH"
}
```

- [ ] **Step 2: Validate sample JSON parses**

Run:

```bash
python3 -m json.tool docs/contracts/ar-capture-contract.sample.json >/tmp/ar-capture-contract.sample.pretty.json
```

Expected: command exits with code `0`.

- [ ] **Step 3: Commit**

```bash
git add docs/contracts/ar-capture-contract.sample.json
git commit -m "docs: document visual scale metadata contract"
```

---

### Task 11: Run Full JVM Verification

**Files:**
- No source edits in this task.

- [ ] **Step 1: Run all unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL and all tests pass.

- [ ] **Step 2: Build the debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Leave verification artifacts uncommitted**

Do not commit in this task. If verification fails, return to the task that introduced the failing behavior, make that task's concrete fix, and use that task's commit step.

---

### Task 12: Verify on PLG110 with a Real Red 2x2 Reference

**Files:**
- No source edits in this task.

- [ ] **Step 1: Confirm device connectivity**

Run:

```bash
adb devices -l
```

Expected: a connected PLG110 device appears, for example a line containing `PLG110`.

- [ ] **Step 2: Install the debug app**

Run:

```bash
./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and install succeeds.

- [ ] **Step 3: Capture a recognition session**

On the device:

1. Open ScanForge.
2. Start recognition capture.
3. Place the red 2x2 reference brick in the top-down frame.
4. Capture the TOP frame, then the required side/angle frames.
5. Tap diagnostics once while the red reference is visible.
6. Finish/upload only after the TOP frame has been captured.

Expected visual behavior: the capture status line distinguishes `尺度` from `门控`; when the red reference is detected, the scale label is around `0.15m` to `0.26m` for a normal top-down handheld distance.

- [ ] **Step 4: Pull the latest metadata and diagnostics**

Run:

```bash
adb shell 'find /sdcard/Android/data/com.johnymoo.arverify/files -name ar_metadata.json -o -name stats.json | sort | tail -20'
```

Expected: output includes at least one `ar_metadata.json` and one `stats.json`.

Pull the newest metadata and diagnostic files:

```bash
LATEST_META=$(adb shell 'find /sdcard/Android/data/com.johnymoo.arverify/files/captures -name ar_metadata.json | sort | tail -1' | tr -d '\r')
LATEST_STATS=$(adb shell 'find /sdcard/Android/data/com.johnymoo.arverify/files/diagnostics -name stats.json | sort | tail -1' | tr -d '\r')
adb pull "$LATEST_META" /tmp/ar_metadata.issue4.json
adb pull "$LATEST_STATS" /tmp/stats.issue4.json
```

- [ ] **Step 5: Inspect accepted metadata fields**

Run:

```bash
grep -E '"distance_m"|"arcore_distance_m"|"visual_distance_m"|"visual_reference_width_px"|"visual_reference_width_mm"|"distance_source_for_scale"|"distance_confidence"' /tmp/ar_metadata.issue4.json
```

Expected:
- `visual_reference_width_mm` is `33.84`.
- `distance_source_for_scale` is `"VISUAL_REFERENCE"` when the red reference is detected.
- `distance_confidence` is `"HIGH"` when the red reference is detected.
- `visual_reference_width_px` is in the visible brick-width range, usually tens of pixels.
- `visual_distance_m` and `distance_m` are around `0.15m` to `0.26m` for the real top-down capture.
- `arcore_distance_m` remains recorded separately and may still be around the unreliable `0.58m` to `0.67m` range.

- [ ] **Step 6: Inspect diagnostic fields**

Run:

```bash
grep -E '"scale_distance_m"|"arcore_distance_m"|"visual_distance_m"|"visual_reference_status"|"distance_source_for_scale"|"distance_confidence"' /tmp/stats.issue4.json
```

Expected:
- `scale_distance_m` exists.
- `arcore_distance_m` exists.
- `visual_reference_status` is `"DETECTED"` for a successful diagnostic capture with the red brick visible, or a concrete failure reason such as `"NO_RED_REFERENCE"` if the red brick is not visible.
- `distance_confidence` is `"HIGH"` only when the visual reference is detected.

- [ ] **Step 7: Commit no files**

This is a verification task only. Do not commit generated APKs, pulled JSON files, or device artifacts.

---

## Self-Review Notes

**Spec coverage:** The plan implements all issue #4 requirements: it stops using ARCore `distance_m` as the strong scale source by making `distance_m` the selected scale distance; it adds visual red-brick distance using `33.84mm`; it records `visual_distance_m`, `visual_reference_width_px`, `visual_reference_width_mm`, `distance_confidence`, and `distance_source_for_scale`; it preserves ARCore gate distance separately; diagnostics record success/failure and pixel width.

**Placeholder scan:** The plan contains no open implementation placeholders.

**Type consistency:** `ScaleDistanceEstimate`, `DistanceSourceForScale`, `DistanceConfidence`, `RgbImage`, and `VisualReferenceDetection` are introduced before use. `RecognitionFrameMeta` fields are serialized, written by `CaptureSessionWriter`, used by both capture paths, and mirrored in the contract sample.
