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
