package com.johnymoo.arverify.capture

/** Pure pinhole-camera math + depth-center distance estimation (no Android types). */
object ScaleMath {

    /** Metric span (meters) of a feature spanning [pixelSpan] px at [distanceMeters], focal [focalLengthPx] px. */
    fun metricSpanMeters(pixelSpan: Double, distanceMeters: Double, focalLengthPx: Double): Double {
        if (focalLengthPx <= 0.0) return 0.0
        return pixelSpan * distanceMeters / focalLengthPx
    }

    /** Inverse of [metricSpanMeters]: pixels for a [metricSpanMeters] feature at [distanceMeters]. */
    fun pixelSpan(metricSpanMeters: Double, distanceMeters: Double, focalLengthPx: Double): Double {
        if (distanceMeters <= 0.0) return 0.0
        return metricSpanMeters * focalLengthPx / distanceMeters
    }

    /** Distance (meters) implied by a known physical width projected to [detectedWidthPx]. */
    fun distanceFromKnownWidthMeters(
        referenceWidthMeters: Double,
        focalLengthPx: Double,
        detectedWidthPx: Double,
    ): Double {
        if (referenceWidthMeters <= 0.0 || focalLengthPx <= 0.0 || detectedWidthPx <= 0.0) return 0.0
        return referenceWidthMeters * focalLengthPx / detectedWidthPx
    }

    /**
     * Median of valid (>0) depth samples inside a centered window covering [centerFraction] of
     * each dimension, converted mm→meters. Returns 0.0 when no valid samples (sentinel).
     */
    fun medianCenterDistanceMeters(
        depthMm: IntArray, width: Int, height: Int, centerFraction: Double = 0.2
    ): Double {
        if (width <= 0 || height <= 0 || depthMm.size < width * height) return 0.0
        val f = centerFraction.coerceIn(0.01, 1.0)
        val winW = (width * f).toInt().coerceAtLeast(1)
        val winH = (height * f).toInt().coerceAtLeast(1)
        val x0 = (width - winW) / 2
        val y0 = (height - winH) / 2
        val samples = ArrayList<Int>(winW * winH)
        for (y in y0 until y0 + winH) {
            val row = y * width
            for (x in x0 until x0 + winW) {
                val mm = depthMm[row + x]
                if (mm > 0) samples.add(mm)
            }
        }
        if (samples.isEmpty()) return 0.0
        samples.sort()
        val mid = samples.size / 2
        val medianMm = if (samples.size % 2 == 1) samples[mid].toDouble()
        else (samples[mid - 1] + samples[mid]) / 2.0
        return medianMm / 1000.0
    }
}
