package com.foton.dxfhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

class GeoMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MapView(context, attrs) {

    var drawing: DxfDrawing? = null
        set(value) {
            field = value
            resolver =
                value?.let {
                    LineResolver(it)
                }

            selected = null
            routeTarget = null
            rebuildGeoCache()
            post {
                resetView()
            }
        }

    var completedIds: Set<String> =
        emptySet()
        set(value) {
            field = value
            invalidate()
        }

    var selected: DxfSegment? =
        null
        private set

    var routeTarget: DxfSegment? =
        null
        set(value) {
            field = value
            invalidate()
        }

    var onSegmentSelected:
        ((DxfSegment, SelectedLineInfo) -> Unit)? =
        null

    var userLocation: Pt? =
        null
        set(value) {
            field = value
            userGeo =
                value?.let {
                    localToGeo(it)
                }
            invalidate()
        }

    var locationAccuracyMeters: Float? =
        null
        set(value) {
            field = value
            invalidate()
        }

    private var resolver: LineResolver? =
        null

    private var centralMeridianOverride:
        Double? =
        null

    private var userGeo: GeoPoint? =
        null

    private data class GeoSeg(
        val source: DxfSegment,
        val a: GeoPoint,
        val b: GeoPoint
    )

    private data class GeoTxt(
        val source: DxfText,
        val point: GeoPoint
    )

    private data class GeoCircle(
        val source: DxfCircle,
        val point: GeoPoint
    )

    private var geoSegments =
        emptyList<GeoSeg>()

    private var geoTexts =
        emptyList<GeoTxt>()

    private var geoCircles =
        emptyList<GeoCircle>()

    private val paintLine =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = dp(1.6f)
            style = Paint.Style.STROKE
        }

    private val paintCompleted =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                Color.rgb(
                    45,
                    220,
                    125
                )
            strokeWidth =
                dp(4.0f)
            style =
                Paint.Style.STROKE
        }

    private val paintSelected =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                Color.rgb(
                    255,
                    174,
                    66
                )
            strokeWidth =
                dp(4.8f)
            style =
                Paint.Style.STROKE
        }

    private val paintCircle =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth =
                dp(1.2f)
            style =
                Paint.Style.STROKE
        }

    private val paintText =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize =
                dp(11f)
        }

    private val paintGps =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                Color.rgb(
                    45,
                    140,
                    255
                )
            style =
                Paint.Style.FILL
        }

    private val paintGpsStroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                Color.WHITE
            strokeWidth =
                dp(2f)
            style =
                Paint.Style.STROKE
        }

    private val paintAccuracy =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                Color.argb(
                    38,
                    45,
                    140,
                    255
                )
            style =
                Paint.Style.FILL
        }

    private val paintAccuracyStroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                Color.argb(
                    150,
                    45,
                    140,
                    255
                )
            strokeWidth =
                dp(1f)
            style =
                Paint.Style.STROKE
        }

    private val paintRoute =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                Color.rgb(
                    183,
                    104,
                    255
                )
            strokeWidth =
                dp(3.2f)
            style =
                Paint.Style.STROKE
            pathEffect =
                DashPathEffect(
                    floatArrayOf(
                        dp(12f),
                        dp(7f)
                    ),
                    0f
                )
        }

    private val paintTarget =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                Color.rgb(
                    225,
                    185,
                    255
                )
            style =
                Paint.Style.FILL
        }

    private val cadOverlay =
        object : Overlay() {

            override fun draw(
                canvas: Canvas,
                mapView: MapView,
                shadow: Boolean
            ) {
                if (shadow) return
                drawCad(canvas)
            }

            override fun onSingleTapConfirmed(
                e: MotionEvent,
                mapView: MapView
            ): Boolean =
                pickSegment(
                    e.x,
                    e.y
                )
        }

    init {
        Configuration
            .getInstance()
            .userAgentValue =
            context.applicationContext.packageName

        setTileSource(
            TileSourceFactory.MAPNIK
        )

        setMultiTouchControls(true)

        zoomController.setVisibility(
            CustomZoomButtonsController
                .Visibility.NEVER
        )

        setMinZoomLevel(3.0)
        setMaxZoomLevel(22.0)

        overlays.add(cadOverlay)
        overlays.add(
            CopyrightOverlay(context)
        )
    }

    fun setCentralMeridianOverride(
        value: Double?
    ) {
        if (
            centralMeridianOverride == value
        ) return

        centralMeridianOverride =
            value

        rebuildGeoCache()

        userLocation =
            userLocation
    }

    fun resetView() {
        val d =
            drawing ?: return

        if (
            geoSegments.isEmpty()
        ) return

        val bounds =
            geoBounds()
                ?: return

        zoomToBoundingBox(
            bounds,
            false,
            dp(60f).toInt()
        )
    }

    fun zoomIn() {
        controller.zoomIn()
    }

    fun zoomOut() {
        controller.zoomOut()
    }

    fun focusOnUserLocation():
        Boolean {
        val p =
            userGeo ?: return false

        controller.animateTo(p)

        if (
            zoomLevelDouble < 18.0
        ) {
            controller.setZoom(
                18.0
            )
        }

        return true
    }

    fun focusOnTarget():
        Boolean {
        val target =
            routeTarget ?: return false

        val mid =
            Pt(
                (
                    target.a.x +
                        target.b.x
                    ) / 2.0,
                (
                    target.a.y +
                        target.b.y
                    ) / 2.0
            )

        val p =
            localToGeo(mid)
                ?: return false

        controller.animateTo(p)

        if (
            zoomLevelDouble < 18.5
        ) {
            controller.setZoom(
                18.5
            )
        }

        return true
    }

    fun showRouteOverview():
        Boolean {
        val user =
            userGeo ?: return false

        val target =
            routeTarget ?: return false

        val nearest =
            userLocation
                ?.let {
                    nearestPointOnSegment(
                        it,
                        target
                    )
                }
                ?: return false

        val targetGeo =
            localToGeo(nearest)
                ?: return false

        val north =
            maxOf(
                user.latitude,
                targetGeo.latitude
            )

        val south =
            minOf(
                user.latitude,
                targetGeo.latitude
            )

        val east =
            maxOf(
                user.longitude,
                targetGeo.longitude
            )

        val west =
            minOf(
                user.longitude,
                targetGeo.longitude
            )

        post {
            zoomToBoundingBox(
                BoundingBox(
                    north,
                    east,
                    south,
                    west
                ),
                true,
                dp(90f).toInt()
            )
        }

        return true
    }

    fun routeDistanceMeters():
        Double? {
        val user =
            userLocation ?: return null

        val target =
            routeTarget ?: return null

        val nearest =
            nearestPointOnSegment(
                user,
                target
            )

        return hypot(
            user.x -
                nearest.x,
            user.y -
                nearest.y
        )
    }

    fun clearRouteGuide() {
        routeTarget = null
        invalidate()
    }

    private fun rebuildGeoCache() {
        val d =
            drawing ?: run {
                geoSegments =
                    emptyList()
                geoTexts =
                    emptyList()
                geoCircles =
                    emptyList()
                return
            }

        geoSegments =
            d.segments.mapNotNull {
                val a =
                    localToGeo(
                        it.a
                    )
                val b =
                    localToGeo(
                        it.b
                    )

                if (
                    a == null ||
                    b == null
                ) {
                    null
                } else {
                    GeoSeg(
                        it,
                        a,
                        b
                    )
                }
            }

        geoTexts =
            d.texts.mapNotNull {
                localToGeo(
                    it.point
                )
                    ?.let { p ->
                        GeoTxt(
                            it,
                            p
                        )
                    }
            }

        geoCircles =
            d.circles.mapNotNull {
                localToGeo(
                    it.center
                )
                    ?.let { p ->
                        GeoCircle(
                            it,
                            p
                        )
                    }
            }

        invalidate()
    }

    private fun localToGeo(
        p: Pt
    ): GeoPoint? {
        val d =
            drawing ?: return null

        if (
            !d.hasProjectedOrigin
        ) return null

        val cm =
            centralMeridianOverride
                ?: d.centralMeridian

        val geo =
            ItrfTmProjection.inverse(
                easting =
                    d.originX +
                        p.x,
                northing =
                    d.originY +
                        p.y,
                centralMeridianDeg =
                    cm
            )

        if (
            !geo.latitude.isFinite() ||
            !geo.longitude.isFinite()
        ) return null

        return GeoPoint(
            geo.latitude,
            geo.longitude
        )
    }

    private fun drawCad(
        canvas: Canvas
    ) {
        val d =
            drawing ?: return

        val projection =
            projection

        val clip =
            Rect(
                -80,
                -80,
                width + 80,
                height + 80
            )

        val a =
            Point()

        val b =
            Point()

        geoSegments.forEach {
            projection.toPixels(
                it.a,
                a
            )

            projection.toPixels(
                it.b,
                b
            )

            val minX =
                minOf(
                    a.x,
                    b.x
                )

            val maxX =
                maxOf(
                    a.x,
                    b.x
                )

            val minY =
                minOf(
                    a.y,
                    b.y
                )

            val maxY =
                maxOf(
                    a.y,
                    b.y
                )

            if (
                maxX < clip.left ||
                minX > clip.right ||
                maxY < clip.top ||
                minY > clip.bottom
            ) {
                return@forEach
            }

            val paint =
                when {
                    selected?.id ==
                        it.source.id ->
                        paintSelected

                    completedIds.contains(
                        it.source.id
                    ) ->
                        paintCompleted

                    else -> {
                        paintLine.color =
                            SourceColor.resolve(
                                it.source
                                    .sourceColorRgb,
                                it.source
                                    .sourceColorAci,
                                Color.rgb(
                                    70,
                                    115,
                                    170
                                )
                            )
                        paintLine
                    }
                }

            canvas.drawLine(
                a.x.toFloat(),
                a.y.toFloat(),
                b.x.toFloat(),
                b.y.toFloat(),
                paint
            )
        }

        // Düşük zoomda yüzlerce baca/yazı çizip cihazı boğma.
        if (
            zoomLevelDouble >= 16.8
        ) {
            geoCircles.forEach {
                projection.toPixels(
                    it.point,
                    a
                )

                if (
                    !clip.contains(
                        a.x,
                        a.y
                    )
                ) return@forEach

                paintCircle.color =
                    SourceColor.resolve(
                        it.source
                            .sourceColorRgb,
                        it.source
                            .sourceColorAci,
                        Color.rgb(
                            90,
                            135,
                            175
                        )
                    )

                canvas.drawCircle(
                    a.x.toFloat(),
                    a.y.toFloat(),
                    dp(3.3f),
                    paintCircle
                )
            }
        }

        if (
            zoomLevelDouble >= 17.4
        ) {
            geoTexts.forEach {
                val text =
                    it.source.text.trim()

                val manhole =
                    MANHOLE_PATTERN
                        .matches(text)

                if (
                    !manhole &&
                    zoomLevelDouble <
                        18.4
                ) {
                    return@forEach
                }

                projection.toPixels(
                    it.point,
                    a
                )

                if (
                    !clip.contains(
                        a.x,
                        a.y
                    )
                ) return@forEach

                paintText.color =
                    SourceColor.resolve(
                        it.source
                            .sourceColorRgb,
                        it.source
                            .sourceColorAci,
                        Color.rgb(
                            35,
                            50,
                            70
                        )
                    )

                paintText.textSize =
                    if (manhole) {
                        dp(11.5f)
                    } else {
                        dp(9f)
                    }

                canvas.drawText(
                    text,
                    a.x.toFloat() +
                        dp(3f),
                    a.y.toFloat() -
                        dp(3f),
                    paintText
                )
            }
        }

        drawRoute(
            canvas
        )

        drawUser(
            canvas
        )
    }

    private fun drawRoute(
        canvas: Canvas
    ) {
        val user =
            userGeo ?: return

        val userLocal =
            userLocation ?: return

        val target =
            routeTarget ?: return

        val nearest =
            nearestPointOnSegment(
                userLocal,
                target
            )

        val targetGeo =
            localToGeo(nearest)
                ?: return

        val p1 =
            projection.toPixels(
                user,
                Point()
            )

        val p2 =
            projection.toPixels(
                targetGeo,
                Point()
            )

        canvas.drawLine(
            p1.x.toFloat(),
            p1.y.toFloat(),
            p2.x.toFloat(),
            p2.y.toFloat(),
            paintRoute
        )

        canvas.drawCircle(
            p2.x.toFloat(),
            p2.y.toFloat(),
            dp(5f),
            paintTarget
        )
    }

    private fun drawUser(
        canvas: Canvas
    ) {
        val user =
            userGeo ?: return

        val p =
            projection.toPixels(
                user,
                Point()
            )

        val accuracy =
            locationAccuracyMeters
                ?.takeIf {
                    it.isFinite() &&
                        it > 0f
                }

        if (
            accuracy != null
        ) {
            val deltaLat =
                accuracy /
                    111320.0

            val north =
                GeoPoint(
                    user.latitude +
                        deltaLat,
                    user.longitude
                )

            val pNorth =
                projection.toPixels(
                    north,
                    Point()
                )

            val radius =
                hypot(
                    (
                        pNorth.x -
                            p.x
                        ).toDouble(),
                    (
                        pNorth.y -
                            p.y
                        ).toDouble()
                )
                    .toFloat()
                    .coerceIn(
                        dp(5f),
                        dp(180f)
                    )

            canvas.drawCircle(
                p.x.toFloat(),
                p.y.toFloat(),
                radius,
                paintAccuracy
            )

            canvas.drawCircle(
                p.x.toFloat(),
                p.y.toFloat(),
                radius,
                paintAccuracyStroke
            )
        }

        canvas.drawCircle(
            p.x.toFloat(),
            p.y.toFloat(),
            dp(7f),
            paintGps
        )

        canvas.drawCircle(
            p.x.toFloat(),
            p.y.toFloat(),
            dp(7f),
            paintGpsStroke
        )
    }

    private fun pickSegment(
        x: Float,
        y: Float
    ): Boolean {
        val d =
            drawing ?: return false

        val tolerance =
            dp(24f)

        var best:
            DxfSegment? =
            null

        var bestDistance =
            Float.MAX_VALUE

        geoSegments.forEach {
            val a =
                projection.toPixels(
                    it.a,
                    Point()
                )

            val b =
                projection.toPixels(
                    it.b,
                    Point()
                )

            val distance =
                pointToSegment(
                    x,
                    y,
                    a.x.toFloat(),
                    a.y.toFloat(),
                    b.x.toFloat(),
                    b.y.toFloat()
                )

            if (
                distance <
                bestDistance
            ) {
                bestDistance =
                    distance
                best =
                    it.source
            }
        }

        val target =
            best ?: return false

        if (
            bestDistance >
            tolerance
        ) return false

        selected =
            target

        val info =
            resolver
                ?.resolve(
                    target
                )
                ?: return false

        onSegmentSelected
            ?.invoke(
                target,
                info
            )

        invalidate()
        return true
    }

    private fun pointToSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val vx =
            x2 - x1

        val vy =
            y2 - y1

        val wx =
            px - x1

        val wy =
            py - y1

        val len2 =
            vx * vx +
                vy * vy

        if (
            len2 <=
            0.0001f
        ) {
            return hypot(
                (px - x1).toDouble(),
                (py - y1).toDouble()
            ).toFloat()
        }

        val t =
            (
                (
                    wx * vx +
                        wy * vy
                    ) /
                    len2
                )
                .coerceIn(
                    0f,
                    1f
                )

        val cx =
            x1 + t * vx

        val cy =
            y1 + t * vy

        return hypot(
            (px - cx).toDouble(),
            (py - cy).toDouble()
        ).toFloat()
    }

    private fun nearestPointOnSegment(
        p: Pt,
        segment: DxfSegment
    ): Pt {
        val vx =
            segment.b.x -
                segment.a.x

        val vy =
            segment.b.y -
                segment.a.y

        val len2 =
            vx * vx +
                vy * vy

        if (
            len2 <=
            0.000000001
        ) {
            return segment.a
        }

        val t =
            (
                (
                    (
                        p.x -
                            segment.a.x
                        ) * vx +
                        (
                            p.y -
                                segment.a.y
                            ) * vy
                    ) /
                    len2
                )
                .coerceIn(
                    0.0,
                    1.0
                )

        return Pt(
            segment.a.x +
                t * vx,
            segment.a.y +
                t * vy
        )
    }

    private fun geoBounds():
        BoundingBox? {
        if (
            geoSegments.isEmpty()
        ) return null

        var north =
            -90.0

        var south =
            90.0

        var east =
            -180.0

        var west =
            180.0

        geoSegments.forEach {
            listOf(
                it.a,
                it.b
            ).forEach { p ->
                north =
                    maxOf(
                        north,
                        p.latitude
                    )

                south =
                    minOf(
                        south,
                        p.latitude
                    )

                east =
                    maxOf(
                        east,
                        p.longitude
                    )

                west =
                    minOf(
                        west,
                        p.longitude
                    )
            }
        }

        return BoundingBox(
            north,
            east,
            south,
            west
        )
    }

    private fun dp(
        value: Float
    ): Float =
        value *
            resources
                .displayMetrics
                .density

    companion object {
        private val MANHOLE_PATTERN =
            Regex(
                "^[A-Za-zÇĞİÖŞÜçğıöşü]+[0-9]+(?:-[0-9]+)*$"
            )
    }
}
