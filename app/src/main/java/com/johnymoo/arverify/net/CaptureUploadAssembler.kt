package com.johnymoo.arverify.net

import java.io.File

object CaptureUploadAssembler {
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
}
