package com.example.embeddedsystemscareerguide.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.embeddedsystemscareerguide.R
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * A draggable floating action button that opens a radial ("pie") menu.
 *
 * Interaction model:
 *  - **Press and hold** the button: the menu blooms outward. Without lifting,
 *    slide onto an option - whichever option the finger is over enlarges and
 *    highlights. Lift on an option to run it; lift anywhere else to cancel.
 *  - **Double-tap then hold**: the button itself is picked up and follows the
 *    finger. On release it snaps to the nearest screen edge.
 *
 * The menu geometry adapts to wherever the button currently sits. Rather than
 * special-casing "corner" vs "edge" vs "middle", every candidate angle is
 * tested for whether an option drawn there would actually fit on screen, and
 * the options are distributed across the largest usable arc. That produces a
 * full circle in open space, a semicircle against an edge, and a quarter arc
 * in a corner, all from the same code path. Options that cannot fit on the
 * first ring spill onto a wider ring further out so nothing is ever hidden.
 *
 * This View deliberately spans the whole screen so that a single continuous
 * gesture can be tracked without handing touches between views. Touches that
 * do not start on the button are rejected from `onTouchEvent`, which lets the
 * content underneath receive them normally.
 */
class RadialFabMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Action(
        val id: Int,
        val label: String,
        val iconRes: Int
    )

    /** Invoked when the user lifts their finger on an option. */
    var onActionSelected: ((Action) -> Unit)? = null

    /**
     * Fallback for accessibility: when touch exploration is on, the slide-to-pick
     * gesture is unusable, so the host shows a plain list dialog instead.
     */
    var onAccessibleMenuRequested: ((List<Action>) -> Unit)? = null

    var actions: List<Action> = emptyList()
        set(value) {
            field = value
            iconCache.clear()
            placements = emptyList()
            invalidate()
        }

    /**
     * Extra top inset for chrome the View cannot discover itself (the app bar).
     * Keeps the button from being dragged underneath the toolbar.
     */
    var extraTopInset: Int = 0
        set(value) {
            field = value
            if (width > 0 && height > 0) {
                clampFabPosition()
                invalidate()
            }
        }

    // ---- geometry -----------------------------------------------------------

    private val fabRadius = dp(28f)
    private val itemRadius = dp(26f)
    private val ringGap = dp(44f)          // clearance between FAB edge and ring 1
    private val ringSpacing = dp(16f)      // clearance between consecutive rings
    private val itemGap = dp(10f)          // minimum clearance between two options
    private val edgeMargin = dp(12f)

    private var fabCx = 0f
    private var fabCy = 0f

    // Stored as a fraction of the view size so the button lands in the same
    // relative spot after a rotation or on a different screen size.
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0

    // ---- interaction state --------------------------------------------------

    private enum class State { IDLE, MENU_OPEN, ARMED_FOR_DRAG, DRAGGING }

    private var state = State.IDLE
    private var hoveredIndex = -1
    private var grabDx = 0f
    private var grabDy = 0f
    private var downX = 0f
    private var downY = 0f
    private var downAtMs = 0L
    private var lastTapUpMs = 0L

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong()

    private val enterDragRunnable = Runnable {
        if (state == State.ARMED_FOR_DRAG) {
            state = State.DRAGGING
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    }

    /** 0 = collapsed into the FAB, 1 = fully bloomed. */
    private var expansion = 0f
    private var expansionAnimator: ValueAnimator? = null

    private class Placement(
        val action: Action,
        val cx: Float,
        val cy: Float,
        var scale: Float = 1f,
        var targetScale: Float = 1f
    )

    private var placements: List<Placement> = emptyList()

    // ---- paint --------------------------------------------------------------

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val itemStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val colorAccent = themeColor(R.color.indigo_600, 0xFF4F46E5.toInt())
    private val colorSurface = themeColor(R.color.slate_800, 0xFF1E293B.toInt())
    private val colorOnSurface = themeColor(R.color.text_primary, 0xFFF1F5F9.toInt())

    private val iconCache = mutableMapOf<Int, Drawable>()
    private val labelRect = RectF()

    init {
        // Nothing is drawn behind the FAB until the menu opens, so the view is
        // transparent and must not steal touches it does not need.
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false

        scrimPaint.color = Color.BLACK
        fabPaint.color = colorAccent
        labelBgPaint.color = colorSurface

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            insetLeft = bars.left
            insetTop = bars.top
            insetRight = bars.right
            insetBottom = bars.bottom
            if (width > 0 && height > 0) clampFabPosition()
            insets
        }
    }

    // ---- layout -------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        val fx = prefs.getFloat(KEY_FRACTION_X, DEFAULT_FRACTION_X)
        val fy = prefs.getFloat(KEY_FRACTION_Y, DEFAULT_FRACTION_Y)
        fabCx = fx * w
        fabCy = fy * h
        clampFabPosition()
    }

    private fun minX() = insetLeft + fabRadius + edgeMargin
    private fun maxX() = width - insetRight - fabRadius - edgeMargin
    private fun minY() = insetTop + extraTopInset + fabRadius + edgeMargin
    private fun maxY() = height - insetBottom - fabRadius - edgeMargin

    private fun clampFabPosition() {
        if (width == 0 || height == 0) return
        // On very small screens the clamp bounds can invert; fall back to centring.
        fabCx = if (minX() <= maxX()) fabCx.coerceIn(minX(), maxX()) else width / 2f
        fabCy = if (minY() <= maxY()) fabCy.coerceIn(minY(), maxY()) else height / 2f
        placements = emptyList()
    }

    private fun persistFabPosition() {
        if (width == 0 || height == 0) return
        prefs.edit()
            .putFloat(KEY_FRACTION_X, fabCx / width)
            .putFloat(KEY_FRACTION_Y, fabCy / height)
            .apply()
    }

    // ---- menu geometry ------------------------------------------------------

    /**
     * Distributes [actions] across as many concentric rings as needed, using
     * only angles where an option would be fully on screen.
     */
    private fun computePlacements() {
        if (actions.isEmpty()) {
            placements = emptyList()
            return
        }

        val result = mutableListOf<Placement>()
        val remaining = ArrayDeque(actions)
        var radius = fabRadius + itemRadius + ringGap
        var ring = 0

        while (remaining.isNotEmpty() && ring < MAX_RINGS) {
            val arc = largestValidArc(radius)
            if (arc == null) {
                // No angle on this ring fits; try further out.
                radius += itemRadius * 2 + ringSpacing
                ring++
                continue
            }

            val (startDeg, sweepDeg) = arc
            val minSep = minAngularSeparation(radius)
            val isFullCircle = sweepDeg >= FULL_CIRCLE_EPSILON

            val capacity = if (isFullCircle) {
                floor(360f / minSep).toInt()
            } else {
                floor(sweepDeg / minSep).toInt() + 1
            }.coerceAtLeast(1)

            val take = min(capacity, remaining.size)
            val batch = (0 until take).map { remaining.removeFirst() }

            batch.forEachIndexed { i, action ->
                val deg = when {
                    isFullCircle -> startDeg + i * (360f / take)
                    take == 1 -> startDeg + sweepDeg / 2f
                    else -> startDeg + i * (sweepDeg / (take - 1))
                }
                val rad = Math.toRadians(deg.toDouble())
                result += Placement(
                    action = action,
                    cx = fabCx + radius * cos(rad).toFloat(),
                    cy = fabCy + radius * sin(rad).toFloat()
                )
            }

            radius += itemRadius * 2 + ringSpacing
            ring++
        }

        // Safety net: if the screen is so constrained that nothing placed,
        // stack whatever is left directly above the button rather than
        // silently dropping actions the user asked for.
        remaining.forEachIndexed { i, action ->
            result += Placement(
                action = action,
                cx = fabCx,
                cy = fabCy - (fabRadius + itemRadius + ringGap) - i * (itemRadius * 2)
            )
        }

        placements = result
    }

    /** Smallest angle between two option centres at [radius] that avoids overlap. */
    private fun minAngularSeparation(radius: Float): Float {
        val half = (itemRadius + itemGap / 2f) / radius
        if (half >= 1f) return 180f
        return Math.toDegrees(2.0 * asin(half.toDouble())).toFloat()
    }

    /**
     * Returns `(startDeg, sweepDeg)` for the longest run of angles at [radius]
     * where an option would sit fully on screen, or null if none do.
     */
    private fun largestValidArc(radius: Float): Pair<Float, Float>? {
        val pad = itemRadius + dp(4f)
        val left = insetLeft + pad
        val right = width - insetRight - pad
        val top = insetTop + extraTopInset + pad
        val bottom = height - insetBottom - pad
        if (left > right || top > bottom) return null

        val valid = BooleanArray(360) { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val x = fabCx + radius * cos(rad).toFloat()
            val y = fabCy + radius * sin(rad).toFloat()
            x in left..right && y in top..bottom
        }

        if (valid.all { it }) return 0f to 360f
        if (valid.none { it }) return null

        // Walk twice around so a run spanning the 359 -> 0 seam is found.
        var bestStart = -1
        var bestLen = 0
        var runStart = -1
        var runLen = 0
        for (i in 0 until 720) {
            if (valid[i % 360]) {
                if (runLen == 0) runStart = i
                runLen++
                if (runLen > bestLen) {
                    bestLen = runLen
                    bestStart = runStart
                }
            } else {
                runLen = 0
            }
        }
        if (bestLen == 0) return null
        // A run cannot exceed the full circle.
        val len = min(bestLen, 360)
        return (bestStart % 360).toFloat() to (len - 1).toFloat()
    }

    // ---- touch --------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isOnFab(x, y)) return false

                parent?.requestDisallowInterceptTouchEvent(true)
                downX = x
                downY = y
                downAtMs = System.currentTimeMillis()
                grabDx = fabCx - x
                grabDy = fabCy - y

                if (isTouchExplorationEnabled()) {
                    // Slide-to-pick cannot work under a screen reader.
                    onAccessibleMenuRequested?.invoke(actions)
                    return true
                }

                val isSecondTap = downAtMs - lastTapUpMs <= doubleTapMs
                if (isSecondTap) {
                    state = State.ARMED_FOR_DRAG
                    postDelayed(enterDragRunnable, longPressMs)
                } else {
                    state = State.MENU_OPEN
                    openMenu()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                when (state) {
                    State.MENU_OPEN -> {
                        updateHover(x, y)
                        invalidate()
                    }

                    State.ARMED_FOR_DRAG -> {
                        // Moving before the hold completes means this was a
                        // swipe, not a deliberate pick-up.
                        if (hypot(x - downX, y - downY) > touchSlop) {
                            removeCallbacks(enterDragRunnable)
                            state = State.IDLE
                        }
                    }

                    State.DRAGGING -> {
                        fabCx = x + grabDx
                        fabCy = y + grabDy
                        clampFabPosition()
                        invalidate()
                    }

                    State.IDLE -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(enterDragRunnable)
                when (state) {
                    State.MENU_OPEN -> {
                        val picked = placements.getOrNull(hoveredIndex)?.action
                        val wasQuickTap = hoveredIndex < 0 &&
                            System.currentTimeMillis() - downAtMs < doubleTapMs
                        closeMenu()
                        if (picked != null) {
                            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onActionSelected?.invoke(picked)
                            lastTapUpMs = 0L
                        } else {
                            // Only a quick tap that selected nothing can begin a
                            // double-tap; otherwise a slow cancel would arm drag mode.
                            lastTapUpMs = if (wasQuickTap) System.currentTimeMillis() else 0L
                        }
                    }

                    State.DRAGGING -> {
                        snapToNearestEdge()
                        lastTapUpMs = 0L
                    }

                    State.ARMED_FOR_DRAG, State.IDLE -> {
                        lastTapUpMs = 0L
                    }
                }
                state = State.IDLE
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(enterDragRunnable)
                if (state == State.MENU_OPEN) closeMenu()
                if (state == State.DRAGGING) snapToNearestEdge()
                state = State.IDLE
                lastTapUpMs = 0L
                return true
            }
        }
        return false
    }

    private fun isOnFab(x: Float, y: Float): Boolean =
        hypot(x - fabCx, y - fabCy) <= fabRadius + dp(6f)

    private fun isTouchExplorationEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return am?.isTouchExplorationEnabled == true
    }

    private fun updateHover(x: Float, y: Float) {
        // Ignore the dead zone over the button itself so resting there cancels.
        var newIndex = -1
        if (hypot(x - fabCx, y - fabCy) > fabRadius) {
            var best = Float.MAX_VALUE
            placements.forEachIndexed { i, p ->
                val d = hypot(x - p.cx, y - p.cy)
                if (d < best && d <= itemRadius * HOVER_SLOP) {
                    best = d
                    newIndex = i
                }
            }
        }
        if (newIndex != hoveredIndex) {
            hoveredIndex = newIndex
            placements.forEachIndexed { i, p ->
                p.targetScale = if (i == hoveredIndex) HOVER_SCALE else 1f
            }
            if (newIndex >= 0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun openMenu() {
        computePlacements()
        hoveredIndex = -1
        placements.forEach { it.scale = 1f; it.targetScale = 1f }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        animateExpansion(1f)
    }

    private fun closeMenu() {
        hoveredIndex = -1
        animateExpansion(0f)
    }

    private fun animateExpansion(target: Float) {
        expansionAnimator?.cancel()
        expansionAnimator = ValueAnimator.ofFloat(expansion, target).apply {
            duration = if (target > 0f) 180L else 140L
            addUpdateListener {
                expansion = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun snapToNearestEdge() {
        val toLeft = fabCx - minX()
        val toRight = maxX() - fabCx
        val toTop = fabCy - minY()
        val toBottom = maxY() - fabCy
        val nearest = minOf(toLeft, toRight, toTop, toBottom)

        var targetX = fabCx
        var targetY = fabCy
        when (nearest) {
            toLeft -> targetX = minX()
            toRight -> targetX = maxX()
            toTop -> targetY = minY()
            else -> targetY = maxY()
        }

        val startX = fabCx
        val startY = fabCy
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            addUpdateListener {
                val t = it.animatedValue as Float
                fabCx = startX + (targetX - startX) * t
                fabCy = startY + (targetY - startY) * t
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    clampFabPosition()
                    persistFabPosition()
                }
            })
            start()
        }
    }

    // ---- drawing ------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        var needsAnotherFrame = false

        if (expansion > 0.01f) {
            scrimPaint.alpha = (SCRIM_ALPHA * expansion).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

            placements.forEachIndexed { i, p ->
                // Ease each option out from under the button.
                val cx = fabCx + (p.cx - fabCx) * expansion
                val cy = fabCy + (p.cy - fabCy) * expansion

                p.scale += (p.targetScale - p.scale) * SCALE_LERP
                if (abs(p.targetScale - p.scale) > 0.005f) needsAnotherFrame = true

                val r = itemRadius * p.scale * expansion
                val hovered = i == hoveredIndex

                itemPaint.color = if (hovered) colorAccent else colorSurface
                itemPaint.alpha = (255 * expansion).toInt()
                canvas.drawCircle(cx, cy, r, itemPaint)

                itemStrokePaint.color = if (hovered) colorOnSurface else colorAccent
                itemStrokePaint.alpha = (255 * expansion).toInt()
                canvas.drawCircle(cx, cy, r, itemStrokePaint)

                val icon = iconFor(p.action.iconRes)
                val half = (r * ICON_RATIO).toInt()
                if (half > 0) {
                    icon.setBounds(
                        (cx - half).toInt(), (cy - half).toInt(),
                        (cx + half).toInt(), (cy + half).toInt()
                    )
                    icon.alpha = (255 * expansion).toInt()
                    icon.draw(canvas)
                }

                if (hovered && expansion > 0.6f) {
                    drawLabel(canvas, p.action.label, cx, cy + r + dp(6f))
                }
            }
        }

        // The button itself is always visible.
        canvas.drawCircle(fabCx, fabCy, fabRadius, fabPaint)
        val fabIcon = iconFor(R.drawable.ic_add)
        val fabHalf = (fabRadius * ICON_RATIO).toInt()
        fabIcon.setBounds(
            (fabCx - fabHalf).toInt(), (fabCy - fabHalf).toInt(),
            (fabCx + fabHalf).toInt(), (fabCy + fabHalf).toInt()
        )
        fabIcon.alpha = 255
        canvas.save()
        // Spin the plus into a cross as the menu opens.
        canvas.rotate(45f * expansion, fabCx, fabCy)
        fabIcon.draw(canvas)
        canvas.restore()

        if (needsAnotherFrame) invalidate()
    }

    private fun drawLabel(canvas: Canvas, text: String, cx: Float, top: Float) {
        val padH = dp(8f)
        val padV = dp(4f)
        val textWidth = labelTextPaint.measureText(text)
        val fm = labelTextPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        var left = cx - textWidth / 2f - padH
        var right = cx + textWidth / 2f + padH
        // Keep the label on screen when an option sits near a side edge.
        val bound = dp(4f)
        if (left < bound) { right += bound - left; left = bound }
        if (right > width - bound) { left -= right - (width - bound); right = width - bound }

        labelRect.set(left, top, right, top + textHeight + padV * 2)
        val radius = labelRect.height() / 2f
        labelBgPaint.alpha = (235 * expansion).toInt()
        canvas.drawRoundRect(labelRect, radius, radius, labelBgPaint)

        labelTextPaint.color = colorOnSurface
        labelTextPaint.alpha = (255 * expansion).toInt()
        canvas.drawText(
            text,
            (left + right) / 2f,
            labelRect.top + padV - fm.ascent,
            labelTextPaint
        )
    }

    private fun iconFor(res: Int): Drawable = iconCache.getOrPut(res) {
        val base = ContextCompat.getDrawable(context, res)!!
        // Several of the project's vectors bake in their own android:tint, so
        // wrap and re-tint rather than inheriting a low-contrast grey.
        DrawableCompat.wrap(base.mutate()).apply {
            DrawableCompat.setTint(this, Color.WHITE)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(enterDragRunnable)
        expansionAnimator?.cancel()
        expansionAnimator = null
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun themeColor(res: Int, fallback: Int): Int = try {
        ContextCompat.getColor(context, res)
    } catch (e: Exception) {
        fallback
    }

    companion object {
        private const val PREFS_NAME = "radial_fab_prefs"
        private const val KEY_FRACTION_X = "fab_fraction_x"
        private const val KEY_FRACTION_Y = "fab_fraction_y"

        private const val DEFAULT_FRACTION_X = 0.88f
        private const val DEFAULT_FRACTION_Y = 0.86f

        private const val MAX_RINGS = 5
        private const val FULL_CIRCLE_EPSILON = 359f
        private const val HOVER_SCALE = 1.35f
        private const val HOVER_SLOP = 1.7f
        private const val SCALE_LERP = 0.35f
        private const val SCRIM_ALPHA = 150
        private const val ICON_RATIO = 0.52f
    }
}
