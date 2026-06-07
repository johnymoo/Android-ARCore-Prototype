package com.johnymoo.arverify.net

import java.io.File

/** Persists a capture package under [rootDir] so data is never lost on upload failure (spec §6). */
class LocalRetention(private val rootDir: File) {

    /** Writes all parts into rootDir/<sanitizedPartId>_<stamp>/ and returns that directory. */
    fun save(pkg: CapturePackage, stamp: String): File {
        val dir = File(rootDir, "${sanitize(pkg.partId)}_$stamp").apply { mkdirs() }
        File(dir, "ar_metadata.json").writeText(pkg.arMetadataJson)
        File(dir, "recognition_rgb.${ext(pkg.recognitionRgb.filename, "jpg")}").writeBytes(pkg.recognitionRgb.bytes)
        File(dir, "recognition_depth.${ext(pkg.recognitionDepth.filename, "png")}").writeBytes(pkg.recognitionDepth.bytes)
        pkg.images.forEachIndexed { i, part ->
            File(dir, "image_${i}_${sanitize(part.filename)}").writeBytes(part.bytes)
        }
        return dir
    }

    private fun sanitize(s: String): String =
        s.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }.joinToString("")

    private fun ext(filename: String, default: String): String =
        filename.substringAfterLast('.', default).ifBlank { default }
}
