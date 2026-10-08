package com.foton.dxfhelper

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import kotlin.math.max
import kotlin.math.min

object DxfParser {
    private data class PairCode(val code: Int, val value: String)
    private data class RawEntity(val type: String, val pairs: List<PairCode>)

    fun parse(input: InputStream): DxfDrawing {
        val charset = runCatching { Charset.forName("windows-1254") }.getOrDefault(Charsets.UTF_8)
        val reader = BufferedReader(InputStreamReader(input, charset))
        val lines = reader.readLines()
        val pairs = ArrayList<PairCode>(lines.size / 2)
        var i = 0
        while (i + 1 < lines.size) {
            val code = lines[i].trim().toIntOrNull()
            if (code != null) pairs += PairCode(code, lines[i + 1].trimEnd())
            i += 2
        }

        val entities = extractEntities(pairs)
        val segments = mutableListOf<DxfSegment>()
        val circles = mutableListOf<DxfCircle>()
        val texts = mutableListOf<DxfText>()

        var e = 0
        while (e < entities.size) {
            val entity = entities[e]
            when (entity.type) {
                "LINE" -> parseLine(entity)?.let { segments += it }
                "CIRCLE" -> parseCircle(entity)?.let { circles += it }
                "TEXT" -> parseText(entity)?.let { texts += it }
                "MTEXT" -> parseMText(entity)?.let { texts += it }
                "LWPOLYLINE" -> segments += parseLwPolyline(entity)
                "POLYLINE" -> {
                    val handle = value(entity.pairs, 5) ?: "POLY_$e"
                    val layer = value(entity.pairs, 8) ?: "0"
                    val vertices = mutableListOf<Pt>()
                    var j = e + 1
                    while (j < entities.size && entities[j].type != "SEQEND") {
                        if (entities[j].type == "VERTEX") {
                            val x = doubleValue(entities[j].pairs, 10)
                            val y = doubleValue(entities[j].pairs, 20)
                            if (x != null && y != null) vertices += Pt(x, y)
                        }
                        j++
                    }
                    for (k in 0 until max(0, vertices.size - 1)) {
                        segments += DxfSegment("$handle:$k", handle, layer, vertices[k], vertices[k + 1])
                    }
                    val flags = intValue(entity.pairs, 70) ?: 0
                    if ((flags and 1) == 1 && vertices.size > 2) {
                        segments += DxfSegment("$handle:${vertices.size - 1}", handle, layer, vertices.last(), vertices.first())
                    }
                    e = j
                }
            }
            e++
        }

        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        fun include(p: Pt) {
            minX = min(minX, p.x); minY = min(minY, p.y)
            maxX = max(maxX, p.x); maxY = max(maxY, p.y)
        }
        segments.forEach { include(it.a); include(it.b) }
        circles.forEach {
            include(Pt(it.center.x - it.radius, it.center.y - it.radius))
            include(Pt(it.center.x + it.radius, it.center.y + it.radius))
        }
        texts.forEach { include(it.point) }

        if (!minX.isFinite()) {
            minX = 0.0; minY = 0.0; maxX = 100.0; maxY = 100.0
        }
        return DxfDrawing(segments, circles, texts, minX, minY, maxX, maxY)
    }

    private fun extractEntities(pairs: List<PairCode>): List<RawEntity> {
        var inEntities = false
        val out = mutableListOf<RawEntity>()
        var i = 0
        while (i < pairs.size) {
            val p = pairs[i]
            if (!inEntities) {
                if (p.code == 0 && p.value.trim() == "SECTION" && i + 1 < pairs.size &&
                    pairs[i + 1].code == 2 && pairs[i + 1].value.trim() == "ENTITIES") {
                    inEntities = true
                    i += 2
                    continue
                }
                i++
                continue
            }
            if (p.code == 0 && p.value.trim() == "ENDSEC") break
            if (p.code != 0) { i++; continue }
            val type = p.value.trim()
            val data = mutableListOf<PairCode>()
            i++
            while (i < pairs.size && pairs[i].code != 0) {
                data += pairs[i]
                i++
            }
            out += RawEntity(type, data)
        }
        return out
    }

    private fun parseLine(e: RawEntity): DxfSegment? {
        val x1 = doubleValue(e.pairs, 10) ?: return null
        val y1 = doubleValue(e.pairs, 20) ?: return null
        val x2 = doubleValue(e.pairs, 11) ?: return null
        val y2 = doubleValue(e.pairs, 21) ?: return null
        val handle = value(e.pairs, 5) ?: "LINE_${x1}_${y1}_${x2}_${y2}"
        return DxfSegment(handle, handle, value(e.pairs, 8) ?: "0", Pt(x1, y1), Pt(x2, y2))
    }

    private fun parseCircle(e: RawEntity): DxfCircle? {
        val x = doubleValue(e.pairs, 10) ?: return null
        val y = doubleValue(e.pairs, 20) ?: return null
        val r = doubleValue(e.pairs, 40) ?: return null
        return DxfCircle(value(e.pairs, 5) ?: "C_${x}_${y}", value(e.pairs, 8) ?: "0", Pt(x, y), r)
    }

    private fun parseText(e: RawEntity): DxfText? {
        val x = doubleValue(e.pairs, 10) ?: return null
        val y = doubleValue(e.pairs, 20) ?: return null
        val text = value(e.pairs, 1)?.trim().orEmpty()
        if (text.isBlank()) return null
        return DxfText(value(e.pairs, 5) ?: "T_${x}_${y}_$text", value(e.pairs, 8) ?: "0", Pt(x, y), text, doubleValue(e.pairs, 40) ?: 1.0)
    }

    private fun parseMText(e: RawEntity): DxfText? {
        val x = doubleValue(e.pairs, 10) ?: return null
        val y = doubleValue(e.pairs, 20) ?: return null
        val parts = e.pairs.filter { it.code == 1 || it.code == 3 }.map { it.value }
        val text = parts.joinToString("").replace("\\P", " ").trim()
        if (text.isBlank()) return null
        return DxfText(value(e.pairs, 5) ?: "MT_${x}_${y}", value(e.pairs, 8) ?: "0", Pt(x, y), text, doubleValue(e.pairs, 40) ?: 1.0)
    }

    private fun parseLwPolyline(e: RawEntity): List<DxfSegment> {
        val handle = value(e.pairs, 5) ?: "LWP_${e.hashCode()}"
        val layer = value(e.pairs, 8) ?: "0"
        val vertices = mutableListOf<Pt>()
        var currentX: Double? = null
        for (p in e.pairs) {
            when (p.code) {
                10 -> currentX = p.value.trim().toDoubleOrNull()
                20 -> {
                    val y = p.value.trim().toDoubleOrNull()
                    val x = currentX
                    if (x != null && y != null) vertices += Pt(x, y)
                    currentX = null
                }
            }
        }
        val out = mutableListOf<DxfSegment>()
        for (i in 0 until max(0, vertices.size - 1)) {
            out += DxfSegment("$handle:$i", handle, layer, vertices[i], vertices[i + 1])
        }
        val flags = intValue(e.pairs, 70) ?: 0
        if ((flags and 1) == 1 && vertices.size > 2) {
            out += DxfSegment("$handle:${vertices.size - 1}", handle, layer, vertices.last(), vertices.first())
        }
        return out
    }

    private fun value(pairs: List<PairCode>, code: Int): String? = pairs.lastOrNull { it.code == code }?.value?.trim()
    private fun doubleValue(pairs: List<PairCode>, code: Int): Double? = value(pairs, code)?.toDoubleOrNull()
    private fun intValue(pairs: List<PairCode>, code: Int): Int? = value(pairs, code)?.toIntOrNull()
}
