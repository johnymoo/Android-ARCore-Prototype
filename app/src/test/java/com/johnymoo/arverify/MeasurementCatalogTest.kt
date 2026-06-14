package com.johnymoo.arverify

import com.johnymoo.arverify.measure.DiagramView
import com.johnymoo.arverify.measure.MeasurementCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCatalogTest {

    @Test fun canonicalOrderHasFiveFieldsWithCodes() {
        val steps = MeasurementCatalog.stepsFor(emptyList()) // empty backend list -> canonical 5
        assertEquals(5, steps.size)
        assertEquals(
            listOf(
                "outer_pitch_mm", "inner_pitch_mm", "stud_diameter_mm",
                "brick_height_net_mm", "brick_height_total_mm",
            ),
            steps.map { it.key },
        )
        assertEquals(listOf("1A", "1B", "③", "②", "④"), steps.map { it.code })
    }

    @Test fun diagramViewSplitsTopAndSide() {
        val byKey = MeasurementCatalog.stepsFor(emptyList()).associateBy { it.key }
        assertEquals(DiagramView.TOP, byKey["outer_pitch_mm"]!!.view)
        assertEquals(DiagramView.TOP, byKey["inner_pitch_mm"]!!.view)
        assertEquals(DiagramView.TOP, byKey["stud_diameter_mm"]!!.view)
        assertEquals(DiagramView.SIDE, byKey["brick_height_net_mm"]!!.view)
        assertEquals(DiagramView.SIDE, byKey["brick_height_total_mm"]!!.view)
    }

    @Test fun backendOrderIsHonored() {
        val steps = MeasurementCatalog.stepsFor(listOf("stud_diameter_mm", "outer_pitch_mm"))
        assertEquals(listOf("stud_diameter_mm", "outer_pitch_mm"), steps.map { it.key })
        assertEquals("③", steps[0].code)
    }

    @Test fun unknownKeyGetsFallbackStep() {
        val steps = MeasurementCatalog.stepsFor(listOf("mystery_mm"))
        assertEquals(1, steps.size)
        assertEquals("mystery_mm", steps[0].key)
        assertEquals("mystery_mm", steps[0].code) // fallback: code = raw key
        assertTrue(steps[0].label.contains("mystery_mm"))
    }

    @Test fun systemsAndKindsMirrorSchema() {
        assertEquals(
            listOf("duplo", "lego", "feile", "generic"),
            MeasurementCatalog.SYSTEMS.map { it.value },
        )
        assertEquals(
            listOf("brick", "plate", "tile", "slope"),
            MeasurementCatalog.KINDS.map { it.value },
        )
        assertEquals("费乐 (FEILE)", MeasurementCatalog.SYSTEMS.first { it.value == "feile" }.label)
        assertEquals(1, MeasurementCatalog.MIN_UNITS)
        assertEquals(16, MeasurementCatalog.MAX_UNITS)
    }
}
