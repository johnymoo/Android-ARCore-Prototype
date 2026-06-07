package com.johnymoo.arverify.ui

data class EdgeInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object SystemBarInsetsPolicy {
    fun contentPadding(base: EdgeInsets, bars: EdgeInsets): EdgeInsets =
        EdgeInsets(
            left = base.left + bars.left,
            top = base.top + bars.top,
            right = base.right + bars.right,
            bottom = base.bottom + bars.bottom,
        )

    fun topChromePadding(base: EdgeInsets, bars: EdgeInsets): EdgeInsets =
        EdgeInsets(
            left = base.left + bars.left,
            top = base.top + bars.top,
            right = base.right + bars.right,
            bottom = base.bottom,
        )

    fun topChromeMargins(base: EdgeInsets, bars: EdgeInsets): EdgeInsets =
        EdgeInsets(
            left = base.left + bars.left,
            top = base.top + bars.top,
            right = base.right + bars.right,
            bottom = base.bottom,
        )

    fun bottomChromePadding(base: EdgeInsets, bars: EdgeInsets): EdgeInsets =
        EdgeInsets(
            left = base.left + bars.left,
            top = base.top,
            right = base.right + bars.right,
            bottom = base.bottom + bars.bottom,
        )

    fun bottomChromeMargins(base: EdgeInsets, bars: EdgeInsets): EdgeInsets =
        EdgeInsets(
            left = base.left + bars.left,
            top = base.top,
            right = base.right + bars.right,
            bottom = base.bottom + bars.bottom,
        )
}
