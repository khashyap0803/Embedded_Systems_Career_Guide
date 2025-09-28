package com.example.embeddedsystemscareerguide.ui.learningpath

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class ParticleAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animationRunning = false

    private val colors = intArrayOf(
        Color.parseColor("#3B82F6"), // Blue
        Color.parseColor("#10B981"), // Emerald
        Color.parseColor("#8B5CF6"), // Purple
        Color.parseColor("#F59E0B"), // Amber
        Color.parseColor("#EF4444")  // Red
    )

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var color: Int,
        var alpha: Float = 255f,
        var rotation: Float = 0f,
        var rotationSpeed: Float = 0f,
        val shape: ParticleShape = ParticleShape.CIRCLE
    )

    enum class ParticleShape {
        CIRCLE, TRIANGLE, SQUARE, DIAMOND
    }

    init {
        initializeParticles()
        startAnimation()
    }

    private fun initializeParticles() {
        particles.clear()
        val particleCount = 15

        repeat(particleCount) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width,
                    y = Random.nextFloat() * height,
                    vx = (Random.nextFloat() - 0.5f) * 2f,
                    vy = (Random.nextFloat() - 0.5f) * 2f,
                    size = Random.nextFloat() * 8f + 4f,
                    color = colors[Random.nextInt(colors.size)],
                    alpha = Random.nextFloat() * 100f + 50f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 4f,
                    shape = ParticleShape.values()[Random.nextInt(ParticleShape.values().size)]
                )
            )
        }
    }

    private fun startAnimation() {
        if (!animationRunning) {
            animationRunning = true
            post(animationRunnable)
        }
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (animationRunning) {
                updateParticles()
                invalidate()
                postDelayed(this, 16) // ~60 FPS
            }
        }
    }

    private fun updateParticles() {
        particles.forEach { particle ->
            // Update position
            particle.x += particle.vx
            particle.y += particle.vy

            // Update rotation
            particle.rotation += particle.rotationSpeed

            // Wrap around screen edges
            if (particle.x < -particle.size) particle.x = width + particle.size
            if (particle.x > width + particle.size) particle.x = -particle.size
            if (particle.y < -particle.size) particle.y = height + particle.size
            if (particle.y > height + particle.size) particle.y = -particle.size

            // Pulse alpha
            particle.alpha = (sin(System.currentTimeMillis() * 0.002f + particle.x * 0.01f) * 30f + 80f).coerceIn(30f, 120f)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (particles.isEmpty() || (oldw != w || oldh != h)) {
            initializeParticles()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        particles.forEach { particle ->
            paint.color = particle.color
            paint.alpha = particle.alpha.toInt()

            canvas.save()
            canvas.translate(particle.x, particle.y)
            canvas.rotate(particle.rotation)

            when (particle.shape) {
                ParticleShape.CIRCLE -> {
                    canvas.drawCircle(0f, 0f, particle.size, paint)
                }
                ParticleShape.TRIANGLE -> {
                    drawTriangle(canvas, particle.size)
                }
                ParticleShape.SQUARE -> {
                    canvas.drawRect(-particle.size, -particle.size, particle.size, particle.size, paint)
                }
                ParticleShape.DIAMOND -> {
                    drawDiamond(canvas, particle.size)
                }
            }

            canvas.restore()
        }
    }

    private fun drawTriangle(canvas: Canvas, size: Float) {
        val path = Path()
        path.moveTo(0f, -size)
        path.lineTo(-size * 0.866f, size * 0.5f)
        path.lineTo(size * 0.866f, size * 0.5f)
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationRunning = false
    }

    fun pauseAnimation() {
        animationRunning = false
    }

    fun resumeAnimation() {
        startAnimation()
    }
}
