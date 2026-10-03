package br.ensaios.cliente.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** Alinhamento do texto numa célula. */
enum class Align { LEFT, CENTER, RIGHT }

/** Célula de tabela. */
data class Cell(
    val text: String,
    val bold: Boolean = false,
    val align: Align = Align.CENTER,
    val color: Int = Color.BLACK,
    val fill: Int? = null,
)

fun c(text: String, bold: Boolean = false, align: Align = Align.CENTER, color: Int = Color.BLACK, fill: Int? = null) =
    Cell(text, bold, align, color, fill)

/**
 * Desenho simples de PDF (A4) com textos, linhas e tabelas, usando o PdfDocument do próprio Android.
 * Coordenadas em pontos (1/72 pol.). Retrato: 595 × 842. Paisagem: 842 × 595.
 */
class PdfKit(private val landscapeDefault: Boolean = false) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var curLandscape = landscapeDefault
    var width = 595f
        private set
    var height = 842f
        private set
    val margin = 28f

    /** Posição vertical atual (onde o próximo conteúdo começa). */
    var y = 0f

    val canvas: Canvas get() = page!!.canvas

    /** Chamado no início de cada página (cabeçalho). */
    var onNewPage: ((PdfKit) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = Color.rgb(90, 90, 90)
    }
    private val fillPaint = Paint().apply { style = Paint.Style.FILL }

    fun newPage(landscape: Boolean = curLandscape) {
        page?.let { doc.finishPage(it) }
        curLandscape = landscape
        width = if (landscape) 842f else 595f
        height = if (landscape) 595f else 842f
        pageNumber++
        page = doc.startPage(PdfDocument.PageInfo.Builder(width.toInt(), height.toInt(), pageNumber).create())
        y = margin
        onNewPage?.invoke(this)
    }

    /** Garante espaço; se não houver, começa outra página. */
    fun ensure(space: Float) {
        if (page == null || y + space > height - margin) newPage()
    }

    private fun setText(size: Float, bold: Boolean, color: Int) {
        paint.textSize = size
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.color = color
        paint.style = Paint.Style.FILL
    }

    fun textWidth(s: String, size: Float, bold: Boolean = false): Float {
        setText(size, bold, Color.BLACK)
        return paint.measureText(s)
    }

    /** Corta o texto com "…" para caber na largura. */
    private fun fit(s: String, maxW: Float): String {
        if (paint.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && paint.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    fun text(x: Float, baseline: Float, s: String, size: Float = 9f, bold: Boolean = false, align: Align = Align.LEFT, color: Int = Color.BLACK, maxW: Float = 10000f) {
        setText(size, bold, color)
        val t = fit(s, maxW)
        val w = paint.measureText(t)
        val x0 = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2
            Align.RIGHT -> x - w
        }
        canvas.drawText(t, x0, baseline, paint)
    }

    fun rect(x: Float, top: Float, w: Float, h: Float, fill: Int? = null) {
        if (fill != null) {
            fillPaint.color = fill
            canvas.drawRect(x, top, x + w, top + h, fillPaint)
        }
        canvas.drawRect(x, top, x + w, top + h, line)
    }

    fun hline(x1: Float, x2: Float, yy: Float) = canvas.drawLine(x1, yy, x2, yy, line)

    /** Título centralizado com espaço abaixo. */
    fun title(s: String, size: Float = 13f) {
        ensure(size + 10)
        text(width / 2, y + size, s, size, bold = true, align = Align.CENTER)
        y += size + 8
    }

    /** Linha de texto simples. */
    fun line(s: String, size: Float = 9f, bold: Boolean = false, color: Int = Color.BLACK) {
        ensure(size + 6)
        text(margin, y + size, s, size, bold, color = color, maxW = width - 2 * margin)
        y += size + 5
    }

    fun space(h: Float) {
        y += h
    }

    /**
     * Tabela com larguras proporcionais ([weights]); [header] repete em cada página.
     * Cada linha tem a mesma altura ([rowH]).
     */
    fun table(weights: List<Float>, header: List<Cell>?, rows: List<List<Cell>>, rowH: Float = 15f, size: Float = 8f, x: Float = margin, w: Float = width - 2 * margin) {
        val total = weights.sum()
        val widths = weights.map { it / total * w }

        fun drawRow(cells: List<Cell>, headerRow: Boolean) {
            var cx = x
            cells.forEachIndexed { i, cell ->
                val cw = widths.getOrElse(i) { 0f }
                rect(cx, y, cw, rowH, cell.fill ?: if (headerRow) Color.rgb(225, 228, 232) else null)
                val tx = when (cell.align) {
                    Align.LEFT -> cx + 3
                    Align.CENTER -> cx + cw / 2
                    Align.RIGHT -> cx + cw - 3
                }
                text(tx, y + rowH / 2 + size / 2.8f, cell.text, size, cell.bold || headerRow, cell.align, cell.color, cw - 4)
                cx += cw
            }
            y += rowH
        }

        if (header != null) {
            ensure(rowH * 2)
            drawRow(header, true)
        }
        rows.forEach { r ->
            if (y + rowH > height - margin) {
                newPage()
                if (header != null) drawRow(header, true)
            }
            drawRow(r, false)
        }
    }

    /** Duas linhas de assinatura no fim. */
    fun signatures(left: String, right: String) {
        ensure(50f)
        y += 34
        val w = (width - 2 * margin) / 2 - 30
        hline(margin + 10, margin + 10 + w, y)
        hline(width - margin - 10 - w, width - margin - 10, y)
        text(margin + 10 + w / 2, y + 11, left, 8f, align = Align.CENTER)
        text(width - margin - 10 - w / 2, y + 11, right, 8f, align = Align.CENTER)
        y += 16
    }

    fun save(file: File): File {
        page?.let { doc.finishPage(it) }
        page = null
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
