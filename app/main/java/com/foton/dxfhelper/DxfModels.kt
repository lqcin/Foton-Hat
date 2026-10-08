package com.foton.dxfhelper

import kotlin.math.hypot

data class Pt(val x: Double, val y: Double) {
    fun distanceTo(other: Pt): Double = hypot(x - other.x, y - other.y)
}

data class DxfSegment(
    val id: String,
    val handle: String,
    val layer: String,
    val a: Pt,
    val b: Pt
) {
    val length: Double get() = a.distanceTo(b)
}

data class DxfCircle(
    val handle: String,
    val layer: String,
    val center: Pt,
    val radius: Double
)

data class DxfText(
    val handle: String,
    val layer: String,
    val point: Pt,
    val text: String,
    val height: Double
)

data class DxfDrawing(
    val segments: List<DxfSegment>,
    val circles: List<DxfCircle>,
    val texts: List<DxfText>,
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = (maxX - minX).coerceAtLeast(1.0)
    val height: Double get() = (maxY - minY).coerceAtLeast(1.0)
    val center: Pt get() = Pt((minX + maxX) / 2.0, (minY + maxY) / 2.0)
}

data class Manhole(val circle: DxfCircle?, val label: DxfText, val point: Pt)

data class SelectedLineInfo(
    val segment: DxfSegment,
    val startName: String,
    val endName: String,
    val startPoint: Pt,
    val endPoint: Pt,
    val reversed: Boolean = false
) {
    val sectionNo: String get() = "$startName-$endName"
    val lengthMeters: Double get() = segment.length

    fun reversedCopy(): SelectedLineInfo = copy(
        startName = endName,
        endName = startName,
        startPoint = endPoint,
        endPoint = startPoint,
        reversed = !reversed
    )
}
