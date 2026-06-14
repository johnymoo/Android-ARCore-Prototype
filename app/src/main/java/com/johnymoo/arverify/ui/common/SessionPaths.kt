package com.johnymoo.arverify.ui.common

import android.content.Context
import java.io.File

object SessionPaths {
    fun captureRoot(context: Context): File =
        File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }

    fun diagnosticsRoot(context: Context): File =
        File(context.getExternalFilesDir(null), "diagnostics").apply { mkdirs() }
}
