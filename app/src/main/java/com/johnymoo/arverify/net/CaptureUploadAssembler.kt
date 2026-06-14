package com.johnymoo.arverify.net

import java.io.File

object CaptureUploadAssembler {
    const val MIN_IMAGES = 4

    data class UploadReadiness(val ready: Boolean, val message: String? = null)

    fun fromDir(dir: File, partId: String, kind: String, systemHint: String?): CapturePackage? {
        val metaFile = File(dir, "ar_metadata.json")
        val rgbFile = File(dir, "recognition_rgb.jpg")
        val depthFile = File(dir, "recognition_depth.png")
        if (!metaFile.isFile || !rgbFile.isFile || !depthFile.isFile) return null

        val images = (dir.listFiles { f -> f.isFile && f.name.matches(Regex("frame_\\d+\\.jpg")) } ?: emptyArray())
            .sortedBy { it.name }
            .map { FilePart(it.name, "image/jpeg", it.readBytes()) }

        return CapturePackage(
            partId = partId,
            kind = kind,
            systemHint = systemHint,
            arMetadataJson = metaFile.readText(),
            recognitionRgb = FilePart("recognition_rgb.jpg", "image/jpeg", rgbFile.readBytes()),
            recognitionDepth = FilePart("recognition_depth.png", "image/png", depthFile.readBytes()),
            images = images,
        )
    }

    fun missingImageCount(pkg: CapturePackage): Int =
        (MIN_IMAGES - pkg.images.size).coerceAtLeast(0)

    fun readiness(dir: File): UploadReadiness {
        val pkg = fromDir(dir, dir.name.substringBeforeLast('_'), "brick", null)
            ?: return UploadReadiness(false, "缺少识别帧，请先拍俯视凸点面")
        val missing = missingImageCount(pkg)
        if (missing > 0) return UploadReadiness(false, "还需补拍 ${missing} 张角度图后再上传")
        return UploadReadiness(true)
    }
}
