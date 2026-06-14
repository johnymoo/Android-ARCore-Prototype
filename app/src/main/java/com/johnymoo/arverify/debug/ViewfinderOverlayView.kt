package com.johnymoo.arverify.debug

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class ViewfinderOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.SQUARE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }

    private val topInsetPx = (96f * resources.displayMetrics.density).toInt()
    private val bottomInsetPx = (112f * resources.displayMetrics.density).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val frame = ViewfinderGeometry.centeredFrame(width, height, topInsetPx, bottomInsetPx)
        val rect = RectF(frame.left.toFloat(), frame.top.toFloat(), frame.right.toFloat(), frame.bottom.toFloat())

        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, shade)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, shade)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, shade)
        canvas.drawRect(rect, stroke)

        val c = 26f * resources.displayMetrics.density
        canvas.drawLine(rect.left, rect.top, rect.left + c, rect.top, corner)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + c, corner)
        canvas.drawLine(rect.right, rect.top, rect.right - c, rect.top, corner)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + c, corner)
        canvas.drawLine(rect.left, rect.bottom, rect.left + c, rect.bottom, corner)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - c, corner)
        canvas.drawLine(rect.right, rect.bottom, rect.right - c, rect.bottom, corner)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - c, corner)

        canvas.drawText("保存/识别取景框", rect.left + 10f * resources.displayMetrics.density, rect.top + 34f, label)
    }
}
