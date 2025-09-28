package com.example.embeddedsystemscareerguide.ui.learningpath

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.R

class PathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cyan_400)
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cyan_300)
        strokeWidth = 20f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 100
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    private val path = Path()
    private val glowPath = Path()
    private var pathPoints = listOf<Pair<Float, Float>>()

    fun setPath(points: List<Pair<Float, Float>>) {
        pathPoints = points
        createSmoothPath()
        invalidate()
    }

    private fun createSmoothPath() {
        if (pathPoints.size < 2) return

        path.reset()
        glowPath.reset()

        val firstPoint = pathPoints.first()
        path.moveTo(firstPoint.first, firstPoint.second)
        glowPath.moveTo(firstPoint.first, firstPoint.second)

        for (i in 1 until pathPoints.size) {
            val currentPoint = pathPoints[i]
            val previousPoint = pathPoints[i - 1]

            // Create smooth curves between points
            val controlPointX = (previousPoint.first + currentPoint.first) / 2f
            val controlPointY = previousPoint.second

            path.quadTo(controlPointX, controlPointY, currentPoint.first, currentPoint.second)
            glowPath.quadTo(controlPointX, controlPointY, currentPoint.first, currentPoint.second)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pathPoints.isNotEmpty()) {
            // Draw glow effect first
            canvas.drawPath(glowPath, glowPaint)
            // Draw main path
            canvas.drawPath(path, pathPaint)
        }
    }
}
