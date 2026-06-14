package com.johnymoo.arverify

import com.johnymoo.arverify.ui.library.SessionDisplay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class SessionDisplayTest {
    @Test fun formatsCaptureTimeWithDateAndMinute() {
        val text = SessionDisplay.captureTime(
            epochMs = 1_718_179_200_000L,
            locale = Locale.US,
            timeZone = TimeZone.getTimeZone("UTC"),
        )

        assertEquals("2024-06-12 08:00", text)
    }

    @Test fun unknownTimeForInvalidEpoch() {
        assertEquals("时间未知", SessionDisplay.captureTime(0L))
    }
}
