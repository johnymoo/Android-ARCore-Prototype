package com.johnymoo.arverify.measure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Focused caliper diagram (style A): draws a simple brick and highlights only
 * the measurement for [code]. TOP view for 1A/1B/③, SIDE view for ②/④.
 * Layout proxy: docs/superpowers/specs/assets/needs-measurement-ui-mockups/measurement-model.html
 */
@Composable
fun MeasurementDiagram(view: DiagramView, code: String, modifier: Modifier = Modifier) {
    val brick = Color(0xFFF1F3F6)
    val brickEdge = Color(0xFFCDD3DC)
    val faded = Color(0xFFEEF1F5)
    val accent = Color(0xFFE8590C)
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width
        val h = size.height
        fun mark(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(accent, Offset(x1, y1), Offset(x2, y2), strokeWidth = 6f)

        if (view == DiagramView.TOP) {
            val left = w * 0.12f
            val right = w * 0.88f
            val top = h * 0.38f
            val bot = h * 0.86f
            drawRect(brick, topLeft = Offset(left, top), size = Size(right - left, bot - top))
            drawRect(brickEdge, topLeft = Offset(left, top), size = Size(right - left, bot - top), style = Stroke(width = 3f))
            val cols = 3
            val r = (right - left) / (cols * 2.4f)
            val cyTop = top + (bot - top) * 0.32f
            val cyBot = top + (bot - top) * 0.72f
            val xs = (0 until cols).map { left + (right - left) * (it + 0.5f) / cols }
            xs.forEach { cx ->
                drawCircle(faded, r, Offset(cx, cyTop)); drawCircle(brickEdge, r, Offset(cx, cyTop), style = Stroke(2f))
                drawCircle(faded, r, Offset(cx, cyBot)); drawCircle(brickEdge, r, Offset(cx, cyBot), style = Stroke(2f))
            }
            when (code) {
                "1A" -> {
                    val y = top * 0.5f
                    mark(xs.first() - r, y, xs.last() + r, y)
                    mark(xs.first() - r, y - 8, xs.first() - r, y + 8)
                    mark(xs.last() + r, y - 8, xs.last() + r, y + 8)
                }
                "1B" -> {
                    mark(xs[0] + r, cyTop, xs[1] - r, cyTop)
                    mark(xs[0] + r, cyTop - 8, xs[0] + r, cyTop + 8)
                    mark(xs[1] - r, cyTop - 8, xs[1] - r, cyTop + 8)
                }
                "③" -> {
                    mark(xs[1] - r, cyBot, xs[1] + r, cyBot)
                    mark(xs[1] - r, cyBot - 8, xs[1] - r, cyBot + 8)
                    mark(xs[1] + r, cyBot - 8, xs[1] + r, cyBot + 8)
                }
            }
        } else {
            val left = w * 0.30f
            val right = w * 0.80f
            val bodyTop = h * 0.42f
            val base = h * 0.86f
            val studTop = h * 0.24f
            val sxs = listOf(left + (right - left) * 0.28f, left + (right - left) * 0.72f)
            sxs.forEach { sx -> drawRect(faded, topLeft = Offset(sx - 14, studTop), size = Size(28f, bodyTop - studTop)) }
            drawRect(brick, topLeft = Offset(left, bodyTop), size = Size(right - left, base - bodyTop))
            drawRect(brickEdge, topLeft = Offset(left, bodyTop), size = Size(right - left, base - bodyTop), style = Stroke(3f))
            when (code) {
                "②" -> {
                    val x = left * 0.6f
                    mark(x, bodyTop, x, base)
                    mark(x - 8, bodyTop, x + 8, bodyTop)
                    mark(x - 8, base, x + 8, base)
                }
                "④" -> {
                    val x = right + (w - right) * 0.5f
                    mark(x, studTop, x, base)
                    mark(x - 8, studTop, x + 8, studTop)
                    mark(x - 8, base, x + 8, base)
                }
            }
        }
    }
}
