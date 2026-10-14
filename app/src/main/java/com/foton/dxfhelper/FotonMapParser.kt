package com.foton.dxfhelper

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

object FotonMapParser {

    fun parse(input: InputStream): DxfDrawing {
        val raw =
            input.bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }

        val root = JSONObject(raw)

        require(
            root.optString("format")
                .equals("FOTONMAP", ignoreCase = true)
        ) {
            "Bu dosya FOTONMAP formatında değil"
        }

        // İleri sürümlerde bildiğimiz alanları okumaya devam et.
        val version =
            root.optInt("version", 1)
                .coerceAtLeast(1)

        val circles =
            parseNodes(root.optJSONArray("nodes"))

        val texts =
            parseTexts(root.optJSONArray("texts"))

        val segmentArray =
            root.optJSONArray("segments")
                ?: root.optJSONArray("lines")
                ?: JSONArray()

        val segments =
            parseSegments(segmentArray)

        require(segments.isNotEmpty()) {
            "FOTONMAP içinde okunabilir/tıklanabilir hat bulunamadı"
        }

        val calculated =
            calculateBounds(
                segments,
                circles,
                texts
            )

        val sourceBounds =
            root.optJSONObject("bounds")

        val minX =
            finite(
                sourceBounds?.optDouble("minX"),
                calculated.minX
            )

        val minY =
            finite(
                sourceBounds?.optDouble("minY"),
                calculated.minY
            )

        var maxX =
            finite(
                sourceBounds?.optDouble("maxX"),
                calculated.maxX
            )

        var maxY =
            finite(
                sourceBounds?.optDouble("maxY"),
                calculated.maxY
            )

        // Karmaşık/eski dosyada bozuk bounds varsa dosyayı reddetmek yerine düzelt.
        if (maxX <= minX) {
            maxX = calculated.maxX
        }

        if (maxY <= minY) {
            maxY = calculated.maxY
        }

        val completedIds =
            parseCompleted(root)

        val originObject =
            root.optJSONObject("origin")

        val projectionObject =
            root.optJSONObject("projection")

        val originX =
            finite(
                originObject?.optDouble("x"),
                0.0
            )

        val originY =
            finite(
                originObject?.optDouble("y"),
                0.0
            )

        val coordinateSystem =
            root.optString(
                "coordinateSystem",
                ""
            )

        val epsgValue =
            projectionObject?.optInt(
                "epsg",
                root.optInt("epsg", 0)
            )
                ?: root.optInt("epsg", 0)

        val epsg =
            epsgValue
                .takeIf { it > 0 }

        val centralMeridian =
            finite(
                projectionObject?.optDouble(
                    "centralMeridian",
                    root.optDouble(
                        "centralMeridian",
                        30.0
                    )
                ),
                30.0
            )

        return DxfDrawing(
            segments = segments,
            circles = circles,
            texts = texts,
            minX = minX,
            minY = minY,
            maxX = max(maxX, minX + 0.001),
            maxY = max(maxY, minY + 0.001),
            sourceName = root.optString("source", ""),
            formatVersion = version,
            originX = originX,
            originY = originY,
            coordinateSystem = coordinateSystem,
            epsg = epsg,
            centralMeridian = centralMeridian,
            embeddedCompletedIds = completedIds
        )
    }

    private fun parseNodes(
        array: JSONArray?
    ): List<DxfCircle> {
        if (array == null) return emptyList()

        val out =
            mutableListOf<DxfCircle>()

        for (i in 0 until array.length()) {
            runCatching {
                val n =
                    array.getJSONObject(i)

                val x =
                    finite(
                        n.optDouble("x"),
                        0.0
                    )

                val y =
                    finite(
                        n.optDouble("y"),
                        0.0
                    )

                val radius =
                    finite(
                        n.optDouble("radius"),
                        0.25
                    ).coerceAtLeast(0.01)

                out +=
                    DxfCircle(
                        handle =
                            n.optString(
                                "handle",
                                "N$i"
                            ),
                        layer =
                            n.optString(
                                "layer",
                                "0"
                            ),
                        center =
                            Pt(x, y),
                        radius =
                            radius,
                        sourceColorRgb =
                            colorRgb(n),
                        sourceColorAci =
                            colorAci(n)
                    )
            }
        }

        return out
    }

    private fun parseTexts(
        array: JSONArray?
    ): List<DxfText> {
        if (array == null) return emptyList()

        val out =
            mutableListOf<DxfText>()

        for (i in 0 until array.length()) {
            runCatching {
                val t =
                    array.getJSONObject(i)

                val value =
                    t.optString(
                        "text",
                        ""
                    )

                if (value.isBlank()) {
                    return@runCatching
                }

                out +=
                    DxfText(
                        handle =
                            t.optString(
                                "handle",
                                "T$i"
                            ),
                        layer =
                            t.optString(
                                "layer",
                                "0"
                            ),
                        point =
                            Pt(
                                finite(
                                    t.optDouble("x"),
                                    0.0
                                ),
                                finite(
                                    t.optDouble("y"),
                                    0.0
                                )
                            ),
                        text =
                            value,
                        height =
                            finite(
                                t.optDouble("height"),
                                0.25
                            ).takeIf { it > 0.0 }
                                ?: 0.25,
                        rotation =
                            finite(
                                t.optDouble("rotation"),
                                0.0
                            ),
                        sourceColorRgb =
                            colorRgb(t),
                        sourceColorAci =
                            colorAci(t)
                    )
            }
        }

        return out
    }

    private fun parseSegments(
        array: JSONArray
    ): List<DxfSegment> {
        val out =
            mutableListOf<DxfSegment>()

        for (i in 0 until array.length()) {
            runCatching {
                val s =
                    array.getJSONObject(i)

                val a =
                    Pt(
                        finite(
                            s.optDouble("x1"),
                            Double.NaN
                        ),
                        finite(
                            s.optDouble("y1"),
                            Double.NaN
                        )
                    )

                val b =
                    Pt(
                        finite(
                            s.optDouble("x2"),
                            Double.NaN
                        ),
                        finite(
                            s.optDouble("y2"),
                            Double.NaN
                        )
                    )

                if (
                    !a.x.isFinite() ||
                    !a.y.isFinite() ||
                    !b.x.isFinite() ||
                    !b.y.isFinite()
                ) {
                    return@runCatching
                }

                val geometric =
                    a.distanceTo(b)

                if (geometric <= 0.000001) {
                    return@runCatching
                }

                val length =
                    finite(
                        s.optDouble("length"),
                        geometric
                    ).takeIf { it > 0.0 }
                        ?: geometric

                out +=
                    DxfSegment(
                        id =
                            s.optString(
                                "id",
                                "S$i"
                            ),
                        handle =
                            s.optString(
                                "handle",
                                ""
                            ),
                        layer =
                            s.optString(
                                "layer",
                                "0"
                            ),
                        a =
                            a,
                        b =
                            b,
                        fromName =
                            s.optString(
                                "from",
                                ""
                            ).trim(),
                        toName =
                            s.optString(
                                "to",
                                ""
                            ).trim(),
                        lengthOverride =
                            length,
                        sourceColorRgb =
                            colorRgb(s),
                        sourceColorAci =
                            colorAci(s)
                    )
            }
        }

        return out
    }

    private fun parseCompleted(
        root: JSONObject
    ): Set<String>? {
        if (!root.has("completed")) {
            return null
        }

        val array =
            root.optJSONArray("completed")
                ?: return emptySet()

        return buildSet {
            for (i in 0 until array.length()) {
                val id =
                    array.optString(
                        i,
                        ""
                    ).trim()

                if (id.isNotBlank()) {
                    add(id)
                }
            }
        }
    }

    private fun colorRgb(
        obj: JSONObject
    ): Int? {
        if (!obj.has("colorRgb")) {
            return null
        }

        val v =
            obj.optInt(
                "colorRgb",
                -1
            )

        return v
            .takeIf {
                it in 0..0xFFFFFF
            }
    }

    private fun colorAci(
        obj: JSONObject
    ): Int? {
        if (!obj.has("colorAci")) {
            return null
        }

        val v =
            obj.optInt(
                "colorAci",
                -1
            )

        return v
            .takeIf {
                it in 1..255
            }
    }

    private fun finite(
        value: Double?,
        fallback: Double
    ): Double {
        val v =
            value ?: fallback

        return if (v.isFinite()) {
            v
        } else {
            fallback
        }
    }

    private data class Bounds(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double
    )

    private fun calculateBounds(
        segments: List<DxfSegment>,
        circles: List<DxfCircle>,
        texts: List<DxfText>
    ): Bounds {
        var minX =
            Double.POSITIVE_INFINITY

        var minY =
            Double.POSITIVE_INFINITY

        var maxX =
            Double.NEGATIVE_INFINITY

        var maxY =
            Double.NEGATIVE_INFINITY

        fun add(
            x: Double,
            y: Double
        ) {
            if (
                !x.isFinite() ||
                !y.isFinite()
            ) return

            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }

        segments.forEach {
            add(it.a.x, it.a.y)
            add(it.b.x, it.b.y)
        }

        circles.forEach {
            add(
                it.center.x - it.radius,
                it.center.y - it.radius
            )

            add(
                it.center.x + it.radius,
                it.center.y + it.radius
            )
        }

        texts.forEach {
            add(
                it.point.x,
                it.point.y
            )
        }

        if (
            !minX.isFinite() ||
            !minY.isFinite() ||
            !maxX.isFinite() ||
            !maxY.isFinite()
        ) {
            return Bounds(
                0.0,
                0.0,
                1.0,
                1.0
            )
        }

        return Bounds(
            minX,
            minY,
            max(maxX, minX + 0.001),
            max(maxY, minY + 0.001)
        )
    }
}
