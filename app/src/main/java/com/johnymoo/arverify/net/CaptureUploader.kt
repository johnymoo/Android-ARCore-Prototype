package com.johnymoo.arverify.net

import java.util.UUID

/** One captured file (already encoded to bytes by the on-device layer). */
data class FilePart(val filename: String, val contentType: String, val bytes: ByteArray)

/** Everything needed to upload one capture (spec §5 multipart fields). */
data class CapturePackage(
    val partId: String,
    val kind: String,
    val systemHint: String?,
    val arMetadataJson: String,
    val recognitionRgb: FilePart,
    val recognitionDepth: FilePart,
    val images: List<FilePart>,
)

/** Result of an upload attempt sequence. */
sealed class UploadOutcome {
    data class Success(val result: RecognitionResult, val rawBody: String) : UploadOutcome()
    data class Failure(val message: String, val lastCode: Int?) : UploadOutcome()
}

/**
 * Builds the multipart body, POSTs via [transport], and parses the response.
 * Retries on 5xx / transport exceptions up to [maxAttempts]; never retries 4xx.
 */
class CaptureUploader(
    private val transport: HttpTransport,
    private val maxAttempts: Int = 3,
    private val boundaryProvider: () -> String = { "arcapture" + UUID.randomUUID().toString().replace("-", "") },
) {
    fun upload(baseUrl: String, pkg: CapturePackage): UploadOutcome {
        val url = baseUrl.trimEnd('/') + ENDPOINT
        val boundary = boundaryProvider()
        val mb = MultipartBuilder(boundary)
            .addFormField("part_id", pkg.partId)
            .addFormField("kind", pkg.kind)
        pkg.systemHint?.let { mb.addFormField("system_hint", it) }
        mb.addFormField("ar_metadata", pkg.arMetadataJson)
        mb.addFilePart("recognition_rgb", pkg.recognitionRgb.filename, pkg.recognitionRgb.contentType, pkg.recognitionRgb.bytes)
        mb.addFilePart("recognition_depth", pkg.recognitionDepth.filename, pkg.recognitionDepth.contentType, pkg.recognitionDepth.bytes)
        pkg.images.forEach { mb.addFilePart("images[]", it.filename, it.contentType, it.bytes) }
        val body = mb.build()
        val contentType = mb.contentType()

        var lastMsg = "upload failed"
        var lastCode: Int? = null
        repeat(maxAttempts) {
            try {
                val resp = transport.post(url, contentType, body)
                when {
                    resp.code in 200..299 ->
                        return UploadOutcome.Success(RecognitionResultModel.parse(resp.body), resp.body)
                    resp.code in 400..499 ->
                        return UploadOutcome.Failure("HTTP ${resp.code}", resp.code)
                    else -> { lastMsg = "HTTP ${resp.code}"; lastCode = resp.code }
                }
            } catch (e: Exception) {
                lastMsg = e.javaClass.simpleName
            }
        }
        return UploadOutcome.Failure(lastMsg, lastCode)
    }

    companion object { const val ENDPOINT = "/api/v1/ar-captures" }
}
