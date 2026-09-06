package com.paychat.paychat.data.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.paychat.paychat.core.money.Money
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws a [StatementDocument] into a PDF file.
 *
 * Android's own PdfDocument is used rather than a library or a Cloud
 * Function: the statement is built entirely from data the device already
 * holds, so an export works offline and costs nothing to run.
 *
 * The layout is a fixed grid rather than a text engine. Amounts are drawn
 * right aligned against known column edges, which is what keeps a column of
 * figures readable, and long notes are clipped rather than wrapped so that a
 * row is always one line tall and the page break arithmetic stays simple.
 */
@Singleton
class StatementPdfWriter @Inject constructor() {

    fun write(document: StatementDocument, into: File): File {
        val pdf = PdfDocument()
        val page = PageWriter(pdf)

        page.header(document)
        document.sections.forEach { section ->
            page.section(section, showHeading = document.sections.size > 1)
        }
        if (document.sections.size > 1) page.grandTotal(document.total)
        page.finish()

        into.parentFile?.mkdirs()
        into.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return into
    }
}

/** A4 at 72 points to the inch, which is the unit PdfDocument works in. */
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val ROW_HEIGHT = 18f
private const val BOTTOM_LIMIT = PAGE_HEIGHT - MARGIN

/** Column right edges for the figures, and left edges for the text. */
private const val COL_DATE = MARGIN
private const val COL_DETAIL = MARGIN + 78f
private const val COL_AMOUNT_END = PAGE_WIDTH - MARGIN - 110f
private const val COL_BALANCE_END = PAGE_WIDTH - MARGIN

/**
 * Holds the one piece of state a paginated document really has: how far down
 * the current page we are, and which page that is.
 */
private class PageWriter(private val pdf: PdfDocument) {

    private var pageNumber = 0
    private var page: PdfDocument.Page = newPage()
    private var canvas: Canvas = page.canvas
    private var y = MARGIN

    private val title = paint(16f, bold = true)
    private val heading = paint(12f, bold = true)
    private val label = paint(9f, bold = true).apply { color = 0xFF666666.toInt() }
    private val body = paint(10f)
    private val bodyMuted = paint(10f).apply { color = 0xFF666666.toInt() }
    private val strong = paint(11f, bold = true)
    private val rule = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.6f }

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val stampFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)

    fun header(document: StatementDocument) {
        canvas.drawText(document.title, MARGIN, y + 14f, title)
        y += 26f
        canvas.drawText(
            "Generated " + stampFormat.format(Date(document.generatedAt)),
            MARGIN, y, bodyMuted
        )
        y += 20f
    }

    fun section(section: StatementSection, showHeading: Boolean) {
        // A heading with no rows under it at the foot of a page is worse than
        // a slightly short page, so the two are reserved together.
        space(ROW_HEIGHT * 3)

        if (showHeading) {
            canvas.drawText(section.counterparty, MARGIN, y, heading)
            canvas.drawText(section.phone, MARGIN + 220f, y, bodyMuted)
            y += 16f
        }

        columnLabels()

        if (section.lines.isEmpty()) {
            canvas.drawText("No accepted transactions.", COL_DATE, y, bodyMuted)
            y += ROW_HEIGHT
        } else {
            section.lines.forEach { line ->
                space(ROW_HEIGHT)
                row(
                    date = dateFormat.format(Date(line.entry.createdAt)),
                    detail = detailOf(line.entry.note, line.viewerIsPayer),
                    amount = line.effect.formatSigned(),
                    balance = line.runningBalance.formatSigned(),
                )
            }
        }

        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += 14f
        rightAlign("Closing balance", COL_AMOUNT_END, strong)
        rightAlign(section.closing.formatSigned(), COL_BALANCE_END, strong)
        y += 22f
    }

    fun grandTotal(total: Money) {
        space(ROW_HEIGHT * 2)
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += 16f
        canvas.drawText("Across all conversations", MARGIN, y, strong)
        rightAlign(total.formatSigned(), COL_BALANCE_END, strong)
    }

    fun finish() {
        pdf.finishPage(page)
    }

    // ------------------------------------------------------------- drawing

    private fun columnLabels() {
        canvas.drawText("DATE", COL_DATE, y, label)
        canvas.drawText("DETAIL", COL_DETAIL, y, label)
        rightAlign("AMOUNT", COL_AMOUNT_END, label)
        rightAlign("BALANCE", COL_BALANCE_END, label)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += 14f
    }

    private fun row(date: String, detail: String, amount: String, balance: String) {
        canvas.drawText(date, COL_DATE, y, body)
        canvas.drawText(clip(detail, COL_AMOUNT_END - COL_DETAIL - 8f), COL_DETAIL, y, body)
        rightAlign(amount, COL_AMOUNT_END, body)
        rightAlign(balance, COL_BALANCE_END, body)
        y += ROW_HEIGHT
    }

    private fun rightAlign(text: String, rightEdge: Float, paint: Paint) {
        canvas.drawText(text, rightEdge - paint.measureText(text), y, paint)
    }

    /** Direction is spelled out, because a sign alone is easy to misread. */
    private fun detailOf(note: String?, viewerIsPayer: Boolean): String {
        val direction = if (viewerIsPayer) "You gave" else "You received"
        return note?.takeIf { it.isNotBlank() }?.let { "$direction — $it" } ?: direction
    }

    private fun clip(text: String, width: Float): String {
        if (body.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && body.measureText(text.substring(0, end) + "…") > width) end--
        return text.substring(0, end) + "…"
    }

    /** Starts a new page when [needed] points will not fit on this one. */
    private fun space(needed: Float) {
        if (y + needed <= BOTTOM_LIMIT) return
        pdf.finishPage(page)
        page = newPage()
        canvas = page.canvas
        y = MARGIN
    }

    private fun newPage(): PdfDocument.Page {
        pageNumber += 1
        return pdf.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
    }
}

private fun paint(size: Float, bold: Boolean = false) = Paint().apply {
    isAntiAlias = true
    textSize = size
    color = 0xFF111111.toInt()
    typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
}
