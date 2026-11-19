package com.example.arrows

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.random.Random

/**
 * Big, zoomable maze of snake arrows that is laid out in the SHAPE of the level (horse, heart, fish ...).
 *
 * - Pinch to zoom, drag to pan, tap a line to send that arrow out.
 * - A free arrow slides out along its own path (body follows head) and fades.
 * - A blocked arrow bumps into the blocker, turns red and bounces back (costs a heart, handled by the activity).
 * - When the last arrow is gone the board zooms out and the silhouette fills in (playReveal).
 * - Levels are seeded (same maze every time) and always solvable.
 */
class ArrowBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val C = 40f   // world units per grid cell
    }

    enum class Dir(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }
    private enum class State { IDLE, SLIDING, BUMPING, GONE }

    var onBlocked: (() -> Unit)? = null
    var onSlide: (() -> Unit)? = null
    var onProgress: ((alive: Int) -> Unit)? = null
    var inputEnabled = true

    var totalArrows = 0
        private set
    var shapeName = ""
        private set

    private var cols = 16
    private var rows = 16
    private var inside: Array<BooleanArray> = emptyArray()
    private var occ: Array<IntArray> = emptyArray()    // >=0 arrow index, -1 free cell of the shape, -2 outside the shape
    private val arrows = ArrayList<Arrow>()
    private val running = ArrayList<ValueAnimator>()
    private var alive = 0
    private var hint: Arrow? = null
    private var rng = Random(1)

    // static geometry of the current shape
    private var insideCells = IntArray(0)               // y * cols + x
    private var dotPts = FloatArray(0)
    private var tintPath = Path()
    private var shapeCx = 0f
    private var shapeCy = 0f
    private var shapeRadius = 1f
    private var revealProgress = 0f

    // ---- camera (zoom + pan)
    private val density = resources.displayMetrics.density
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var fitScale = 1f        // play view: cells at least ~16dp wide
    private var overviewScale = 1f   // whole shape visible
    private var minScale = 0.2f
    private var maxScale = 8f
    private var pendingFit = true
    private var viewAnim: ValueAnimator? = null

    private inner class Arrow(val cells: List<Pair<Int, Int>>) {   // tail -> head
        val n = cells.size
        val dir: Dir
        val kOut: Int
        val pts = ArrayList<PointF>()                                // cell units
        val minX = cells.minOf { it.first }
        val maxX = cells.maxOf { it.first }
        val minY = cells.minOf { it.second }
        val maxY = cells.maxOf { it.second }
        var state = State.IDLE
        var offset = 0f
        var alpha = 255
        var hot = false

        init {
            val (px, py) = cells[n - 2]
            val (hx, hy) = cells[n - 1]
            dir = Dir.values().first { it.dx == hx - px && it.dy == hy - py }
            kOut = when (dir) {
                Dir.RIGHT -> cols - hx
                Dir.LEFT -> hx + 1
                Dir.DOWN -> rows - hy
                Dir.UP -> hy + 1
            }
            cells.forEach { pts.add(PointF(it.first + 0.5f, it.second + 0.5f)) }
            for (k in 1..(n + kOut)) pts.add(PointF(hx + 0.5f + dir.dx * k, hy + 0.5f + dir.dy * k))
        }

        val endOffset: Float get() = (n - 1 + kOut).toFloat()
    }

    private val ink = ContextCompat.getColor(context, R.color.ink)
    private val heart = ContextCompat.getColor(context, R.color.heart)
    private val accent = ContextCompat.getColor(context, R.color.accent)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = C * 0.13f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.dot)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = C * 0.10f
    }
    private val tintPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.shape_tint)
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    private val revealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.FILL
    }

    // ================================================================ level generation

    fun loadLevel(level: Int) {
        cancelAnimations()
        val cfg = LevelConfig.forLevel(context, level)
        inside = cfg.inside
        rows = inside.size
        cols = inside[0].size
        shapeName = cfg.shapeName
        rng = Random(level * 7919L + 13L)
        occ = Array(rows) { y -> IntArray(cols) { x -> if (inside[y][x]) -1 else -2 } }
        arrows.clear()
        hint = null
        revealProgress = 0f

        buildStaticGeometry()
        generate(cfg.minLen, cfg.maxLen)

        totalArrows = arrows.size
        alive = totalArrows
        pendingFit = true
        if (width > 0 && height > 0) fitToScreen(false)
        onProgress?.invoke(alive)
        invalidate()
    }

    private fun buildStaticGeometry() {
        val list = ArrayList<Int>()
        var sx = 0f
        var sy = 0f
        for (y in 0 until rows) for (x in 0 until cols) if (inside[y][x]) {
            list.add(y * cols + x); sx += x; sy += y
        }
        insideCells = list.toIntArray()
        val n = max(1, insideCells.size)
        shapeCx = sx / n
        shapeCy = sy / n
        var maxD = 1f
        for (idx in insideCells) maxD = max(maxD, hypot(idx % cols - shapeCx, idx / cols - shapeCy))
        shapeRadius = maxD

        dotPts = FloatArray(insideCells.size * 2)
        for ((i, idx) in insideCells.withIndex()) {
            dotPts[2 * i] = (idx % cols + 0.5f) * C
            dotPts[2 * i + 1] = (idx / cols + 0.5f) * C
        }
        // pale silhouette behind the maze: one rectangle per horizontal run of cells
        tintPath = Path().also { p ->
            for (y in 0 until rows) {
                var x = 0
                while (x < cols) {
                    if (inside[y][x]) {
                        val s = x
                        while (x < cols && inside[y][x]) x++
                        p.addRect(s * C, y * C, x * C, (y + 1) * C, Path.Direction.CW)
                    } else x++
                }
            }
        }
    }

    /**
     * Fills the shape from its centre outwards. An arrow is only added when the straight line in
     * front of its head is free of other arrows at that moment, so removing the arrows in reverse
     * order of placement always works -> every level is solvable. Cells outside the shape are
     * never used, but arrows can slide out through them.
     */
    private fun generate(minLen: Int, maxLen: Int) {
        val order = insideCells.map { idx ->
            val x = idx % cols
            val y = idx / cols
            Triple(hypot((x - shapeCx) / cols, (y - shapeCy) / rows) + rng.nextFloat() * 0.06f, x, y)
        }.sortedBy { it.first }

        for (pass in 0..1) {
            val lo = if (pass == 0) minLen else 2
            for ((_, x, y) in order) {
                var tries = 0
                while (tries < 14 && occ[y][x] == -1) {
                    tries++
                    val a = tryArrow(x, y, lo, maxLen) ?: continue
                    val idx = arrows.size
                    arrows.add(a)
                    a.cells.forEach { occ[it.second][it.first] = idx }
                }
            }
        }
    }

    private fun tryArrow(sx: Int, sy: Int, minLen: Int, maxLen: Int): Arrow? {
        val cells = arrayListOf(sx to sy)
        val target = if (maxLen > minLen) rng.nextInt(minLen, maxLen + 1) else minLen
        var last: Dir? = null
        while (cells.size < target) {
            val (cx, cy) = cells.last()
            val options = Dir.values().filter { d ->
                val nx = cx + d.dx
                val ny = cy + d.dy
                nx in 0 until cols && ny in 0 until rows && occ[ny][nx] == -1 && (nx to ny) !in cells
            }
            if (options.isEmpty()) break
            val keep = last?.takeIf { it in options }
            val d = if (keep != null && rng.nextFloat() < 0.55f) keep else options.random(rng)
            cells.add((cx + d.dx) to (cy + d.dy))
            last = d
        }
        if (cells.size < minLen || cells.size < 2) return null

        for (candidate in listOf<List<Pair<Int, Int>>>(cells, cells.reversed()).shuffled(rng)) {
            if (rayClear(candidate)) return Arrow(candidate)
        }
        return null
    }

    private fun rayClear(c: List<Pair<Int, Int>>): Boolean {
        val n = c.size
        val (px, py) = c[n - 2]
        var (hx, hy) = c[n - 1]
        val dx = hx - px
        val dy = hy - py
        while (true) {
            hx += dx
            hy += dy
            if (hx !in 0 until cols || hy !in 0 until rows) return true
            if (occ[hy][hx] >= 0 || (hx to hy) in c) return false
        }
    }

    /** -1 if the head can leave the board, otherwise the number of free cells before the blocker. */
    private fun freeSteps(a: Arrow): Int {
        var (x, y) = a.cells.last()
        var steps = 0
        while (true) {
            x += a.dir.dx
            y += a.dir.dy
            if (x !in 0 until cols || y !in 0 until rows) return -1
            if (occ[y][x] >= 0) return steps
            steps++
        }
    }

    fun showHint() {
        val a = arrows.filter { it.state == State.IDLE && freeSteps(it) < 0 }.randomOrNull() ?: return
        hint = a
        val head = a.pts[a.n - 1]
        val ns = max(scale, fitScale * 1.6f).coerceAtMost(maxScale)
        animateTo(ns, width / 2f - head.x * C * ns, height / 2f - head.y * C * ns)
    }

    /** Called when the last arrow has left: zoom out and fill the silhouette in. */
    fun playReveal(onEnd: () -> Unit) {
        computeLimits()
        if (scale > overviewScale * 1.05f) {
            animateTo(overviewScale, (width - cols * C * overviewScale) / 2f, (height - rows * C * overviewScale) / 2f)
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                revealProgress = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    running.remove(animation)
                    onEnd()
                }
            })
        }
        running.add(anim)
        anim.start()
    }

    // ================================================================ camera

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0 && arrows.isNotEmpty()) {
            if (pendingFit) fitToScreen(false) else { computeLimits(); clampTranslation() }
        }
    }

    private fun computeLimits() {
        val sW = width / (cols * C)
        val sH = height / (rows * C)
        overviewScale = min(sW, sH) * 0.96f                      // whole shape, small margin
        val comfy = 16f * density / C                            // a cell is 16dp wide
        fitScale = max(overviewScale, comfy)
        minScale = overviewScale * 0.9f
        maxScale = max(56f * density / C, fitScale * 1.5f)
    }

    private fun fitToScreen(animate: Boolean) {
        computeLimits()
        pendingFit = false
        val ntx = (width - cols * C * fitScale) / 2f
        val nty = (height - rows * C * fitScale) / 2f
        if (animate) animateTo(fitScale, ntx, nty) else {
            scale = fitScale; tx = ntx; ty = nty
            clampTranslation()
            invalidate()
        }
    }

    /** Button "#": whole shape  <->  play view (or a closer zoom when the shape is already fully visible). */
    fun toggleZoom() {
        computeLimits()
        when {
            scale > overviewScale * 1.15f ->
                animateTo(overviewScale, (width - cols * C * overviewScale) / 2f, (height - rows * C * overviewScale) / 2f)
            fitScale > overviewScale * 1.05f -> fitToScreen(true)
            else -> {
                val ns = min(maxScale, overviewScale * 1.8f)
                val cx = width / 2f
                val cy = height / 2f
                animateTo(ns, cx - (cx - tx) * ns / scale, cy - (cy - ty) * ns / scale)
            }
        }
    }

    private fun clampTranslation() {
        val m = 24f * density
        val bw = cols * C * scale
        val bh = rows * C * scale
        tx = if (bw + 2 * m <= width) (width - bw) / 2f else tx.coerceIn(width - bw - m, m)
        ty = if (bh + 2 * m <= height) (height - bh) / 2f else ty.coerceIn(height - bh - m, m)
    }

    private fun animateTo(ns: Float, ntx: Float, nty: Float) {
        viewAnim?.cancel()
        val s0 = scale
        val x0 = tx
        val y0 = ty
        viewAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                scale = s0 + (ns - s0) * f
                tx = x0 + (ntx - x0) * f
                ty = y0 + (nty - y0) * f
                clampTranslation()
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            viewAnim?.cancel()
            val ns = (scale * d.scaleFactor).coerceIn(minScale, maxScale)
            val f = ns / scale
            tx = d.focusX - (d.focusX - tx) * f
            ty = d.focusY - (d.focusY - ty) * f
            scale = ns
            clampTranslation()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaleDetector.isInProgress) return false
            viewAnim?.cancel()
            tx -= dx
            ty -= dy
            clampTranslation()
            invalidate()
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(e)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(e)
        return true
    }

    // ================================================================ taps

    private fun handleTap(x: Float, y: Float) {
        if (!inputEnabled || arrows.isEmpty()) return
        val wx = (x - tx) / scale / C
        val wy = (y - ty) / scale / C
        val thr = (16f * density / (scale * C)).coerceIn(0.35f, 0.9f)   // finger-friendly radius

        var best: Arrow? = null
        var bestDist = thr
        for (a in arrows) {
            if (a.state != State.IDLE) continue
            for (i in 0 until a.n - 1) {
                val d = distToSegment(wx, wy, a.pts[i], a.pts[i + 1])
                if (d < bestDist) { bestDist = d; best = a }
            }
        }
        best?.let {
            hint = null
            val f = freeSteps(it)
            if (f < 0) slideOut(it) else bump(it, f)
        }
    }

    private fun distToSegment(px: Float, py: Float, a: PointF, b: PointF): Float {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        val t = if (len2 == 0f) 0f else (((px - a.x) * vx + (py - a.y) * vy) / len2).coerceIn(0f, 1f)
        return hypot(px - (a.x + t * vx), py - (a.y + t * vy))
    }

    // ================================================================ animation

    private fun slideOut(a: Arrow) {
        a.state = State.SLIDING
        a.cells.forEach { occ[it.second][it.first] = -1 }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onSlide?.invoke()

        val anim = ValueAnimator.ofFloat(0f, a.endOffset).apply {
            duration = (a.endOffset * 45f).toLong().coerceIn(300L, 1100L)
            interpolator = AccelerateInterpolator(1.3f)
            addUpdateListener {
                a.offset = it.animatedValue as Float
                val fr = it.animatedFraction
                a.alpha = if (fr > 0.75f) (255 * (1f - (fr - 0.75f) / 0.25f)).toInt().coerceIn(0, 255) else 255
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    running.remove(animation)
                    a.state = State.GONE
                    alive--
                    onProgress?.invoke(alive)
                    invalidate()
                }
            })
        }
        running.add(anim)
        anim.start()
    }

    private fun bump(a: Arrow, freeCells: Int) {
        a.state = State.BUMPING
        a.hot = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onBlocked?.invoke()

        val anim = ValueAnimator.ofFloat(0f, freeCells + 0.45f).apply {
            duration = 150L + freeCells * 50L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = 1
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                a.offset = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    running.remove(animation)
                    a.offset = 0f
                    a.hot = false
                    a.state = State.IDLE
                    invalidate()
                }
            })
        }
        running.add(anim)
        anim.start()
    }

    private fun cancelAnimations() {
        viewAnim?.cancel()
        running.toList().forEach {
            it.removeAllListeners()
            it.removeAllUpdateListeners()
            it.cancel()
        }
        running.clear()
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        super.onDetachedFromWindow()
    }

    // ================================================================ drawing

    override fun onDraw(canvas: Canvas) {
        if (arrows.isEmpty()) return
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(scale, scale)

        canvas.drawPath(tintPath, tintPaint)          // pale silhouette
        canvas.drawPoints(dotPts, dotPaint)

        if (revealProgress > 0f) drawReveal(canvas)

        val l = -tx / scale
        val t = -ty / scale
        val r = (width - tx) / scale
        val b = (height - ty) / scale
        for (a in arrows) {
            if (a.state == State.IDLE &&
                (a.maxX + 1) * C >= l && a.minX * C <= r && (a.maxY + 1) * C >= t && a.minY * C <= b
            ) drawArrow(canvas, a)
        }
        for (a in arrows) if (a.state == State.SLIDING || a.state == State.BUMPING) drawArrow(canvas, a)
        canvas.restore()
    }

    /** The cleared board turns into a solid silhouette, spreading out from the centre. */
    private fun drawReveal(canvas: Canvas) {
        for (idx in insideCells) {
            val x = idx % cols
            val y = idx / cols
            val d = hypot(x - shapeCx, y - shapeCy) / shapeRadius
            val local = ((revealProgress * 1.5f - d) / 0.5f).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val inset = (1f - local) * 0.5f * C
            revealPaint.alpha = (local * 235).toInt()
            canvas.drawRect(x * C + inset, y * C + inset, (x + 1) * C - inset, (y + 1) * C - inset, revealPaint)
        }
    }

    private fun pointAt(a: Arrow, t: Float): PointF {
        val i = floor(t).toInt().coerceIn(0, a.pts.size - 2)
        val f = (t - i).coerceIn(0f, 1f)
        val p = a.pts[i]
        val q = a.pts[i + 1]
        return PointF(p.x + (q.x - p.x) * f, p.y + (q.y - p.y) * f)
    }

    private fun drawArrow(c: Canvas, a: Arrow) {
        val s = a.offset
        val tEnd = s + (a.n - 1)

        val p0 = pointAt(a, s)
        val path = Path().apply { moveTo(p0.x * C, p0.y * C) }
        var i = floor(s).toInt() + 1
        while (i < tEnd) {
            path.lineTo(a.pts[i].x * C, a.pts[i].y * C)
            i++
        }
        val tip = pointAt(a, tEnd)
        val hx = tip.x * C
        val hy = tip.y * C
        path.lineTo(hx, hy)

        linePaint.color = when {
            a.hot -> heart
            a === hint -> accent
            else -> ink
        }
        linePaint.alpha = a.alpha
        c.drawPath(path, linePaint)

        val seg = (ceil(tEnd).toInt() - 1).coerceIn(0, a.pts.size - 2)
        val dx = sign(a.pts[seg + 1].x - a.pts[seg].x)
        val dy = sign(a.pts[seg + 1].y - a.pts[seg].y)
        val h = C * 0.30f
        val px = -dy
        val py = dx
        val head = Path().apply {
            moveTo(hx - dx * h + px * h, hy - dy * h + py * h)
            lineTo(hx, hy)
            lineTo(hx - dx * h - px * h, hy - dy * h - py * h)
        }
        c.drawPath(head, linePaint)
    }
}
