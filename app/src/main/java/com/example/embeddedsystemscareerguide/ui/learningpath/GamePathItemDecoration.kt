package com.example.embeddedsystemscareerguide.ui.learningpath

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView

class GamePathItemDecoration(private val context: Context) : RecyclerView.ItemDecoration() {

    private val pathPaint = Paint().apply {
        color = 0xFF4F46E5.toInt() // indigo_600
        strokeWidth = 8f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        isAntiAlias = true
    }

    private val dotPaint = Paint().apply {
        color = 0xFF6366F1.toInt() // indigo_500
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)

        val childCount = parent.childCount
        if (childCount <= 1) return

        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            val nextChild = parent.getChildAt(i + 1)

            val startX = child.x + child.width / 2
            val startY = child.y + child.height
            val endX = nextChild.x + nextChild.width / 2
            val endY = nextChild.y

            // Draw curved path between stages
            val path = Path()
            path.moveTo(startX, startY)

            val controlX = (startX + endX) / 2
            val controlY = (startY + endY) / 2

            path.quadTo(controlX + 50f, controlY, endX, endY)
            c.drawPath(path, pathPaint)

            // Draw dots along the path
            val dotRadius = 6f
            c.drawCircle(startX, startY + 20f, dotRadius, dotPaint)
            c.drawCircle(controlX, controlY, dotRadius, dotPaint)
        }
    }
}
