package com.johnymoo.arverify

import com.johnymoo.arverify.debug.ViewfinderGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewfinderGeometryTest {
    @Test fun fitsSixteenByNineFrameBetweenTopAndBottomChrome() {
        val rect = ViewfinderGeometry.centeredFrame(
            width = 1272,
            height = 2772,
            topInset = 180,
            bottomInset = 280,
            aspectW = 16,
            aspectH = 9,
        )

        assertEquals(51, rect.left)
        assertEquals(1221, rect.right)
        assertEquals(1007, rect.top)
        assertEquals(1665, rect.bottom)
        assertTrue(rect.top >= 180)
        assertTrue(rect.bottom <= 2492)
    }

    @Test fun constrainsFrameByAvailableHeightOnWideScreens() {
        val rect = ViewfinderGeometry.centeredFrame(
            width = 2400,
            height = 1080,
            topInset = 120,
            bottomInset = 180,
            aspectW = 16,
            aspectH = 9,
        )

        assertEquals(507, rect.left)
        assertEquals(1893, rect.right)
        assertEquals(120, rect.top)
        assertEquals(900, rect.bottom)
    }
}
