package com.johnymoo.arverify.model

/** Which modeling route the report recommends, per the issue #1 MVP conclusion. */
enum class RecommendedRoute(val labelZh: String) {
    DEPTH_CAPTURE("深度采集"),
    PHOTO_MODELING("照片建模");

    companion object {
        fun decide(arcoreOk: Boolean, depthOk: Boolean): RecommendedRoute =
            if (arcoreOk && depthOk) DEPTH_CAPTURE else PHOTO_MODELING
    }
}
