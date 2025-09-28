package com.example.embeddedsystemscareerguide.ui.custom

import android.animation.ValueAnimator
import android.animation.Animator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.R
import kotlin.math.*
import kotlin.random.Random

class SparkleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val sparkles = mutableListOf<Sparkle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    data class Sparkle(
        val startX: Float,
        val startY: Float,
        var x: Float,
        var y: Float,
        val targetX: Float,
        val targetY: Float,
        var alpha: Float,
        var scale: Float,
        val color: Int,
        val rotationSpeed: Float,
        var rotation: Float = 0f
    )

    fun startSparkleAnimation(centerX: Float, centerY: Float, onComplete: () -> Unit) {
        // Clear existing sparkles
        sparkles.clear()

        // Create sparkles in a burst pattern
        repeat(12) { i ->
            val angle = (i * 30f) * (PI / 180f).toFloat()
            val distance = 100f + Random.nextFloat() * 150f
            val targetX = centerX + cos(angle) * distance
            val targetY = centerY + sin(angle) * distance

            sparkles.add(
                Sparkle(
                    startX = centerX,
                    startY = centerY,
                    x = centerX,
                    y = centerY,
                    targetX = targetX,
                    targetY = targetY,
                    alpha = 1f,
                    scale = 0f,
                    color = when (i % 3) {
                        0 -> ContextCompat.getColor(context, R.color.amber_400)
                        1 -> ContextCompat.getColor(context, R.color.emerald_400)
                        else -> ContextCompat.getColor(context, R.color.indigo_400)
                    },
                    rotationSpeed = Random.nextFloat() * 10f + 5f
                )
            )
        }

        // Animate sparkles
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                val progress = valueAnimator.animatedValue as Float

                sparkles.forEach { sparkle ->
                    // Movement
                    sparkle.x = sparkle.startX + (sparkle.targetX - sparkle.startX) * progress
                    sparkle.y = sparkle.startY + (sparkle.targetY - sparkle.startY) * progress

                    // Scale (grow then shrink)
                    sparkle.scale = if (progress < 0.3f) {
                        progress / 0.3f
                    } else {
                        1f - ((progress - 0.3f) / 0.7f)
                    }

                    // Alpha fade out
                    sparkle.alpha = 1f - progress

                    // Rotation
                    sparkle.rotation += sparkle.rotationSpeed
                }

                invalidate()
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        sparkles.forEach { sparkle ->
            paint.color = sparkle.color
            paint.alpha = (sparkle.alpha * 255).toInt()

            canvas.save()
            canvas.translate(sparkle.x, sparkle.y)
            canvas.rotate(sparkle.rotation)
            canvas.scale(sparkle.scale, sparkle.scale)

            // Draw sparkle as a star shape
            drawStar(canvas, 15f, paint)

            canvas.restore()
        }
    }

    private fun drawStar(canvas: Canvas, radius: Float, paint: Paint) {
        val path = Path()
        val outerRadius = radius
        val innerRadius = radius * 0.5f

        for (i in 0 until 10) {
            val angle = (i * 36f - 90f) * (PI / 180f).toFloat()
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val x = cos(angle) * r
            val y = sin(angle) * r

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        canvas.drawPath(path, paint)
    }
}
