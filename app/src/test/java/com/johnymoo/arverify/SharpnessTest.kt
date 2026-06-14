package com.johnymoo.arverify

import com.johnymoo.arverify.capture.Sharpness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharpnessTest {
    @Test fun uniformImageHasZeroSharpness() {
        val flat = IntArray(8 * 8) { 128 }
        assertEquals(0.0, Sharpness.varianceOfLaplacian(flat, 8, 8), 1e-9)
    }

    @Test fun sharpEdgesScoreHigherThanGradient() {
        val w = 8; val h = 8
        // Hard checkerboard = strong second derivative everywhere.
        val sharp = IntArray(w * h) { i -> if (((i % w) + (i / w)) % 2 == 0) 0 else 255 }
        // Smooth horizontal ramp = near-zero second derivative.
        val smooth = IntArray(w * h) { i -> ((i % w) * 255 / (w - 1)) }
        assertTrue(
            Sharpness.varianceOfLaplacian(sharp, w, h) >
                Sharpness.varianceOfLaplacian(smooth, w, h)
        )
    }

    @Test fun tooSmallImageIsZero() {
        assertEquals(0.0, Sharpness.varianceOfLaplacian(IntArray(2) { 10 }, 2, 1), 1e-9)
    }
}
