package com.johnymoo.arverify.capture

data class HitTestPoint(val x: Float, val y: Float)

object ReticleHitTestSampler {
    private const val SAMPLE_SPACING_FRACTION = 0.18f

    fun samplePoints(width: Int, height: Int): List<HitTestPoint> {
        if (width <= 0 || height <= 0) return emptyList()

        val cx = width * 0.5f
        val cy = height * 0.5f
        val spacing = minOf(width, height) * SAMPLE_SPACING_FRACTION
        val offsets = floatArrayOf(-spacing, 0f, spacing)
        return offsets.flatMap { dy ->
            offsets.map { dx ->
                HitTestPoint(
                    x = (cx + dx).coerceIn(0f, (width - 1).toFloat()),
                    y = (cy + dy).coerceIn(0f, (height - 1).toFloat()),
                )
            }
        }
    }
}
