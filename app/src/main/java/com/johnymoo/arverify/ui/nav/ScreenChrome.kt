package com.johnymoo.arverify.ui.nav

import androidx.annotation.StringRes
import com.johnymoo.arverify.R

/** Top-bar descriptor for a NavHost route. */
data class ScreenChrome(@StringRes val titleRes: Int, val showBack: Boolean)

/**
 * Pure route -> top-bar mapping. Tab routes show a title only; sub-screens show
 * title + back. Returns null for routes that render their own chrome (none here)
 * or unknown routes. Matched against NavDestination.route (the pattern, e.g.
 * "session/{dir}"), so the encoded argument value is irrelevant.
 */
fun topBarFor(route: String?): ScreenChrome? = when (route) {
    Routes.HOME -> ScreenChrome(R.string.app_name, showBack = false)
    Routes.LIBRARY -> ScreenChrome(R.string.nav_library, showBack = false)
    Routes.SETTINGS -> ScreenChrome(R.string.nav_settings, showBack = false)
    Routes.DIAGNOSTICS -> ScreenChrome(R.string.settings_diagnostics, showBack = true)
    Routes.SESSION_DETAIL -> ScreenChrome(R.string.screen_session_detail, showBack = true)
    Routes.FRAME_VIEWER -> ScreenChrome(R.string.screen_frame_viewer, showBack = true)
    else -> null
}
