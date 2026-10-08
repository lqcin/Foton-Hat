package com.foton.dxfhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class DxfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var drawing: DxfDrawing? = null
        set(value) {
            field = value
            resolver = value?.let { LineResolver(it) }
            selected = null
            fitInitialized = false
            invalidate()
        }

    var completedIds: Set<String> = emptySet()
        set(value) { field = value; invalidate() }

    var selected: DxfSegment? = null
        private set

    var onSegmentSelected: ((DxfSegment, SelectedLineInfo) -> Unit)? = null

    private var resolver: LineResolver? = null
    private var baseScale = 1f
    private var userScale = 1f
    private var panX = 0f
    private var panY = 0f
    private var fitInitialized = false

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 160, 190); strokeWidth = dp(1.4f); style = Paint.Style.STROKE
    }
    private val completedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(44, 210, 120); strokeWidth = dp(3.2f); style = Paint.Style.STROKE
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 183, 77); strokeWidth = dp(4.2f); style = Paint.Style.STROKE
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(110, 150, 180); strokeWidth = dp(1.2f); style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(235, 242, 250); textSize = sp(12f)
    }
    private val gridPaint = Paint().apply { color = Color.rgb(24, 37, 56) }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val old = userScale
            userScale = (userScale * detector.scaleFactor).coerceIn(0.25f, 30f)
            val ratio = userScale / old
            panX = detector.focusX - (detector.focusX - panX) * ratio
            panY = detector.focusY - (detector.focusY - panY) * ratio
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            panX -= distanceX
            panY -= distanceY
            invalidate()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            pickSegment(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetView()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(11, 20, 32))
        val d = drawing ?: return
        if (!fitInitialized && width > 0 && height > 0) initializeFit(d)

        d.segments.forEach { seg ->
            val a = worldToScreen(seg.a, d)
            val b = worldToScreen(seg.b, d)
            val paint = when {
                selected?.id == seg.id -> selectedPaint
                completedIds.contains(seg.id) -> completedPaint
                else -> linePaint
            }
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }

        val r = resolver
        if (r != null) {
            val labelScale = (baseScale * userScale).coerceAtLeast(0.001f)
            r.manholeLabels().forEach { m ->
                val c = worldToScreen(m.point, d)
                val rr = ((m.circle?.radius ?: 0.25) * labelScale).toFloat().coerceIn(dp(2f), dp(9f))
                canvas.drawCircle(c.x, c.y, rr, circlePaint)
                if (userScale >= 0.8f) {
                    canvas.drawText(m.label.text, c.x + dp(6f), c.y - dp(5f), textPaint)
                }
            }
        }
    }

    fun resetView() {
        userScale = 1f
        panX = 0f
        panY = 0f
        fitInitialized = false
        invalidate()
    }

    private fun initializeFit(d: DxfDrawing) {
        val sx = (width * 0.90 / d.width).toFloat()
        val sy = (height * 0.88 / d.height).toFloat()
        baseScale = min(sx, sy).coerceAtLeast(0.0001f)
        userScale = 1f
        panX = 0f; panY = 0f
        fitInitialized = true
    }

    private fun worldToScreen(p: Pt, d: DxfDrawing): PointF {
        val c = d.center
        val s = baseScale * userScale
        return PointF(
            ((p.x - c.x) * s + width / 2f + panX).toFloat(),
            (-(p.y - c.y) * s + height / 2f + panY).toFloat()
        )
    }

    private fun pickSegment(x: Float, y: Float) {
        val d = drawing ?: return
        val tolerance = dp(24f)
        var best: DxfSegment? = null
        var bestDist = Float.MAX_VALUE
        d.segments.forEach { seg ->
            val a = worldToScreen(seg.a, d)
            val b = worldToScreen(seg.b, d)
            val dist = distancePointToSegment(x, y, a.x, a.y, b.x, b.y)
            if (dist < bestDist) { bestDist = dist; best = seg }
        }
        if (best != null && bestDist <= tolerance) {
            selected = best
            val info = resolver?.resolve(best!!) ?: return
            onSegmentSelected?.invoke(best!!, info)
            invalidate()
        }
    }

    private fun distancePointToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val vx = x2 - x1; val vy = y2 - y1
        val wx = px - x1; val wy = py - y1
        val len2 = vx * vx + vy * vy
        if (len2 <= 0.0001f) return Math.hypot((px - x1).toDouble(), (py - y1).toDouble()).toFloat()
        val t = ((wx * vx + wy * vy) / len2).coerceIn(0f, 1f)
        val cx = x1 + t * vx; val cy = y1 + t * vy
        return Math.hypot((px - cx).toDouble(), (py - cy).toDouble()).toFloat()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}
