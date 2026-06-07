package com.johnymoo.arverify

import com.johnymoo.arverify.net.RecognitionResultModel
import com.johnymoo.arverify.net.RecognitionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionResultModelTest {
    @Test fun parsesRecognized() {
        val json = """
            {"capture_id":"c1","job_id":"j1",
             "recognized":{"system":"feile","kind":"brick","units_x":2,"units_y":4,
                           "pitch_mm":16.1,"confidence":0.93},
             "status":"recognized","needs_measurement":null}
        """.trimIndent()
        val r = RecognitionResultModel.parse(json)
        assertEquals(RecognitionStatus.RECOGNIZED, r.status)
        assertEquals("c1", r.captureId)
        assertEquals("j1", r.jobId)
        assertEquals("feile", r.recognized?.system)
        assertEquals("brick", r.recognized?.kind)
        assertEquals(2, r.recognized?.unitsX)
        assertEquals(4, r.recognized?.unitsY)
        assertEquals(16.1, r.recognized?.pitchMm!!, 1e-9)
        assertEquals(0.93, r.recognized?.confidence!!, 1e-9)
        assertNull(r.needsMeasurement)
    }

    @Test fun parsesNeedsMeasurement() {
        val json = """
            {"capture_id":"c2","job_id":"j2",
             "recognized":{"system":"unknown","kind":"brick"},
             "status":"needs_measurement",
             "needs_measurement":{"guidance":"卡住可夹位置",
               "fields":[{"key":"length","label":"长","unit":"mm"},
                         {"key":"width","label":"宽","unit":"mm"}]}}
        """.trimIndent()
        val r = RecognitionResultModel.parse(json)
        assertEquals(RecognitionStatus.NEEDS_MEASUREMENT, r.status)
        assertEquals("unknown", r.recognized?.system)
        assertNull(r.recognized?.unitsX) // partial recognized tolerated
        assertEquals("卡住可夹位置", r.needsMeasurement?.guidance)
        assertEquals(2, r.needsMeasurement?.fields?.size)
        assertEquals("length", r.needsMeasurement?.fields?.get(0)?.key)
        assertEquals("宽", r.needsMeasurement?.fields?.get(1)?.label)
    }

    @Test fun unknownStatusAndMalformedAreSafe() {
        val r = RecognitionResultModel.parse("""{"status":"weird"}""")
        assertEquals(RecognitionStatus.UNKNOWN, r.status)
        assertNull(r.recognized)
        assertNull(r.needsMeasurement)
        // Non-JSON input must not throw:
        val r2 = RecognitionResultModel.parse("not json at all")
        assertEquals(RecognitionStatus.UNKNOWN, r2.status)
    }

    @Test fun missingFieldsArrayYieldsEmptyList() {
        val r = RecognitionResultModel.parse(
            """{"status":"needs_measurement","needs_measurement":{"guidance":"g"}}"""
        )
        assertEquals(RecognitionStatus.NEEDS_MEASUREMENT, r.status)
        assertTrue(r.needsMeasurement?.fields?.isEmpty() == true)
        assertEquals("g", r.needsMeasurement?.guidance)
    }
}
