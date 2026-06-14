package com.johnymoo.arverify

import com.johnymoo.arverify.model.RecommendedRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendedRouteTest {
    @Test fun arcoreAndDepth_givesDepthCapture() {
        assertEquals(RecommendedRoute.DEPTH_CAPTURE, RecommendedRoute.decide(arcoreOk = true, depthOk = true))
    }

    @Test fun arcoreButNoDepth_givesPhotoModeling() {
        assertEquals(RecommendedRoute.PHOTO_MODELING, RecommendedRoute.decide(arcoreOk = true, depthOk = false))
    }

    @Test fun noArcore_alwaysPhotoModeling() {
        assertEquals(RecommendedRoute.PHOTO_MODELING, RecommendedRoute.decide(arcoreOk = false, depthOk = true))
        assertEquals(RecommendedRoute.PHOTO_MODELING, RecommendedRoute.decide(arcoreOk = false, depthOk = false))
    }
}
