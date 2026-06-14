package com.johnymoo.arverify

import com.johnymoo.arverify.capture.DistanceConfidence
import com.johnymoo.arverify.capture.DistanceSourceForScale
import com.johnymoo.arverify.capture.RgbImage
import com.johnymoo.arverify.capture.ScaleEstimationPolicy
import com.johnymoo.arverify.capture.TargetDistanceSource
import com.johnymoo.arverify.capture.VisualScaleEstimator
import com.johnymoo.arverify.metadata.CameraIntrinsics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            policy = ScaleEstimationPolicy(referenceModeEnabled = true, maxDepthDisagreementM = 1.0),
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

    @Test fun fallsBackToArcoreDistanceWithLowConfidenceWhenReferenceMissing() {
        val darkImage = RgbImage(80, 60, IntArray(80 * 60) { 0x202020 })

        val estimate = VisualScaleEstimator.estimate(
            image = darkImage,
            intrinsics = intrinsics,
            arcoreDistanceM = 0.673,
            arcoreDistanceSource = TargetDistanceSource.HIT_TEST,
            policy = ScaleEstimationPolicy(referenceModeEnabled = true),
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
            policy = ScaleEstimationPolicy(referenceModeEnabled = true),
        )

        assertEquals(0.5807, estimate.distanceM, 0.0)
        assertEquals("RGB_UNAVAILABLE", estimate.visualReferenceStatus)
        assertEquals(DistanceConfidence.LOW, estimate.distanceConfidence)
    }

    @Test fun detectedReferenceWithInvalidIntrinsicsFallsBackToArcoreDistance() {
        val image = redRectImage(width = 120, height = 80, rectWidth = 65)
        val invalidIntrinsics = intrinsics.copy(fx = 0.0)

        val estimate = VisualScaleEstimator.estimate(
            image = image,
            intrinsics = invalidIntrinsics,
            arcoreDistanceM = 0.673,
            arcoreDistanceSource = TargetDistanceSource.DEPTH,
            policy = ScaleEstimationPolicy(referenceModeEnabled = true),
        )

        assertEquals(0.673, estimate.distanceM, 0.0)
        assertEquals(0.673, estimate.arcoreDistanceM, 0.0)
        assertNull(estimate.visualDistanceM)
        assertNull(estimate.visualReferenceWidthPx)
        assertEquals(DistanceSourceForScale.ARCORE_DISTANCE, estimate.distanceSourceForScale)
        assertEquals(DistanceConfidence.LOW, estimate.distanceConfidence)
        assertEquals("INVALID_INTRINSICS", estimate.visualReferenceStatus)
    }

    @Test fun rgbUnavailableWithNonPositiveArcoreDistanceFallsBackToZeroDistance() {
        val estimate = VisualScaleEstimator.estimate(
            image = null,
            intrinsics = intrinsics,
            arcoreDistanceM = -0.25,
            arcoreDistanceSource = TargetDistanceSource.DEPTH,
            policy = ScaleEstimationPolicy(referenceModeEnabled = true),
        )

        assertEquals(0.0, estimate.distanceM, 0.0)
        assertEquals(0.0, estimate.arcoreDistanceM, 0.0)
        assertNull(estimate.visualDistanceM)
        assertNull(estimate.visualReferenceWidthPx)
        assertEquals(DistanceSourceForScale.ARCORE_DISTANCE, estimate.distanceSourceForScale)
        assertEquals(DistanceConfidence.LOW, estimate.distanceConfidence)
        assertEquals("RGB_UNAVAILABLE", estimate.visualReferenceStatus)
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
