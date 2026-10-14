package com.foton.dxfhelper

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

object MapPdfExporter {
    fun write(
        output: OutputStream,
        drawing: DxfDrawing,
        completedIds: Set<String>,
        mapName: String
    ) {
        val pdf = PdfDocument()
        try {
            // A4 yatay, 72 dpi puan mantığıyla.
            val pageWidth = 842
            val pageHeight = 595
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(25, 35, 48)
                textSize = 16f
                isFakeBoldText = true
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(70, 80, 92)
                textSize = 9f
            }
            val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(105, 125, 142)
                strokeWidth = 0.8f
                style = Paint.Style.STROKE
            }
            val donePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 170, 90)
                strokeWidth = 2.2f
                style = Paint.Style.STROKE
            }
            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(80, 100, 115)
                strokeWidth = 0.7f
                style = Paint.Style.STROKE
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(40, 50, 62)
                textSize = 6.8f
            }

            canvas.drawText("FOTONMAP GÖRÜNTÜLEME DURUM PLANI", 30f, 30f, titlePaint)
            canvas.drawText(mapName, 30f, 46f, subPaint)
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date())
            canvas.drawText(date, 650f, 30f, subPaint)
            canvas.drawText("Yeşil: tamamlandı   Gri: bekliyor", 650f, 46f, subPaint)

            val left = 30f
            val top = 62f
            val right = pageWidth - 30f
            val bottom = pageHeight - 28f
            val mapW = right - left
            val mapH = bottom - top
            val scale = min(mapW / drawing.width.toFloat(), mapH / drawing.height.toFloat())
            val drawW = drawing.width.toFloat() * scale
            val drawH = drawing.height.toFloat() * scale
            val ox = left + (mapW - drawW) / 2f
            val oy = top + (mapH - drawH) / 2f

            fun sx(x: Double): Float = ox + ((x - drawing.minX) * scale).toFloat()
            fun sy(y: Double): Float = oy + drawH - ((y - drawing.minY) * scale).toFloat()

            drawing.segments.forEach { seg ->
                val paint = if (completedIds.contains(seg.id)) donePaint else normalPaint
                canvas.drawLine(sx(seg.a.x), sy(seg.a.y), sx(seg.b.x), sy(seg.b.y), paint)
            }

            drawing.circles.forEach { c ->
                val r = (c.radius * scale).toFloat().coerceIn(0.8f, 4.5f)
                canvas.drawCircle(sx(c.center.x), sy(c.center.y), r, circlePaint)
            }

            val labelPattern = Regex("^[A-Za-zÇĞİÖŞÜçğıöşü]+[0-9]+(?:-[0-9]+)*$")
            drawing.texts.asSequence()
                .filter { labelPattern.matches(it.text.trim()) }
                .distinctBy { "${it.text.trim()}@${"%.3f".format(Locale.US, it.point.x)}:${"%.3f".format(Locale.US, it.point.y)}" }
                .forEach { t ->
                    canvas.drawText(t.text.trim(), sx(t.point.x) + 2f, sy(t.point.y) - 2f, labelPaint)
                }

            canvas.drawText(
                "Tamamlanan: ${completedIds.size} / ${drawing.segments.size}",
                30f,
                pageHeight - 10f,
                subPaint
            )

            pdf.finishPage(page)
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }
}
