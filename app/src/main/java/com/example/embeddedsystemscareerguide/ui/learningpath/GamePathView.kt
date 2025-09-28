package com.example.embeddedsystemscareerguide.ui.learningpath

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.R

class GamePathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = ContextCompat.getColor(context, android.R.color.holo_blue_light)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Basic path drawing implementation
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, pathPaint)
    }
}
