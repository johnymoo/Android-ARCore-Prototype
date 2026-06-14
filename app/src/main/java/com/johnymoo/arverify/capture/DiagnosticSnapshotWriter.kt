package com.johnymoo.arverify.capture

import android.graphics.Bitmap
import com.johnymoo.arverify.depth.DepthHeatmap
import com.johnymoo.arverify.depth.DepthRangeMm
import com.johnymoo.arverify.imaging.Depth16PngWriter
import java.io.File

fun interface DepthPreviewPngWriter {
    fun write(file: File, depthGridMm: IntArray, depthWidth: Int, depthHeight: Int, depthRange: DepthRangeMm)
}

class DiagnosticSnapshotWriter(
    private val rootDir: File,
    private val previewWriter: DepthPreviewPngWriter = DepthPreviewPngWriter { file, depthGridMm, depthWidth, depthHeight, depthRange ->
        val preview = DepthHeatmap.toBitmap(depthGridMm, depthWidth, depthHeight, depthRange)
        file.outputStream().use {
            preview.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    },
) {
    fun write(
        report: DepthFrameDiagnostics,
        rgbJpeg: ByteArray?,
        depthGridMm: IntArray?,
        depthWidth: Int,
        depthHeight: Int,
        depthRange: DepthRangeMm,
    ): File {
        val dir = File(rootDir, "diag-${report.createdAtEpochMs}").apply { mkdirs() }
        File(dir, "stats.json").writeText(DepthDiagnostics.toJson(report))

        if (rgbJpeg != null) {
            File(dir, "rgb.jpg").writeBytes(rgbJpeg)
        }
        if (depthGridMm != null && depthWidth > 0 && depthHeight > 0 && depthGridMm.size >= depthWidth * depthHeight) {
            File(dir, "depth.png").writeBytes(Depth16PngWriter.encode(depthGridMm, depthWidth, depthHeight))
            previewWriter.write(File(dir, "depth_preview.png"), depthGridMm, depthWidth, depthHeight, depthRange)
        }

        return dir
    }
}
