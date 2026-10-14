package com.foton.dxfhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot
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
            routeTarget = null
            fitInitialized = false
            invalidate()
        }

    var completedIds: Set<String> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    var selected: DxfSegment? = null
        private set

    var routeTarget: DxfSegment? = null
        set(value) {
            field = value
            invalidate()
        }

    var onSegmentSelected: ((DxfSegment, SelectedLineInfo) -> Unit)? = null

    var userLocation: Pt? = null
        set(value) {
            field = value
            invalidate()
        }

    var locationAccuracyMeters: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    private var resolver: LineResolver? = null

    private var baseScale = 1f
    private var userScale = 1f
    private var panX = 0f
    private var panY = 0f
    private var fitInitialized = false

    private val minUserScale = 0.75f
    private val maxUserScale = 80f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(118, 158, 188)
        strokeWidth = dp(1.5f)
        style = Paint.Style.STROKE
    }

    private val completedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(45, 220, 125)
        strokeWidth = dp(3.5f)
        style = Paint.Style.STROKE
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 183, 77)
        strokeWidth = dp(4.5f)
        style = Paint.Style.STROKE
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(132, 174, 201)
        strokeWidth = dp(1.1f)
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 244, 250)
    }

    private val locationAccuracyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                46,
                58,
                150,
                255
            )
            style = Paint.Style.FILL
        }

    private val locationAccuracyStroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                180,
                58,
                150,
                255
            )
            strokeWidth = dp(1.2f)
            style = Paint.Style.STROKE
        }

    private val locationDotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(
                45,
                140,
                255
            )
            style = Paint.Style.FILL
        }

    private val locationDotStroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = dp(2.0f)
            style = Paint.Style.STROKE
        }

    private val routePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(
                191,
                125,
                255
            )
            strokeWidth = dp(2.6f)
            style = Paint.Style.STROKE
            pathEffect =
                DashPathEffect(
                    floatArrayOf(
                        dp(10f),
                        dp(7f)
                    ),
                    0f
                )
        }

    private val routeTargetPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(
                220,
                180,
                255
            )
            style = Paint.Style.FILL
        }

    /**
     * Eski sürümde pinch zoom odagi ekran merkezini hesaba katmadan
     * hesaplandigi icin harita parmak altindan kayiyordu.
     * Yeni hesap focus noktasini gercekten sabit tutar.
     */
    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomAt(
                    detector.scaleFactor,
                    detector.focusX,
                    detector.focusY
                )
                return true
            }
        })

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                panX -= distanceX
                panY -= distanceY
                invalidate()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                pickSegment(e.x, e.y)
                return true
            }

            // Double tap artik sifirlamaz; dokunulan noktaya yaklasir.
            override fun onDoubleTap(e: MotionEvent): Boolean {
                zoomAt(1.85f, e.x, e.y)
                return true
            }
        })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(11, 20, 32))

        val d = drawing ?: return

        if (!fitInitialized && width > 0 && height > 0) {
            initializeFit(d)
        }

        d.segments.forEach { seg ->
            val a = worldToScreen(seg.a, d)
            val b = worldToScreen(seg.b, d)

            val paint = when {
                selected?.id == seg.id ->
                    selectedPaint

                completedIds.contains(seg.id) ->
                    completedPaint

                else -> {
                    linePaint.color =
                        SourceColor.resolve(
                            seg.sourceColorRgb,
                            seg.sourceColorAci,
                            Color.rgb(118, 158, 188)
                        )
                    linePaint
                }
            }

            canvas.drawLine(
                a.x,
                a.y,
                b.x,
                b.y,
                paint
            )
        }

        d.circles.forEach { circle ->
            val c =
                worldToScreen(
                    circle.center,
                    d
                )

            val rr =
                (
                    circle.radius *
                    baseScale *
                    userScale
                )
                .toFloat()
                .coerceIn(
                    dp(1.5f),
                    dp(12f)
                )

            circlePaint.color =
                SourceColor.resolve(
                    circle.sourceColorRgb,
                    circle.sourceColorAci,
                    Color.rgb(132, 174, 201)
                )

            canvas.drawCircle(
                c.x,
                c.y,
                rr,
                circlePaint
            )
        }

        drawRouteGuide(canvas, d)
        drawTexts(canvas, d)
        drawUserLocation(canvas, d)
    }

    private fun drawTexts(canvas: Canvas, d: DxfDrawing) {
        val scale = baseScale * userScale

        d.texts.forEach { t ->
            val p = worldToScreen(t.point, d)
            val sourceSize = (t.height * scale).toFloat()
            val size = sourceSize.coerceIn(dp(7f), dp(24f))

            val looksLikeManhole = MANHOLE_PATTERN.matches(t.text.trim())
            if (!looksLikeManhole && sourceSize < dp(4.5f) && userScale < 1.6f) {
                return@forEach
            }

            textPaint.textSize =
                if (looksLikeManhole)
                    size.coerceAtLeast(dp(9f))
                else
                    size

            textPaint.color =
                SourceColor.resolve(
                    t.sourceColorRgb,
                    t.sourceColorAci,
                    Color.rgb(238, 244, 250)
                )

            canvas.save()
            canvas.rotate(
                (-Math.toDegrees(t.rotation)).toFloat(),
                p.x,
                p.y
            )
            canvas.drawText(
                t.text,
                p.x + dp(2f),
                p.y - dp(2f),
                textPaint
            )
            canvas.restore()
        }
    }

    private fun drawRouteGuide(
        canvas: Canvas,
        d: DxfDrawing
    ) {
        val location =
            userLocation ?: return

        val target =
            routeTarget ?: return

        val nearest =
            nearestPointOnSegment(
                location,
                target
            )

        val from =
            worldToScreen(
                location,
                d
            )

        val to =
            worldToScreen(
                nearest,
                d
            )

        canvas.drawLine(
            from.x,
            from.y,
            to.x,
            to.y,
            routePaint
        )

        canvas.drawCircle(
            to.x,
            to.y,
            dp(5f),
            routeTargetPaint
        )
    }

    fun routeDistanceMeters(): Double? {
        val location =
            userLocation ?: return null

        val target =
            routeTarget ?: return null

        val nearest =
            nearestPointOnSegment(
                location,
                target
            )

        return hypot(
            location.x - nearest.x,
            location.y - nearest.y
        )
    }

    fun clearRouteGuide() {
        routeTarget = null
        invalidate()
    }

    private fun nearestPointOnSegment(
        p: Pt,
        segment: DxfSegment
    ): Pt {
        val ax = segment.a.x
        val ay = segment.a.y
        val bx = segment.b.x
        val by = segment.b.y

        val vx =
            bx - ax

        val vy =
            by - ay

        val len2 =
            vx * vx +
                vy * vy

        if (
            len2 <= 0.000000001
        ) {
            return segment.a
        }

        val t =
            (
                (
                    (p.x - ax) * vx +
                    (p.y - ay) * vy
                ) / len2
            )
                .coerceIn(
                    0.0,
                    1.0
                )

        return Pt(
            ax + t * vx,
            ay + t * vy
        )
    }

    private fun drawUserLocation(
        canvas: Canvas,
        d: DxfDrawing
    ) {
        val location =
            userLocation ?: return

        val p =
            worldToScreen(
                location,
                d
            )

        val accuracy =
            locationAccuracyMeters
                ?.takeIf {
                    it.isFinite() &&
                        it > 0f
                }

        if (accuracy != null) {
            val radius =
                (
                    accuracy *
                        baseScale *
                        userScale
                    )
                    .coerceIn(
                        dp(5f),
                        dp(150f)
                    )

            canvas.drawCircle(
                p.x,
                p.y,
                radius,
                locationAccuracyPaint
            )

            canvas.drawCircle(
                p.x,
                p.y,
                radius,
                locationAccuracyStroke
            )
        }

        canvas.drawCircle(
            p.x,
            p.y,
            dp(7f),
            locationDotPaint
        )

        canvas.drawCircle(
            p.x,
            p.y,
            dp(7f),
            locationDotStroke
        )

        canvas.drawCircle(
            p.x,
            p.y,
            dp(2.3f),
            locationDotStroke
        )
    }

    fun focusOnUserLocation(): Boolean {
        val d =
            drawing ?: return false

        val location =
            userLocation ?: return false

        if (
            width <= 0 ||
            height <= 0
        ) return false

        // Fokus sırasında harita yeterince okunabilir olsun.
        userScale =
            max(
                userScale,
                4.0f
            )
            .coerceAtMost(
                maxUserScale
            )

        val s =
            baseScale *
                userScale

        val c =
            d.center

        // worldToScreen sonucu ekran merkezine gelsin.
        panX =
            -(
                (
                    location.x -
                        c.x
                    ) * s
                ).toFloat()

        panY =
            (
                (
                    location.y -
                        c.y
                    ) * s
                ).toFloat()

        invalidate()
        return true
    }

    fun resetView() {
        userScale = 1f
        panX = 0f
        panY = 0f
        fitInitialized = false
        invalidate()
    }

    fun zoomIn() {
        zoomAt(1.55f, width / 2f, height / 2f)
    }

    fun zoomOut() {
        zoomAt(1f / 1.55f, width / 2f, height / 2f)
    }

    fun zoomAt(factor: Float, focusX: Float, focusY: Float) {
        if (width <= 0 || height <= 0 || factor <= 0f) return

        val oldScale = userScale
        val newScale = (oldScale * factor).coerceIn(minUserScale, maxUserScale)
        if (newScale == oldScale) return

        val ratio = newScale / oldScale

        // Transformun ekran merkezi terimini hesaba kat.
        val cx = width / 2f
        val cy = height / 2f

        panX =
            focusX - cx - (focusX - cx - panX) * ratio
        panY =
            focusY - cy - (focusY - cy - panY) * ratio

        userScale = newScale
        invalidate()
    }

    private fun initializeFit(d: DxfDrawing) {
        val sx = (width * 0.94 / d.width).toFloat()
        val sy = (height * 0.92 / d.height).toFloat()

        baseScale = min(sx, sy).coerceAtLeast(0.0001f)

        userScale = 1f
        panX = 0f
        panY = 0f
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

        // Zoom seviyesine gore biraz daha rahat secim:
        // uzak planda parmak toleransi genis, yakin planda hassas.
        val tolerance = when {
            userScale < 1.2f -> dp(30f)
            userScale < 3f -> dp(25f)
            else -> dp(20f)
        }

        var best: DxfSegment? = null
        var bestDist = Float.MAX_VALUE

        d.segments.forEach { seg ->
            val a = worldToScreen(seg.a, d)
            val b = worldToScreen(seg.b, d)

            val dist = distancePointToSegment(
                x, y,
                a.x, a.y,
                b.x, b.y
            )

            if (dist < bestDist) {
                bestDist = dist
                best = seg
            }
        }

        if (best != null && bestDist <= tolerance) {
            selected = best
            val info = resolver?.resolve(best!!) ?: return
            onSegmentSelected?.invoke(best!!, info)
            invalidate()
        }
    }

    private fun distancePointToSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val vx = x2 - x1
        val vy = y2 - y1
        val wx = px - x1
        val wy = py - y1

        val len2 = vx * vx + vy * vy

        if (len2 <= 0.0001f) {
            return Math.hypot(
                (px - x1).toDouble(),
                (py - y1).toDouble()
            ).toFloat()
        }

        val t =
            ((wx * vx + wy * vy) / len2).coerceIn(0f, 1f)

        val cx = x1 + t * vx
        val cy = y1 + t * vy

        return Math.hypot(
            (px - cx).toDouble(),
            (py - cy).toDouble()
        ).toFloat()
    }

    private fun dp(v: Float): Float =
        v * resources.displayMetrics.density

    companion object {
        private val MANHOLE_PATTERN =
            Regex("^[A-Za-zÇĞİÖŞÜçğıöşü]+[0-9]+(?:-[0-9]+)*$")
    }
}
