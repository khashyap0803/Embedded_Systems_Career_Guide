package com.example.embeddedsystemscareerguide.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.R
import kotlin.math.*

class GamePathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.indigo_400)
        strokeWidth = 12f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.indigo_200)
        strokeWidth = 20f
        style = Paint.Style.STROKE
        alpha = 80
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.OUTER)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.emerald_400)
        strokeWidth = 12f
        style = Paint.Style.STROKE
    }

    private val path = Path()
    private var progress = 0f
    private var pathMeasure = PathMeasure()
    private var animatedProgress = 0f

    fun setProgress(newProgress: Float) {
        progress = newProgress.coerceIn(0f, 1f)
        animateProgress()
    }

    private fun animateProgress() {
        ValueAnimator.ofFloat(animatedProgress, progress).apply {
            duration = 1000
            addUpdateListener { animator ->
                animatedProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        // Create a winding path from bottom to top
        createWindingPath()

        // Draw glow effect
        canvas.drawPath(path, glowPaint)

        // Draw main path
        canvas.drawPath(path, pathPaint)

        // Draw progress overlay
        if (animatedProgress > 0f) {
            val progressPath = Path()
            pathMeasure.setPath(path, false)
            pathMeasure.getSegment(0f, pathMeasure.length * animatedProgress, progressPath, true)
            canvas.drawPath(progressPath, progressPaint)
        }
    }

    private fun createWindingPath() {
        path.reset()

        val centerX = width / 2f
        val startY = height * 0.9f
        val endY = height * 0.1f
        val amplitude = width * 0.15f

        path.moveTo(centerX, startY)

        // Create a smooth winding path
        var currentY = startY
        val segments = 8
        val segmentHeight = (startY - endY) / segments

        for (i in 0 until segments) {
            val nextY = currentY - segmentHeight
            val direction = if (i % 2 == 0) 1f else -1f
            val controlX = centerX + (amplitude * direction)

            path.quadTo(
                controlX, currentY - segmentHeight / 2f,
                centerX - (direction * amplitude * 0.3f), nextY
            )

            currentY = nextY
        }

        pathMeasure.setPath(path, false)
    }
}
