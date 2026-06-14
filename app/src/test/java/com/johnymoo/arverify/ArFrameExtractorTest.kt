package com.johnymoo.arverify

import com.johnymoo.arverify.capture.ArFrameExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class ArFrameExtractorTest {
    @Test fun depth16MillimetersMasksConfidenceBits() {
        assertEquals(7_000, ArFrameExtractor.depth16Millimeters(7_000))
        assertEquals(240, ArFrameExtractor.depth16Millimeters(0xE000 or 240))
        assertEquals(700, ArFrameExtractor.depth16Millimeters(0xA000 or 700))
        assertEquals(0x1FFF, ArFrameExtractor.depth16Millimeters(0xFFFF))
    }
}
