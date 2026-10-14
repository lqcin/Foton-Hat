package com.foton.dxfhelper

import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.StandardCharsets

object FotonMapParser {
    fun parse(input: InputStream): DxfDrawing {
        val raw = input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        require(root.optString("format").equals("FOTONMAP", ignoreCase = true)) {
            "Bu dosya FOTONMAP formatında değil"
        }

        val version = root.optInt("version", 1)
        require(version == 1) { "Desteklenmeyen FOTONMAP sürümü: $version" }

        val bounds = root.getJSONObject("bounds")
        val minX = bounds.optDouble("minX", 0.0)
        val minY = bounds.optDouble("minY", 0.0)
        val maxX = bounds.optDouble("maxX", 1.0)
        val maxY = bounds.optDouble("maxY", 1.0)
        require(maxX > minX && maxY > minY) { "FOTONMAP sınırları geçersiz" }

        val nodesArray = root.optJSONArray("nodes")
        val circles = mutableListOf<DxfCircle>()
        if (nodesArray != null) {
            for (i in 0 until nodesArray.length()) {
                val n = nodesArray.getJSONObject(i)
                val x = n.optDouble("x", 0.0)
                val y = n.optDouble("y", 0.0)
                val name = n.optString("name", "").trim()
                val handle = n.optString("handle", "N$i")
                val layer = n.optString("layer", "0")
                val radius = n.optDouble("radius", 0.25).coerceAtLeast(0.05)
                circles += DxfCircle(handle, layer, Pt(x, y), radius)
            }
        }

        val texts = mutableListOf<DxfText>()
        val textsArray = root.optJSONArray("texts")
        if (textsArray != null) {
            for (i in 0 until textsArray.length()) {
                val t = textsArray.getJSONObject(i)
                val txt = t.optString("text", "")
                if (txt.isBlank()) continue
                texts += DxfText(
                    handle = t.optString("handle", "T$i"),
                    layer = t.optString("layer", "0"),
                    point = Pt(t.optDouble("x", 0.0), t.optDouble("y", 0.0)),
                    text = txt,
                    height = t.optDouble("height", 0.25).takeIf { it > 0.0 } ?: 0.25,
                    rotation = t.optDouble("rotation", 0.0)
                )
            }
        }
        val segments = mutableListOf<DxfSegment>()
        val segArray = root.optJSONArray("segments")
            ?: error("FOTONMAP içinde segments alanı yok")
        require(segArray.length() > 0) { "FOTONMAP içinde tıklanabilir hat yok" }
        for (i in 0 until segArray.length()) {
            val s = segArray.getJSONObject(i)
            val a = Pt(s.optDouble("x1", 0.0), s.optDouble("y1", 0.0))
            val b = Pt(s.optDouble("x2", 0.0), s.optDouble("y2", 0.0))
            val length = s.optDouble("length", a.distanceTo(b))
            if (a.distanceTo(b) <= 0.000001) continue
            segments += DxfSegment(
                id = s.optString("id", "S$i"),
                handle = s.optString("handle", ""),
                layer = s.optString("layer", "0"),
                a = a,
                b = b,
                fromName = s.optString("from", "").trim(),
                toName = s.optString("to", "").trim(),
                lengthOverride = length.takeIf { it > 0.0 }
            )
        }

        val completedIds: Set<String>? =
            if (root.has("completed")) {
                val arr = root.optJSONArray("completed")
                if (arr == null) {
                    emptySet()
                } else {
                    buildSet {
                        for (i in 0 until arr.length()) {
                            val id = arr.optString(i, "").trim()
                            if (id.isNotBlank()) add(id)
                        }
                    }
                }
            } else {
                null
            }

        return DxfDrawing(
            segments = segments,
            circles = circles,
            texts = texts,
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY,
            sourceName = root.optString("source", ""),
            formatVersion = version,
            embeddedCompletedIds = completedIds
        )
    }
}
