package com.foton.dxfhelper

import kotlin.math.max

class LineResolver(private val drawing: DxfDrawing) {
    private val manholes: List<Manhole> = buildManholes()

    private fun buildManholes(): List<Manhole> {
        val labelPattern = Regex("^[A-Za-zÇĞİÖŞÜçğıöşü]+[0-9]+(?:-[0-9]+)*$")
        val candidateTexts = drawing.texts.filter {
            it.layer.contains("BACA", ignoreCase = true) || labelPattern.matches(it.text.trim())
        }

        return drawing.circles.mapNotNull { circle ->
            val best = candidateTexts
                .map { text -> text to text.point.distanceTo(circle.center) }
                .filter { (_, d) -> d <= max(6.0, circle.radius * 20.0) }
                .sortedWith(compareBy<Pair<DxfText, Double>>(
                    { if (labelPattern.matches(it.first.text.trim())) 0 else 1 },
                    { it.second }
                ))
                .firstOrNull()
                ?: return@mapNotNull null
            Manhole(circle, best.first, circle.center)
        }
    }

    fun resolve(segment: DxfSegment): SelectedLineInfo {
        val start = nearestManhole(segment.a)
        val end = nearestManhole(segment.b)
        val startName = start?.label?.text?.trim().takeUnless { it.isNullOrBlank() } ?: nearestLabel(segment.a)?.text ?: "?"
        val endName = end?.label?.text?.trim().takeUnless { it.isNullOrBlank() } ?: nearestLabel(segment.b)?.text ?: "?"
        return SelectedLineInfo(
            segment = segment,
            startName = startName,
            endName = endName,
            startPoint = start?.point ?: segment.a,
            endPoint = end?.point ?: segment.b
        )
    }

    private fun nearestManhole(point: Pt): Manhole? {
        val nearest = manholes.minByOrNull { it.point.distanceTo(point) } ?: return null
        val tolerance = max(0.75, nearest.circle?.radius?.times(3.0) ?: 0.75)
        return nearest.takeIf { it.point.distanceTo(point) <= tolerance }
    }

    private fun nearestLabel(point: Pt): DxfText? {
        val pattern = Regex("^[A-Za-zÇĞİÖŞÜçğıöşü]+[0-9]+(?:-[0-9]+)*$")
        return drawing.texts
            .asSequence()
            .filter { pattern.matches(it.text.trim()) || it.layer.contains("BACA_ADI", true) }
            .map { it to it.point.distanceTo(point) }
            .filter { it.second <= 6.0 }
            .minByOrNull { it.second }
            ?.first
    }

    fun manholeLabels(): List<Manhole> = manholes
}
