package com.johnymoo.arverify.ui.library

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SessionDisplay {
    private const val UNKNOWN_TIME = "时间未知"

    fun captureTime(
        epochMs: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        if (epochMs <= 0L) return UNKNOWN_TIME
        return SimpleDateFormat("yyyy-MM-dd HH:mm", locale).apply {
            this.timeZone = timeZone
        }.format(Date(epochMs))
    }
}
