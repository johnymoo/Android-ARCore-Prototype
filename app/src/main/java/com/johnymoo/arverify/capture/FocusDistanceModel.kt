package com.johnymoo.arverify.capture

import java.util.Locale

object FocusDistanceModel {
    private const val AF_PASSIVE_FOCUSED = 2
    private const val AF_FOCUSED_LOCKED = 4

    fun metersFromDiopters(diopters: Float): Double? {
        if (diopters <= 0f) return null
        return 1.0 / diopters.toDouble()
    }

    fun isFocusedAfState(afState: Int?): Boolean {
        return afState == AF_PASSIVE_FOCUSED || afState == AF_FOCUSED_LOCKED
    }

    fun gateDistanceMeters(focusMeters: Double?, afState: Int?): Double? {
        return when {
            focusMeters == null -> null
            afState == null -> focusMeters
            isFocusedAfState(afState) -> focusMeters
            else -> null
        }
    }

    fun debugText(focusMeters: Double?, afState: Int?): String {
        val meters = focusMeters?.let { String.format(Locale.US, "%.2fm", it) } ?: "--"
        val state = afState?.let { " · AF $it" } ?: ""
        return "焦点 $meters$state"
    }
}
