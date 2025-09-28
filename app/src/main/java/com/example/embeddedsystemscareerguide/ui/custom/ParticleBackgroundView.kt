package com.example.embeddedsystemscareerguide.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*
import kotlin.random.Random

class ParticleBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var alpha: Float,
        var size: Float
    )

    init {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#4F46E5")
    }

    fun startAnimation() {
        // Initialize particles
        particles.clear()
        repeat(20) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width,
                    y = Random.nextFloat() * height,
                    vx = (Random.nextFloat() - 0.5f) * 2f,
                    vy = (Random.nextFloat() - 0.5f) * 2f,
                    alpha = Random.nextFloat() * 0.5f + 0.1f,
                    size = Random.nextFloat() * 4f + 2f
                )
            )
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE

            addUpdateListener {
                updateParticles()
                invalidate()
            }

            start()
        }
    }

    private fun updateParticles() {
        particles.forEach { particle ->
            particle.x += particle.vx
            particle.y += particle.vy

            // Wrap around screen
            if (particle.x < 0) particle.x = width.toFloat()
            if (particle.x > width) particle.x = 0f
            if (particle.y < 0) particle.y = height.toFloat()
            if (particle.y > height) particle.y = 0f

            // Vary alpha for twinkling effect
            particle.alpha = (sin(System.currentTimeMillis() * 0.001f + particle.x * 0.01f) * 0.3f + 0.2f).toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        particles.forEach { particle ->
            paint.alpha = (particle.alpha * 255).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
