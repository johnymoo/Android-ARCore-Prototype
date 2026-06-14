package com.johnymoo.arverify.ui.nav

import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val SESSION_DETAIL = "session/{dir}"
    const val FRAME_VIEWER = "frame/{dir}/{index}"

    fun sessionDetail(dirPath: String) = "session/${enc(dirPath)}"
    fun frameViewer(dirPath: String, index: Int) = "frame/${enc(dirPath)}/$index"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
