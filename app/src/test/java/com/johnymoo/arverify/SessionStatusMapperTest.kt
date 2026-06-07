package com.johnymoo.arverify

import com.johnymoo.arverify.net.RecognitionStatus
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.session.SessionStatusMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatusMapperTest {
    @Test fun recognizedMapsToRecognized() {
        assertEquals(SessionStatus.RECOGNIZED, SessionStatusMapper.fromRecognition(RecognitionStatus.RECOGNIZED))
    }
    @Test fun needsMeasurementMaps() {
        assertEquals(SessionStatus.NEEDS_MEASUREMENT, SessionStatusMapper.fromRecognition(RecognitionStatus.NEEDS_MEASUREMENT))
    }
    @Test fun unknownStaysPendingUpload() {
        assertEquals(SessionStatus.PENDING_UPLOAD, SessionStatusMapper.fromRecognition(RecognitionStatus.UNKNOWN))
    }
}
