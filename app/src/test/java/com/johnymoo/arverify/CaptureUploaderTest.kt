package com.johnymoo.arverify

import com.johnymoo.arverify.net.CapturePackage
import com.johnymoo.arverify.net.CaptureUploader
import com.johnymoo.arverify.net.FilePart
import com.johnymoo.arverify.net.HttpResponse
import com.johnymoo.arverify.net.HttpTransport
import com.johnymoo.arverify.net.RecognitionStatus
import com.johnymoo.arverify.net.UploadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeTransport(private val scripted: List<Result<HttpResponse>>) : HttpTransport {
    var calls = 0
    var lastUrl: String? = null
    var lastContentType: String? = null
    var lastBody: ByteArray? = null
    override fun post(url: String, contentType: String, body: ByteArray): HttpResponse {
        lastUrl = url; lastContentType = contentType; lastBody = body
        val r = scripted[calls.coerceAtMost(scripted.size - 1)]
        calls++
        return r.getOrElse { throw it }
    }
}

class CaptureUploaderTest {
    private fun pkg() = CapturePackage(
        partId = "part-1", kind = "brick", systemHint = "feile",
        arMetadataJson = """{"device":{"model":"X"}}""",
        recognitionRgb = FilePart("rgb.jpg", "image/jpeg", byteArrayOf(1)),
        recognitionDepth = FilePart("depth.png", "image/png", byteArrayOf(2)),
        images = listOf(FilePart("a.jpg", "image/jpeg", byteArrayOf(3))),
    )

    private val ok = """{"status":"recognized","recognized":{"units_x":2,"units_y":4}}"""

    @Test fun postsToContractEndpointAndParsesSuccess() {
        val t = FakeTransport(listOf(Result.success(HttpResponse(200, ok))))
        val outcome = CaptureUploader(t, boundaryProvider = { "BND" })
            .upload("http://10.0.0.5:8000/", pkg())
        assertTrue(outcome is UploadOutcome.Success)
        outcome as UploadOutcome.Success
        assertEquals(RecognitionStatus.RECOGNIZED, outcome.result.status)
        assertEquals(1, t.calls)
        assertEquals("http://10.0.0.5:8000/api/v1/ar-captures", t.lastUrl)
        assertEquals("multipart/form-data; boundary=BND", t.lastContentType)
        val body = String(t.lastBody!!, Charsets.ISO_8859_1)
        assertTrue(body.contains("name=\"part_id\""))
        assertTrue(body.contains("name=\"kind\""))
        assertTrue(body.contains("name=\"system_hint\""))
        assertTrue(body.contains("name=\"ar_metadata\""))
        assertTrue(body.contains("name=\"recognition_rgb\"; filename=\"rgb.jpg\""))
        assertTrue(body.contains("name=\"recognition_depth\"; filename=\"depth.png\""))
        assertTrue(body.contains("name=\"images\"; filename=\"a.jpg\""))
    }

    @Test fun retriesServerErrorsThenSucceeds() {
        val t = FakeTransport(listOf(
            Result.success(HttpResponse(500, "err")),
            Result.success(HttpResponse(503, "err")),
            Result.success(HttpResponse(200, ok)),
        ))
        val outcome = CaptureUploader(t, maxAttempts = 3, boundaryProvider = { "BND" })
            .upload("http://h/", pkg())
        assertTrue(outcome is UploadOutcome.Success)
        assertEquals(3, t.calls)
    }

    @Test fun doesNotRetryClientErrors() {
        val t = FakeTransport(listOf(Result.success(HttpResponse(400, "bad"))))
        val outcome = CaptureUploader(t, maxAttempts = 3, boundaryProvider = { "BND" })
            .upload("http://h/", pkg())
        assertTrue(outcome is UploadOutcome.Failure)
        outcome as UploadOutcome.Failure
        assertEquals(400, outcome.lastCode)
        assertTrue(outcome.message.contains("bad"))
        assertEquals(1, t.calls)
    }

    @Test fun exhaustsRetriesOnRepeatedNetworkErrors() {
        val t = FakeTransport(listOf(Result.failure(IOException("net"))))
        val outcome = CaptureUploader(t, maxAttempts = 3, boundaryProvider = { "BND" })
            .upload("http://h/", pkg())
        assertTrue(outcome is UploadOutcome.Failure)
        assertEquals(3, t.calls)
    }
}
