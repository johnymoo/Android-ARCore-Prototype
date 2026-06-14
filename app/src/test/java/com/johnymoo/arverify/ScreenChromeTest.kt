package com.johnymoo.arverify

import com.johnymoo.arverify.R
import com.johnymoo.arverify.ui.nav.Routes
import com.johnymoo.arverify.ui.nav.topBarFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenChromeTest {
    @Test fun tabsHaveNoBack() {
        assertFalse(topBarFor(Routes.HOME)!!.showBack)
        assertFalse(topBarFor(Routes.LIBRARY)!!.showBack)
        assertFalse(topBarFor(Routes.SETTINGS)!!.showBack)
    }

    @Test fun subScreensShowBack() {
        assertTrue(topBarFor(Routes.DIAGNOSTICS)!!.showBack)
        assertTrue(topBarFor(Routes.SESSION_DETAIL)!!.showBack)
        assertTrue(topBarFor(Routes.FRAME_VIEWER)!!.showBack)
    }

    @Test fun titlesMapToResources() {
        assertEquals(R.string.nav_library, topBarFor(Routes.LIBRARY)!!.titleRes)
        assertEquals(R.string.screen_session_detail, topBarFor(Routes.SESSION_DETAIL)!!.titleRes)
    }

    @Test fun unknownRouteIsNull() {
        assertNull(topBarFor("nope"))
        assertNull(topBarFor(null))
    }
}
