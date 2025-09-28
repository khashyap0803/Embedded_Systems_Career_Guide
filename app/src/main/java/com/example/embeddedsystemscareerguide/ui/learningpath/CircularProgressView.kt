package com.example.embeddedsystemscareerguide.ui.learningpath

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.R
import kotlin.math.*

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var maxProgress = 100f
    private var animatedProgress = 0f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = ContextCompat.getColor(context, R.color.slate_700)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }

    private val rect = RectF()
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    private var progressColor = ContextCompat.getColor(context, R.color.emerald_400)
    private var glowColor = Color.parseColor("#4010B981")

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        centerX = w / 2f
        centerY = h / 2f
        radius = (min(w, h) / 2f) - backgroundPaint.strokeWidth

        rect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background circle
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // Draw glow effect
        if (animatedProgress > 0) {
            glowPaint.color = glowColor
            val startAngle = -90f
            val sweepAngle = (animatedProgress / maxProgress) * 360f
            canvas.drawArc(rect, startAngle, sweepAngle, false, glowPaint)
        }

        // Draw progress arc
        if (animatedProgress > 0) {
            progressPaint.color = progressColor
            val startAngle = -90f
            val sweepAngle = (animatedProgress / maxProgress) * 360f
            canvas.drawArc(rect, startAngle, sweepAngle, false, progressPaint)
        }

        // Draw progress text
        val progressText = "${(animatedProgress / maxProgress * 100).toInt()}%"
        val textBounds = Rect()
        textPaint.getTextBounds(progressText, 0, progressText.length, textBounds)

        canvas.drawText(
            progressText,
            centerX,
            centerY + textBounds.height() / 2f,
            textPaint
        )
    }

    fun setProgress(progress: Float, animate: Boolean = true) {
        this.progress = progress.coerceIn(0f, maxProgress)

        if (animate) {
            animateProgressTo(this.progress)
        } else {
            animatedProgress = this.progress
            invalidate()
        }
    }

    fun setProgressColor(color: Int) {
        progressColor = color
        glowColor = Color.argb(64, Color.red(color), Color.green(color), Color.blue(color))
        invalidate()
    }

    fun setMaxProgress(max: Float) {
        maxProgress = max
        invalidate()
    }

    private fun animateProgressTo(targetProgress: Float) {
        ValueAnimator.ofFloat(animatedProgress, targetProgress).apply {
            duration = 1000
            addUpdateListener { animation ->
                animatedProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun pulseEffect() {
        ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 600
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }
            start()
        }
    }
}
