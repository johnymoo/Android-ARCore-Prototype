package com.johnymoo.arverify.capture

import kotlin.math.asin

object CameraPitch {
    fun fromQuaternion(x: Double, y: Double, z: Double, w: Double): Double {
        val forwardY = (2.0 * (w * x - y * z)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(asin(forwardY))
    }
}
