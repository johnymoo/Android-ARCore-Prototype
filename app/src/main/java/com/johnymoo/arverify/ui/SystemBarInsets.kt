package com.johnymoo.arverify.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

private fun View.basePadding(): EdgeInsets =
    EdgeInsets(paddingLeft, paddingTop, paddingRight, paddingBottom)

private fun View.baseMargins(): EdgeInsets {
    val params = layoutParams as? ViewGroup.MarginLayoutParams
        ?: return EdgeInsets(0, 0, 0, 0)
    return EdgeInsets(
        left = params.leftMargin,
        top = params.topMargin,
        right = params.rightMargin,
        bottom = params.bottomMargin,
    )
}

private fun View.updateMargins(margins: EdgeInsets) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.setMargins(margins.left, margins.top, margins.right, margins.bottom)
    layoutParams = params
}

private fun WindowInsetsCompat.systemBars(): EdgeInsets {
    val bars = getInsets(WindowInsetsCompat.Type.systemBars())
    return EdgeInsets(bars.left, bars.top, bars.right, bars.bottom)
}

fun View.applyContentSystemBarPadding() {
    val base = basePadding()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val padding = SystemBarInsetsPolicy.contentPadding(base, insets.systemBars())
        view.updatePadding(
            left = padding.left,
            top = padding.top,
            right = padding.right,
            bottom = padding.bottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.applyTopChromeSystemBarPadding() {
    val base = basePadding()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val padding = SystemBarInsetsPolicy.topChromePadding(base, insets.systemBars())
        view.updatePadding(
            left = padding.left,
            top = padding.top,
            right = padding.right,
            bottom = padding.bottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.applyBottomChromeSystemBarPadding() {
    val base = basePadding()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val padding = SystemBarInsetsPolicy.bottomChromePadding(base, insets.systemBars())
        view.updatePadding(
            left = padding.left,
            top = padding.top,
            right = padding.right,
            bottom = padding.bottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.applyTopChromeSystemBarMargins() {
    val base = baseMargins()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        view.updateMargins(SystemBarInsetsPolicy.topChromeMargins(base, insets.systemBars()))
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.applyBottomChromeSystemBarMargins() {
    val base = baseMargins()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        view.updateMargins(SystemBarInsetsPolicy.bottomChromeMargins(base, insets.systemBars()))
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
