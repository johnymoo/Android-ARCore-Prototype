package com.johnymoo.arverify.capture

/** Pure focus metric: variance of the 4-neighbour Laplacian over interior pixels of a luma grid. */
object Sharpness {
    /**
     * @param luma row-major grayscale values (any non-negative scale, e.g. 0..255), size width*height.
     * @return variance of the discrete Laplacian over interior pixels; 0.0 if the image has no interior.
     */
    fun varianceOfLaplacian(luma: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || luma.size < width * height) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val c = luma[row + x]
                val lap = (luma[row + x - 1] + luma[row + x + 1] +
                    luma[row - width + x] + luma[row + width + x] - 4 * c).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}
