package com.johnymoo.arverify

import com.google.gson.JsonParser
import com.johnymoo.arverify.net.HttpResponse
import com.johnymoo.arverify.net.HttpTransport
import com.johnymoo.arverify.net.ParametricBlockRequest
import com.johnymoo.arverify.net.ParametricOutcome
import com.johnymoo.arverify.net.ParametricSubmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricSubmissionTest {

    private class FakeTransport(val code: Int, val body: String) : HttpTransport {
        var url: String? = null
        var contentType: String? = null
        var sent: String? = null
        override fun post(url: String, contentType: String, body: ByteArray): HttpResponse {
            this.url = url; this.contentType = contentType; this.sent = String(body, Charsets.UTF_8)
            return HttpResponse(code, this.body)
        }
    }

    private fun req() = ParametricBlockRequest(
        system = "feile", kind = "brick", unitsX = 2, unitsY = 4,
        rawMeasurementsMm = linkedMapOf(
            "outer_pitch_mm" to 31.8, "inner_pitch_mm" to 16.0, "stud_diameter_mm" to 7.9,
            "brick_height_net_mm" to 9.6, "brick_height_total_mm" to 11.4,
        ),
    )

    @Test fun rawMeasurementsJsonHasFiveKeysAndIsValid() {
        val json = ParametricSubmission.rawMeasurementsJson(req().rawMeasurementsMm)
        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals(5, obj.size())
        assertEquals(31.8, obj["outer_pitch_mm"].asDouble, 1e-9)
        assertTrue(obj.has("brick_height_total_mm"))
    }

    @Test fun postsToParametricBlocksEndpointWithAllFormFields() {
        val t = FakeTransport(201, """{"capture_id":"c","job_id":"j","status":"pending"}""")
        ParametricSubmission(t).submit("http://h:5173/api/v1", req())
        assertTrue(t.url!!.endsWith("/api/v1/parametric-blocks"))
        assertTrue(t.contentType!!.startsWith("multipart/form-data"))
        val body = t.sent!!
        listOf(
            "name=\"system\"", "name=\"kind\"", "name=\"units_x\"",
            "name=\"units_y\"", "name=\"raw_measurements_mm\"",
        ).forEach { assertTrue("missing $it", body.contains(it)) }
        assertTrue(body.contains("feile"))
        assertTrue(body.contains("\"outer_pitch_mm\""))
    }

    @Test fun sendsZeroPhotos() {
        val t = FakeTransport(201, """{"status":"pending"}""")
        ParametricSubmission(t).submit("http://h/api/v1", req())
        assertTrue(!t.sent!!.contains("name=\"photos\""))
    }

    @Test fun success201Parsed() {
        val t = FakeTransport(
            201,
            """
            {"capture_id":"cap1","job_id":"job1","part_id":"feile-brick-2x4",
             "status":"pending","cross_check_warnings":["w1"]}
            """.trimIndent(),
        )
        val out = ParametricSubmission(t).submit("http://h/api/v1", req())
        assertTrue(out is ParametricOutcome.Success)
        val s = out as ParametricOutcome.Success
        assertEquals("cap1", s.result.captureId)
        assertEquals("job1", s.result.jobId)
        assertEquals(listOf("w1"), s.result.crossCheckWarnings)
    }

    @Test fun http422IsFailureWithBody() {
        val t = FakeTransport(422, """{"detail":"raw_measurements_mm.inner_pitch_mm must be > 0"}""")
        val out = ParametricSubmission(t).submit("http://h/api/v1", req())
        assertTrue(out is ParametricOutcome.Failure)
        assertTrue((out as ParametricOutcome.Failure).message.contains("inner_pitch_mm"))
        assertEquals(422, out.code)
    }
}
