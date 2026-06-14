package com.johnymoo.arverify

import com.johnymoo.arverify.ui.EdgeInsets
import com.johnymoo.arverify.ui.SystemBarInsetsPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarInsetsPolicyTest {
    @Test fun contentPaddingKeepsOriginalPaddingAndAddsAllSystemBars() {
        val result = SystemBarInsetsPolicy.contentPadding(
            base = EdgeInsets(left = 20, top = 20, right = 20, bottom = 20),
            bars = EdgeInsets(left = 3, top = 48, right = 5, bottom = 60),
        )

        assertEquals(EdgeInsets(left = 23, top = 68, right = 25, bottom = 80), result)
    }

    @Test fun cameraTopChromeStaysBelowStatusBarWithoutLiftingBottom() {
        val result = SystemBarInsetsPolicy.topChromePadding(
            base = EdgeInsets(left = 0, top = 12, right = 0, bottom = 12),
            bars = EdgeInsets(left = 2, top = 48, right = 4, bottom = 60),
        )

        assertEquals(EdgeInsets(left = 2, top = 60, right = 4, bottom = 12), result)
    }

    @Test fun floatingTopChromeMovesBelowStatusBarAsAWhole() {
        val result = SystemBarInsetsPolicy.topChromeMargins(
            base = EdgeInsets(left = 0, top = 12, right = 12, bottom = 0),
            bars = EdgeInsets(left = 2, top = 48, right = 4, bottom = 60),
        )

        assertEquals(EdgeInsets(left = 2, top = 60, right = 16, bottom = 0), result)
    }

    @Test fun cameraBottomChromeStaysAboveNavigationBarWithoutChangingTop() {
        val result = SystemBarInsetsPolicy.bottomChromePadding(
            base = EdgeInsets(left = 0, top = 16, right = 0, bottom = 16),
            bars = EdgeInsets(left = 2, top = 48, right = 4, bottom = 60),
        )

        assertEquals(EdgeInsets(left = 2, top = 16, right = 4, bottom = 76), result)
    }

    @Test fun floatingBottomChromeMovesAboveNavigationBarAsAWhole() {
        val result = SystemBarInsetsPolicy.bottomChromeMargins(
            base = EdgeInsets(left = 0, top = 0, right = 0, bottom = 12),
            bars = EdgeInsets(left = 2, top = 48, right = 4, bottom = 60),
        )

        assertEquals(EdgeInsets(left = 2, top = 0, right = 4, bottom = 72), result)
    }
}
