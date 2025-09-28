package com.example.embeddedsystemscareerguide.ui.learningpath

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class SparkleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val sparkles = mutableListOf<Sparkle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    private val sparkleColors = intArrayOf(
        Color.parseColor("#FFD700"), // Gold
        Color.parseColor("#FFF700"), // Bright Yellow
        Color.parseColor("#FF6B6B"), // Light Red
        Color.parseColor("#4ECDC4"), // Teal
        Color.parseColor("#45B7D1"), // Sky Blue
        Color.parseColor("#96CEB4"), // Mint
        Color.parseColor("#FFEAA7")  // Light Yellow
    )

    data class Sparkle(
        val startX: Float,
        val startY: Float,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val maxSize: Float,
        var size: Float = 0f,
        var alpha: Float = 255f,
        val color: Int,
        var rotation: Float = 0f,
        var lifetime: Float = 1f,
        val maxLifetime: Float = 1f,
        val shape: SparkleShape = SparkleShape.STAR
    )

    enum class SparkleShape {
        STAR, DIAMOND, PLUS, HEART, CIRCLE
    }

    fun triggerSparkles(centerX: Float, centerY: Float, intensity: Int = 20) {
        sparkles.clear()

        repeat(intensity) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = Random.nextFloat() * 200f + 100f
            val distance = Random.nextFloat() * 150f + 50f

            sparkles.add(
                Sparkle(
                    startX = centerX,
                    startY = centerY,
                    x = centerX,
                    y = centerY,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    maxSize = Random.nextFloat() * 16f + 8f,
                    color = sparkleColors[Random.nextInt(sparkleColors.size)],
                    rotation = Random.nextFloat() * 360f,
                    lifetime = 1f,
                    maxLifetime = Random.nextFloat() * 1.5f + 1f,
                    shape = SparkleShape.values()[Random.nextInt(SparkleShape.values().size)]
                )
            )
        }

        startSparkleAnimation()
    }

    private fun startSparkleAnimation() {
        animator?.cancel()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float

                sparkles.forEach { sparkle ->
                    val normalizedLifetime = progress / sparkle.maxLifetime

                    if (normalizedLifetime <= 1f) {
                        // Update position
                        sparkle.x = sparkle.startX + sparkle.vx * progress
                        sparkle.y = sparkle.startY + sparkle.vy * progress + (progress * progress * 200f) // Gravity effect

                        // Update size (grow then shrink)
                        sparkle.size = if (normalizedLifetime < 0.3f) {
                            sparkle.maxSize * (normalizedLifetime / 0.3f)
                        } else {
                            sparkle.maxSize * (1f - (normalizedLifetime - 0.3f) / 0.7f)
                        }

                        // Update alpha (fade out)
                        sparkle.alpha = (255f * (1f - normalizedLifetime)).coerceAtLeast(0f)

                        // Update rotation
                        sparkle.rotation += 5f

                        sparkle.lifetime = normalizedLifetime
                    }
                }

                invalidate()

                // Remove expired sparkles
                sparkles.removeAll { it.lifetime >= 1f }
            }

            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        sparkles.filter { it.lifetime < 1f }.forEach { sparkle ->
            paint.color = sparkle.color
            paint.alpha = sparkle.alpha.toInt()

            canvas.save()
            canvas.translate(sparkle.x, sparkle.y)
            canvas.rotate(sparkle.rotation)

            when (sparkle.shape) {
                SparkleShape.STAR -> drawStar(canvas, sparkle.size)
                SparkleShape.DIAMOND -> drawDiamond(canvas, sparkle.size)
                SparkleShape.PLUS -> drawPlus(canvas, sparkle.size)
                SparkleShape.HEART -> drawHeart(canvas, sparkle.size)
                SparkleShape.CIRCLE -> canvas.drawCircle(0f, 0f, sparkle.size, paint)
            }

            canvas.restore()
        }
    }

    private fun drawStar(canvas: Canvas, size: Float) {
        val path = Path()
        val outerRadius = size
        val innerRadius = size * 0.4f
        val numPoints = 5

        for (i in 0 until numPoints * 2) {
            val angle = (i * PI / numPoints).toFloat()
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val x = cos(angle) * radius
            val y = sin(angle) * radius

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawDiamond(canvas: Canvas, size: Float) {
        val path = Path()
        path.moveTo(0f, -size)
        path.lineTo(size, 0f)
        path.lineTo(0f, size)
        path.lineTo(-size, 0f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawPlus(canvas: Canvas, size: Float) {
        val thickness = size * 0.3f
        canvas.drawRect(-thickness, -size, thickness, size, paint)
        canvas.drawRect(-size, -thickness, size, thickness, paint)
    }

    private fun drawHeart(canvas: Canvas, size: Float) {
        val path = Path()
        val width = size * 1.2f
        val height = size

        path.moveTo(0f, height * 0.3f)

        // Left curve
        path.cubicTo(
            -width * 0.5f, -height * 0.1f,
            -width * 0.5f, height * 0.3f,
            0f, height * 0.7f
        )

        // Right curve
        path.cubicTo(
            width * 0.5f, height * 0.3f,
            width * 0.5f, -height * 0.1f,
            0f, height * 0.3f
        )

        canvas.drawPath(path, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    fun clear() {
        sparkles.clear()
        animator?.cancel()
        invalidate()
    }
}
