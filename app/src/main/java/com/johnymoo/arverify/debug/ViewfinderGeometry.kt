package com.johnymoo.arverify.debug

/** Pure geometry for the on-screen debug frame that marks the saved/recognized image area. */
object ViewfinderGeometry {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun centeredFrame(
        width: Int,
        height: Int,
        topInset: Int,
        bottomInset: Int,
        aspectW: Int = 16,
        aspectH: Int = 9,
        horizontalMarginFraction: Double = 0.04,
    ): Rect {
        require(width > 0 && height > 0)
        require(aspectW > 0 && aspectH > 0)
        val safeTop = topInset.coerceIn(0, height)
        val safeBottom = (height - bottomInset).coerceIn(safeTop, height)
        val availableHeight = (safeBottom - safeTop).coerceAtLeast(1)
        val maxFrameWidth = (width * (1.0 - horizontalMarginFraction * 2.0)).toInt().coerceAtLeast(1)
        val maxFrameHeightForWidth = (maxFrameWidth * aspectH.toDouble() / aspectW).toInt().coerceAtLeast(1)

        val frameWidth: Int
        val frameHeight: Int
        if (maxFrameHeightForWidth <= availableHeight) {
            frameWidth = maxFrameWidth
            frameHeight = maxFrameHeightForWidth
        } else {
            frameHeight = availableHeight
            frameWidth = (frameHeight * aspectW.toDouble() / aspectH).toInt().coerceAtMost(width)
        }

        val left = ((width - frameWidth) / 2).coerceAtLeast(0)
        val top = safeTop + ((availableHeight - frameHeight) / 2).coerceAtLeast(0)
        return Rect(
            left = left,
            top = top,
            right = (left + frameWidth).coerceAtMost(width),
            bottom = (top + frameHeight).coerceAtMost(height),
        )
    }
}
