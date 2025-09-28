package com.example.embeddedsystemscareerguide.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class GamePathItemDecoration(private val context: Context) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F46E5") // indigo_600
        strokeWidth = 8f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val completedPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10b981") // emerald_500
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    override fun onDrawOver(canvas: Canvas, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
        super.onDrawOver(canvas, parent, state)

        // Draw connecting path between stages
        val childCount = parent.childCount
        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            val nextChild = parent.getChildAt(i + 1)

            val startX = child.x + child.width / 2
            val startY = child.y + child.height
            val endX = nextChild.x + nextChild.width / 2
            val endY = nextChild.y

            // Use completed path paint for unlocked stages
            val paint = if (i < 2) completedPathPaint else pathPaint

            canvas.drawLine(startX, startY, endX, endY, paint)
        }
    }
}
