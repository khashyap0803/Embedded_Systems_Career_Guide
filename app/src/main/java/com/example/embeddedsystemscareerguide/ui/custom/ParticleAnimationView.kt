package com.example.embeddedsystemscareerguide.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.R
import kotlin.math.*
import kotlin.random.Random

class ParticleAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    data class Particle(
        var x: Float,
        var y: Float,
        val vx: Float,
        val vy: Float,
        val size: Float,
        val color: Int,
        val alpha: Int
    )

    init {
        // Initialize particles
        repeat(15) {
            particles.add(createParticle())
        }
    }

    private fun createParticle(): Particle {
        return Particle(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            vx = (Random.nextFloat() - 0.5f) * 2f,
            vy = (Random.nextFloat() - 0.5f) * 2f,
            size = Random.nextFloat() * 6f + 2f,
            color = when (Random.nextInt(4)) {
                0 -> ContextCompat.getColor(context, R.color.indigo_400)
                1 -> ContextCompat.getColor(context, R.color.emerald_400)
                2 -> ContextCompat.getColor(context, R.color.amber_400)
                else -> ContextCompat.getColor(context, R.color.slate_400)
            },
            alpha = Random.nextInt(100) + 50
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        particles.forEach { particle ->
            paint.color = particle.color
            paint.alpha = particle.alpha
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
    }

    private fun updateParticles() {
        particles.forEach { particle ->
            particle.x += particle.vx
            particle.y += particle.vy

            // Wrap around screen edges
            if (particle.x < 0) particle.x = width.toFloat()
            if (particle.x > width) particle.x = 0f
            if (particle.y < 0) particle.y = height.toFloat()
            if (particle.y > height) particle.y = 0f
        }
    }

    fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                updateParticles()
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
