package com.example.planyourtrip.ui.common.share

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.theme.PdfColors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Draws [TripPdfContent] on A4 pages with Android's [PdfDocument] and shares the file through the app's
 * FileProvider. The only PDF / FileProvider code of the app. Older shared PDFs are deleted first.
 */
class AndroidTripPdfExporter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TripPdfExporter {

    override suspend fun export(content: TripPdfContent, fileName: String): SharedPdf = withContext(ioDispatcher) {
        try {
            val dir = File(context.cacheDir, SHARED_DIR)
            dir.listFiles()?.forEach { it.delete() }
            if (!dir.isDirectory && !dir.mkdirs()) error("Can't create $dir")
            val file = File(dir, fileName)
            TripPdfRenderer(context).render(content, file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            SharedPdf(uri.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Creating the trip PDF failed", e)
            throw e
        }
    }

    private companion object {
        const val TAG = "TripPdf"

        /** Must match `res/xml/file_paths.xml`. */
        const val SHARED_DIR = "shared_pdfs"
    }
}

/** A measured piece of the page body; drawn at [top] with the content's left edge at x = 0. */
private class Block(val height: Float, val keepWithNext: Boolean = false, val draw: Canvas.() -> Unit)

private class TripPdfRenderer(private val context: Context) {
    private val res = context.resources

    private val regular = typeface(R.font.inter_regular)
    private val semiBold = typeface(R.font.inter_semibold)
    private val bold = typeface(R.font.inter_bold)

    fun render(content: TripPdfContent, file: File) {
        val blocks = blocks(content)
        val pages = paginate(blocks.map { PdfBlockSize(it.height, it.keepWithNext) }, BODY_HEIGHT)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                val canvas = pdfPage.canvas
                var top = MARGIN
                for (blockIndex in page) {
                    val block = blocks[blockIndex]
                    // A block taller than a page (a giant note) is cut at the footer; nothing else ever is.
                    canvas.withClip(0f, MARGIN, PAGE_WIDTH.toFloat(), MARGIN + BODY_HEIGHT) {
                        withTranslation(MARGIN, top) { block.draw(this) }
                    }
                    top += block.height
                }
                drawFooter(canvas, index + 1, pages.size)
                document.finishPage(pdfPage)
            }
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    // ---------- Blocks ----------

    private fun blocks(content: TripPdfContent): List<Block> = buildList {
        add(header(content))
        content.costs?.let { addAll(costBlocks(it)) }
        val days = content.days
        if (days == null) {
            add(textBlock(res.getString(R.string.pdf_no_activities), paint(regular, BODY, PdfColors.textMuted), top = SECTION_GAP))
        } else {
            add(sectionTitle(res.getString(R.string.pdf_day_by_day)))
            days.forEach { day -> addAll(dayBlocks(day)) }
        }
    }

    private fun header(content: TripPdfContent): Block {
        val padding = 24f
        val inner = CONTENT_WIDTH - 2 * padding
        val title = layout(content.destination, paint(bold, 26f, PdfColors.onHero), inner)
        val length = res.getQuantityString(R.plurals.trip_length_days, content.lengthDays, content.lengthDays)
        val datesText = res.getString(
            R.string.home_dates_with_length,
            TripFormatting.dateRange(content.startDate, content.endDate),
            length,
        )
        val dates = layout(datesText, paint(regular, 13f, PdfColors.onHeroMuted), inner)
        val boxHeight = padding + title.height + 8f + dates.height + padding
        return Block(boxHeight) {
            val box = RectF(0f, 0f, CONTENT_WIDTH, boxHeight)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, CONTENT_WIDTH, boxHeight, PdfColors.heroStart, PdfColors.heroEnd, Shader.TileMode.CLAMP)
            }
            drawRoundRect(box, 16f, 16f, fill)
            drawLayout(title, padding, padding)
            drawLayout(dates, padding, padding + title.height + 8f)
        }
    }

    private fun costBlocks(costs: CostSummary): List<Block> = buildList {
        add(sectionTitle(res.getString(R.string.pdf_trip_cost)))
        val label = paint(regular, BODY, PdfColors.textMuted)
        val value = paint(semiBold, BODY, PdfColors.text)
        add(row(res.getString(R.string.pdf_total_spent), shekels(costs.spentAgorot), label, value))
        costs.budgetAgorot?.let { add(row(res.getString(R.string.pdf_budget), shekels(it), label, value)) }
        costs.difference?.let { difference ->
            val (text, color) = when (difference) {
                is BudgetDifference.Under ->
                    res.getString(R.string.pdf_under_budget, shekels(difference.agorot)) to PdfColors.underBudget
                is BudgetDifference.Over ->
                    res.getString(R.string.pdf_over_budget, shekels(difference.agorot)) to PdfColors.overBudget
                BudgetDifference.OnBudget -> res.getString(R.string.pdf_on_budget) to PdfColors.underBudget
            }
            add(textBlock(text, paint(semiBold, BODY, color), top = 4f, bottom = 4f))
        }
        if (costs.categories.isNotEmpty()) {
            add(textBlock(res.getString(R.string.pdf_by_category), paint(semiBold, BODY, PdfColors.text), top = 14f, bottom = 4f, keepWithNext = true))
            costs.categories.forEach { total ->
                add(
                    tableRow(
                        res.getString(total.category.labelRes()),
                        shekels(total.amountAgorot),
                        Money.formatPercent(total.percent),
                    )
                )
            }
        }
    }

    private fun dayBlocks(day: PdfDay): List<Block> = buildList {
        val title = res.getString(R.string.pdf_day_title, day.number, TripFormatting.weekdayDayMonth(day.date))
        val heading = layout(title, paint(semiBold, 14f, PdfColors.heading), CONTENT_WIDTH)
        val top = 16f
        add(
            Block(top + heading.height + 6f + 1f + 4f, keepWithNext = true) {
                drawLayout(heading, 0f, top)
                val y = top + heading.height + 6f
                drawLine(0f, y, CONTENT_WIDTH, y, linePaint())
            }
        )
        if (day.activities.isEmpty()) {
            add(textBlock(res.getString(R.string.pdf_nothing_planned), paint(regular, BODY, PdfColors.textMuted), top = 6f, bottom = 4f))
        } else {
            day.activities.forEach { add(activityBlock(it)) }
        }
    }

    private fun activityBlock(activity: PdfActivity): Block {
        val timeWidth = 48f
        val textWidth = CONTENT_WIDTH - timeWidth
        val time = activity.time?.let { layout(TripFormatting.time(it), paint(semiBold, BODY, PdfColors.text), timeWidth) }
        val title = layout(activity.title, paint(semiBold, BODY, PdfColors.text), textWidth)
        val meta = SpannableStringBuilder().apply {
            append(res.getString(activity.type.labelRes()), ForegroundColorSpan(PdfColors.activityType(activity.type)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            activity.location?.let { append(" · ").append(it) }
        }
        val metaLayout = layout(meta, paint(regular, SMALL, PdfColors.textMuted), textWidth)
        val note = activity.note?.let { layout(it, paint(regular, SMALL, PdfColors.textMuted), textWidth) }
        val top = 8f
        val textHeight = title.height + 2f + metaLayout.height + (note?.let { 3f + it.height } ?: 0f)
        return Block(top + textHeight + 4f) {
            time?.let { drawLayout(it, 0f, top) }
            var y = top
            drawLayout(title, timeWidth, y)
            y += title.height + 2f
            drawLayout(metaLayout, timeWidth, y)
            y += metaLayout.height
            note?.let { drawLayout(it, timeWidth, y + 3f) }
        }
    }

    // ---------- Building blocks ----------

    private fun sectionTitle(text: String): Block {
        val layout = layout(text, paint(bold, 18f, PdfColors.text), CONTENT_WIDTH)
        return Block(SECTION_GAP + layout.height + 4f, keepWithNext = true) { drawLayout(layout, 0f, SECTION_GAP) }
    }

    private fun textBlock(text: CharSequence, paint: TextPaint, top: Float = 0f, bottom: Float = 0f, keepWithNext: Boolean = false): Block {
        val layout = layout(text, paint, CONTENT_WIDTH)
        return Block(top + layout.height + bottom, keepWithNext) { drawLayout(layout, 0f, top) }
    }

    /** "Total spent ............ ₪2,550": label on the left, value on the right. */
    private fun row(label: String, value: String, labelPaint: TextPaint, valuePaint: TextPaint): Block {
        val valueWidth = 140f
        val labelLayout = layout(label, labelPaint, CONTENT_WIDTH - valueWidth)
        val valueLayout = layout(value, valuePaint, valueWidth, Layout.Alignment.ALIGN_OPPOSITE)
        val top = 4f
        return Block(top + maxOf(labelLayout.height, valueLayout.height) + 2f) {
            drawLayout(labelLayout, 0f, top)
            drawLayout(valueLayout, CONTENT_WIDTH - valueWidth, top)
        }
    }

    /** Category | amount | percent, with a thin line under it. */
    private fun tableRow(category: String, amount: String, percent: String): Block {
        val percentWidth = 60f
        val amountWidth = 110f
        val categoryWidth = CONTENT_WIDTH - amountWidth - percentWidth
        val text = paint(regular, BODY, PdfColors.text)
        val categoryLayout = layout(category, text, categoryWidth)
        val amountLayout = layout(amount, paint(semiBold, BODY, PdfColors.text), amountWidth, Layout.Alignment.ALIGN_OPPOSITE)
        val percentLayout = layout(percent, paint(regular, BODY, PdfColors.textMuted), percentWidth, Layout.Alignment.ALIGN_OPPOSITE)
        val top = 6f
        val lineY = top + maxOf(categoryLayout.height, amountLayout.height, percentLayout.height) + 6f
        return Block(lineY + 1f) {
            drawLayout(categoryLayout, 0f, top)
            drawLayout(amountLayout, categoryWidth, top)
            drawLayout(percentLayout, categoryWidth + amountWidth, top)
            drawLine(0f, lineY, CONTENT_WIDTH, lineY, linePaint())
        }
    }

    private fun drawFooter(canvas: Canvas, page: Int, pageCount: Int) {
        val paint = paint(regular, 9f, PdfColors.textMuted)
        val half = CONTENT_WIDTH / 2
        val left = layout(res.getString(R.string.pdf_footer), paint, half)
        val right = layout(res.getString(R.string.pdf_page, page, pageCount), paint, half, Layout.Alignment.ALIGN_OPPOSITE)
        val lineY = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT + 8f
        canvas.drawLine(MARGIN, lineY, MARGIN + CONTENT_WIDTH, lineY, linePaint())
        canvas.drawLayout(left, MARGIN, lineY + 8f)
        canvas.drawLayout(right, MARGIN + half, lineY + 8f)
    }

    // ---------- Text ----------

    private fun shekels(agorot: Long): String = res.getString(R.string.money_shekels, Money.formatAgorot(agorot))

    /**
     * Wrapping text in any script: the paragraph direction comes from its first strong character, so Hebrew or
     * Arabic lays out right to left (ALIGN_NORMAL then means the right edge) and mixed text gets bidi ordering.
     */
    private fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
        .setAlignment(alignment)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setIncludePad(false)
        .setLineSpacing(0f, 1.15f)
        .build()

    private fun Canvas.drawLayout(layout: StaticLayout, x: Float, y: Float) = withTranslation(x, y) { layout.draw(this) }

    private fun paint(typeface: Typeface, size: Float, @ColorInt color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        this.color = color
    }

    private fun linePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PdfColors.divider
        strokeWidth = 1f
    }

    /**
     * Inter for Latin text, with the system fonts as fallback for every character Inter doesn't cover (Hebrew,
     * Arabic, CJK, emoji...). API 29+ sets the fallback explicitly; older versions add it to font resources
     * automatically.
     */
    private fun typeface(@FontRes font: Int): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val inter = Font.Builder(res, font).build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(inter).build())
                .setStyle(inter.style)
                .setSystemFallback("sans-serif")
                .build()
        } else {
            ResourcesCompat.getFont(context, font) ?: Typeface.DEFAULT
        }

    private companion object {
        /** A4 in PostScript points (1/72 inch). */
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val FOOTER_HEIGHT = 32f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val BODY_HEIGHT = PAGE_HEIGHT - 2 * MARGIN - FOOTER_HEIGHT
        const val SECTION_GAP = 24f
        const val BODY = 11f
        const val SMALL = 9.5f
    }
}
