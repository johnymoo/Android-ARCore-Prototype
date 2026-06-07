package com.johnymoo.arverify

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class CaptureWizardLayoutTest {
    @Test fun counterLivesInsideTopChromeToAvoidOverlayingStepText() {
        val root = parseLayout("app/src/main/res/layout/activity_capture_wizard.xml")
        val topChrome = root.findById("@+id/top_chrome")

        assertTrue(topChrome.containsId("@+id/tv_counter"))
    }

    private fun parseLayout(path: String): Element {
        val file = listOf(
            File(path),
            File("..", path),
            File("src/main/res/layout/activity_capture_wizard.xml"),
        ).first { it.exists() }
        val factory = DocumentBuilderFactory.newInstance()
        val document = factory.newDocumentBuilder().parse(file)
        return document.documentElement
    }

    private fun Element.findById(id: String): Element {
        if (getAttribute("android:id") == id) return this
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                runCatching { return child.findById(id) }
            }
        }
        error("Missing $id")
    }

    private fun Element.containsId(id: String): Boolean {
        if (getAttribute("android:id") == id) return true
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.containsId(id)) return true
        }
        return false
    }
}
