package com.example.embeddedsystemscareerguide.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.R

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.slate_600)
        strokeWidth = 8f
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.indigo_400)
        strokeWidth = 8f
    }

    private var progress = 0f
    private var animatedProgress = 0f
    private val rect = RectF()

    init {
        // Parse custom attributes if needed
        context.theme.obtainStyledAttributes(
            attrs,
            intArrayOf(android.R.attr.progress),
            0, 0
        ).apply {
            try {
                progress = getFloat(0, 0f)
            } finally {
                recycle()
            }
        }
    }

    fun setProgress(newProgress: Int) {
        val normalizedProgress = newProgress.toFloat() / 100f
        animateToProgress(normalizedProgress)
    }

    private fun animateToProgress(targetProgress: Float) {
        ValueAnimator.ofFloat(animatedProgress, targetProgress).apply {
            duration = 800
            addUpdateListener { animator ->
                animatedProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) - backgroundPaint.strokeWidth / 2f

        rect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Draw background circle
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // Draw progress arc
        if (animatedProgress > 0f) {
            val sweepAngle = 360f * animatedProgress
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }
}
