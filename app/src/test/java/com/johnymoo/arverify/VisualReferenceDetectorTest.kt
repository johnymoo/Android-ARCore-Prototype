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

    @Test fun reportsFailureWhenRedReferenceIsBelowMinPixels() {
        val image = imageWithRedRect(width = 50, height = 40, left = 10, top = 10, rectWidth = 8, rectHeight = 5)

        val result = RedBrickReferenceDetector.detect(image, minPixels = 50)

        assertEquals(VisualReferenceDetection.Failed("REFERENCE_TOO_SMALL"), result)
    }

    @Test fun reportsFailureWhenRedReferenceLongSideIsTooSmall() {
        val image = imageWithRedRect(width = 50, height = 40, left = 10, top = 10, rectWidth = 7, rectHeight = 6)

        val result = RedBrickReferenceDetector.detect(image)

        assertEquals(VisualReferenceDetection.Failed("REFERENCE_TOO_SMALL"), result)
    }

    @Test fun reportsFailureWhenRedReferenceIsFragmented() {
        val width = 80
        val pixels = IntArray(width * 60) { 0x282828 }
        paintHorizontalLine(pixels, width, left = 10, top = 12, lineWidth = 30, color = 0xD71920)
        paintVerticalLine(pixels, width, left = 10, top = 12, lineHeight = 20, color = 0xD71920)

        val result = RedBrickReferenceDetector.detect(RgbImage(width, 60, pixels))

        assertEquals(VisualReferenceDetection.Failed("REFERENCE_FRAGMENTED"), result)
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

    private fun paintHorizontalLine(
        pixels: IntArray,
        imageWidth: Int,
        left: Int,
        top: Int,
        lineWidth: Int,
        color: Int,
    ) {
        for (x in left until left + lineWidth) {
            pixels[top * imageWidth + x] = color
        }
    }

    private fun paintVerticalLine(
        pixels: IntArray,
        imageWidth: Int,
        left: Int,
        top: Int,
        lineHeight: Int,
        color: Int,
    ) {
        for (y in top until top + lineHeight) {
            pixels[y * imageWidth + left] = color
        }
    }
}
