package com.johnymoo.arverify.config

import android.content.Context

/** SharedPreferences-backed persistence for the tunable CaptureConfig (spec §6/§8). */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("capture_prefs", Context.MODE_PRIVATE)

    fun load(): CaptureConfig {
        val d = CaptureConfig()
        return CaptureConfig(
            baseUrl = sp.getString(KEY_BASE_URL, d.baseUrl) ?: d.baseUrl,
            minDistanceM = sp.getFloat(KEY_MIN_DIST, d.minDistanceM.toFloat()).toDouble(),
            maxDistanceM = sp.getFloat(KEY_MAX_DIST, d.maxDistanceM.toFloat()).toDouble(),
            minSharpness = sp.getFloat(KEY_SHARP, d.minSharpness.toFloat()).toDouble(),
            saveDebugRgbToGallery = sp.getBoolean(KEY_DEBUG_GALLERY, d.saveDebugRgbToGallery),
        )
    }

    fun setBaseUrl(url: String) = sp.edit().putString(KEY_BASE_URL, url).apply()

    fun setSaveDebugRgbToGallery(enabled: Boolean) =
        sp.edit().putBoolean(KEY_DEBUG_GALLERY, enabled).apply()

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MIN_DIST = "min_dist"
        private const val KEY_MAX_DIST = "max_dist"
        private const val KEY_SHARP = "min_sharp"
        private const val KEY_DEBUG_GALLERY = "debug_gallery"
    }
}
