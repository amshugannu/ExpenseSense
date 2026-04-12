package com.amshu.expensesense.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.amshu.expensesense.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

data class CategorySplit(
    val name: String,
    val amount: Double,
    val color: Int = 0,
)

data class PTransaction(
    val date: String,
    val merchantName: String,
    val category: String,
    val accountName: String,
    val amount: Double,
)

data class AnalyticsData(
    val month: String,
    val totalIncome: Double,
    val totalExpenses: Double,
    val transactions: List<PTransaction>,
    val categorySplits: List<CategorySplit>,
    val heatmapValues: List<Double>,
    val spendingPoints: List<Double>,
    val spendingLabels: List<String>,
)

// ─────────────────────────────────────────────────────────────────────────────
// PDF EXPORTER
// ─────────────────────────────────────────────────────────────────────────────

class ExpenseSensePdfExporter(private val context: Context) {

    private val PAGE_WIDTH = 595
    private val PAGE_HEIGHT = 842
    private val M = 36f
    private val CW = PAGE_WIDTH - M * 2

    // Professional Color Palette
    private val C_BG         = Color.parseColor("#F5F5F7")
    private val C_CARD       = Color.WHITE
    private val C_BRAND      = Color.parseColor("#5B4CF5")
    private val C_INCOME     = Color.parseColor("#1D9E75")
    private val C_EXPENSE    = Color.parseColor("#D85A30")
    private val C_ACCENT     = Color.parseColor("#378ADD")
    private val C_TEXT1      = Color.parseColor("#1A1A1A")
    private val C_TEXT2      = Color.parseColor("#666666")
    private val C_DIVIDER    = Color.parseColor("#EAEAEA")

    private val PIE_COLORS = listOf(
        Color.parseColor("#5B4CF5"),
        Color.parseColor("#378ADD"),
        Color.parseColor("#1D9E75"),
        Color.parseColor("#F7931A"),
        Color.parseColor("#D85A30")
    )

    fun exportToPdf(transactionsSource: List<Transaction>, cal: Calendar): File {
        // ─────────────────────────────────────────────────────────────────────────────
        // PART 9: FINAL VERIFICATION LOG (MANDATORY)
        // ─────────────────────────────────────────────────────────────────────────────
        android.util.Log.e("FINAL_PDF_DATA", "Transactions used in PDF: ${transactionsSource.size}")
        transactionsSource.forEach {
            android.util.Log.e("FINAL_PDF_DATA", "TX -> ${it.title}")
        }

        // Processing transactionsSource exclusively
        val transactions = transactionsSource
        android.util.Log.e("FINAL_PDF_DATA", "Transactions for PDF generation after mapping: ${transactions.size}")

        val totalIncome = transactions.filter { it.transactionType == Transaction.TYPE_INCOME }.sumOf { it.amount }
        val totalExpenses = transactions.filter { it.transactionType == Transaction.TYPE_EXPENSE }.sumOf { it.amount }

        val splits = transactions.filter { it.transactionType == Transaction.TYPE_EXPENSE }
            .groupBy { it.category }
            .map { (cat, list) -> CategorySplit(cat, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        val heatmap = mutableListOf<Double>()
        val dayCal = Calendar.getInstance()
        val spendByDay = transactions.filter { it.transactionType == Transaction.TYPE_EXPENSE }
            .groupBy { 
                dayCal.timeInMillis = it.timestamp
                dayCal.get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { it.value.sumOf { t -> t.amount } }

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val calClone = cal.clone() as Calendar
        calClone.set(Calendar.DAY_OF_MONTH, 1)
        val startDayOfWeek = calClone.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sun
        
        for (i in 0 until startDayOfWeek) {
            heatmap.add(-1.0)
        }

        for (day in 1..daysInMonth) {
            heatmap.add(spendByDay[day] ?: 0.0)
        }

        val spendingPoints = mutableListOf<Double>()
        val spendingLabels = mutableListOf<String>()
        val tempCal = cal.clone() as Calendar

        for (day in 1..daysInMonth) {
            spendingPoints.add(spendByDay[day] ?: 0.0)
            val ordinal = when {
                day in 11..13 -> "${day}th"
                day % 10 == 1 -> "${day}st"
                day % 10 == 2 -> "${day}nd"
                day % 10 == 3 -> "${day}rd"
                else -> "${day}th"
            }
            spendingLabels.add(ordinal)
        }

        val data = AnalyticsData(
            month = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time),
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            transactions = transactionsSource.sortedByDescending { it.timestamp }.map { // Correctly sorted single source
                PTransaction(
                    date = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(it.timestamp)),
                    merchantName = it.title,
                    category = it.category,
                    accountName = it.accountName,
                    amount = if (it.transactionType == Transaction.TYPE_EXPENSE) -it.amount else it.amount
                )
            },
            categorySplits = splits,
            heatmapValues = heatmap,
            spendingPoints = spendingPoints,
            spendingLabels = spendingLabels
        )

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(C_BG)
        android.util.Log.d("PDF_DEBUG", "Total prepared for PDF: ${data.transactions.size}")
        drawPage(canvas, data)

        document.finishPage(page)
        val file = saveToDownloads(document, data.month)
        document.close()

        return file
    }

    private fun drawPage(canvas: Canvas, data: AnalyticsData) {
        var y = M

        y = drawHeader(canvas, y, data)
        y += 24f

        y = drawSummaryCards(canvas, y, data)
        y += 30f

        y = drawLineChart(canvas, y, data)
        y += 30f

        y = drawBreakdownRow(canvas, y, data)
        y += 30f

        drawSection(canvas, y, "Recent Transactions")
        y += 20f
        drawTransactionTable(canvas, y, data.transactions)

        drawFooter(canvas)
    }

    private fun drawHeader(canvas: Canvas, y: Float, data: AnalyticsData): Float {
        val h = 60f
        drawRoundRectCard(canvas, M, y, CW, h)

        val logoBox = 34f
        val paintLogo = Paint().apply { color = C_BRAND }
        canvas.drawRoundRect(M + 12, y + 13, M + 12 + logoBox, y + 13 + logoBox, 8f, 8f, paintLogo)
        canvas.drawText("ES", M + 21, y + 36, paint(Color.WHITE, 14f, true))

        canvas.drawText("ExpenseSense", M + 60, y + 30, paint(C_TEXT1, 15f, true))
        canvas.drawText("Personal Finance Tracker", M + 60, y + 46, paint(C_TEXT2, 10f))

        canvas.drawText("ANALYTICS REPORT", PAGE_WIDTH - M - 12, y + 26, paint(C_TEXT2, 9f, true, Paint.Align.RIGHT))
        canvas.drawText(data.month, PAGE_WIDTH - M - 12, y + 42, paint(C_TEXT1, 12f, true, Paint.Align.RIGHT))
        val today = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Generated: $today", PAGE_WIDTH - M - 12, y + 54, paint(C_TEXT2, 8f, align = Paint.Align.RIGHT))

        return y + h
    }

    private fun drawSummaryCards(canvas: Canvas, y: Float, data: AnalyticsData): Float {
        val cardW = (CW - 24) / 3
        val cardH = 50f

        val cards = listOf(
            Triple("TOTAL INCOME", data.totalIncome, C_INCOME),
            Triple("TOTAL EXPENSE", data.totalExpenses, C_EXPENSE),
            Triple("NET BALANCE", data.totalIncome - data.totalExpenses, C_ACCENT)
        )

        cards.forEachIndexed { i, (label, amount, color) ->
            val x = M + i * (cardW + 12)
            drawRoundRectCard(canvas, x, y, cardW, cardH)
            
            val stripP = Paint().apply { this.color = color }
            canvas.drawRoundRect(x, y, x + 4, y + cardH, 4f, 4f, stripP)

            canvas.drawText(label, x + 12, y + 18, paint(C_TEXT2, 8f, true))
            canvas.drawText("₹${formatAmount(amount)}", x + 12, y + 38, paint(C_TEXT1, 14f, true))
        }

        return y + cardH
    }

    private fun drawLineChart(canvas: Canvas, y: Float, data: AnalyticsData): Float {
        val h = 160f
        drawRoundRectCard(canvas, M, y, CW, h)
        
        canvas.drawText("SPENDING OVER TIME", M + 16, y + 24, paint(C_TEXT1, 10f, true))

        val chartM = 40f
        val chartW = CW - chartM * 2
        val chartH = h - 60f
        val startX = M + chartM
        val startY = y + 40f + chartH

        android.util.Log.d("PDF_DEBUG", "LineChart input size (spendingPoints): ${data.spendingPoints.size}")
        if (data.spendingPoints.size >= 2) {
            val rawMax = data.spendingPoints.maxOrNull()?.coerceAtLeast(100.0) ?: 100.0
            val max = Math.ceil(rawMax / 100.0) * 100.0
            val mid = max / 2.0

            val yGridPaint = paint(C_DIVIDER, 1f)
            val yLabelPaint = paint(C_TEXT2, 8f, align = Paint.Align.RIGHT)

            canvas.drawText("0", startX - 8f, startY + 3f, yLabelPaint)
            canvas.drawLine(startX, startY, startX + chartW, startY, yGridPaint)

            val midY = startY - (chartH / 2)
            canvas.drawText(formatAmount(mid), startX - 8f, midY + 3f, yLabelPaint)
            canvas.drawLine(startX, midY, startX + chartW, midY, yGridPaint)

            val maxY = startY - chartH
            canvas.drawText(formatAmount(max), startX - 8f, maxY + 3f, yLabelPaint)
            canvas.drawLine(startX, maxY, startX + chartW, maxY, yGridPaint)

            val paintPath = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C_BRAND
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            val stepX = chartW / (data.spendingPoints.size - 1)

            data.spendingPoints.forEachIndexed { i, pt ->
                val px = startX + i * stepX
                val py = startY - (pt.toFloat() / max.toFloat() * chartH)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                
                canvas.drawCircle(px, py, 2.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_BRAND })
                
                if (pt > 0) {
                    canvas.drawText("₹${formatAmount(pt)}", px, py - 6f, paint(C_BRAND, 6.5f, true, Paint.Align.CENTER))
                }

                if ((i + 1) % 2 == 0) {
                    if (i < data.spendingLabels.size) {
                        canvas.drawText(data.spendingLabels[i], px, startY + 14, paint(C_TEXT2, 6.5f, align = Paint.Align.CENTER))
                    }
                }
            }
            canvas.drawPath(path, paintPath)
        } else {
            canvas.drawLine(startX, startY, startX + chartW, startY, paint(C_DIVIDER, 1f))
        }

        return y + h
    }

    private fun drawBreakdownRow(canvas: Canvas, y: Float, data: AnalyticsData): Float {
        val cardW = (CW - 12) / 2
        val h = 150f

        // Card 1: Pie
        drawRoundRectCard(canvas, M, y, cardW, h)
        canvas.drawText("CATEGORY BREAKDOWN", M + 16, y + 24, paint(C_TEXT1, 10f, true))

        val centerX = M + 60f
        val centerY = y + 85f
        val radius = 45f
        val rectF = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        var startAngle = -90f
        val total = data.categorySplits.sumOf { it.amount }.coerceAtLeast(1.0)
        
        android.util.Log.d("PDF_DEBUG", "PieChart input size (categorySplits): ${data.categorySplits.size}")
        if (data.categorySplits.isEmpty()) {
            android.util.Log.e("ERROR_DEBUG", "Pie Chart skipped: No category data available")
        }
        
        data.categorySplits.take(5).forEachIndexed { i, split ->
            val sweep = (split.amount / total * 360).toFloat()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 14f
                color = PIE_COLORS[i % PIE_COLORS.size]
            }
            canvas.drawArc(rectF, startAngle, sweep, false, p)
            
            val lx = centerX + radius + 15
            val ly = y + 45 + i * 18
            canvas.drawCircle(lx, ly - 3, 4f, p.apply { style = Paint.Style.FILL })
            canvas.drawText(ellipsize(split.name, 10), lx + 10, ly, paint(C_TEXT1, 9f))
            canvas.drawText("${(split.amount/total*100).toInt()}%", M + cardW - 16, ly, paint(C_TEXT2, 9f, align = Paint.Align.RIGHT))
            
            startAngle += sweep
        }

        // Card 2: Heatmap
        val x2 = M + cardW + 12
        drawRoundRectCard(canvas, x2, y, cardW, h)
        canvas.drawText("ACTIVITY MAP", x2 + 16, y + 24, paint(C_TEXT1, 10f, true))

        val cellSize = 12f
        val gap = 4f
        val hmStartX = x2 + (cardW - (7 * cellSize + 6 * gap)) / 2f + 4f
        val hmStartY = y + 62f

        val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
        daysOfWeek.forEachIndexed { col, text ->
            val cx = hmStartX + col * (cellSize + gap) + (cellSize / 2)
            canvas.drawText(text, cx, hmStartY - 8f, paint(C_TEXT2, 7f, align = Paint.Align.CENTER))
        }

        var startOfWeekPadding = 0
        while (startOfWeekPadding < data.heatmapValues.size && data.heatmapValues[startOfWeekPadding] == -1.0) {
            startOfWeekPadding++
        }

        val rows = Math.ceil(data.heatmapValues.size / 7.0).toInt()
        for (row in 0 until rows) {
            var firstValidDayInRow = -1
            for (c in 0..6) {
                val idx = row * 7 + c
                if (idx < data.heatmapValues.size && data.heatmapValues[idx] != -1.0) {
                    firstValidDayInRow = idx - startOfWeekPadding + 1
                    break
                }
            }
            if (firstValidDayInRow != -1) {
                val ry = hmStartY + row * (cellSize + gap)
                canvas.drawText(firstValidDayInRow.toString(), hmStartX - 6f, ry + 9f, paint(C_TEXT2, 7f, align = Paint.Align.RIGHT))
            }
        }

        data.heatmapValues.forEachIndexed { i, value ->
            if (value != -1.0) {
                val row = i / 7
                val col = i % 7
                val rx = hmStartX + col * (cellSize + gap)
                val ry = hmStartY + row * (cellSize + gap)
                
                val opacity = when {
                    value <= 0 -> 0.1f
                    value < 500 -> 0.3f
                    value < 2000 -> 0.6f
                    else -> 1.0f
                }
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
                    color = C_BRAND
                    alpha = (opacity * 255).toInt()
                }
                canvas.drawRoundRect(rx, ry, rx + cellSize, ry + cellSize, 3f, 3f, p)
            }
        }
        
        android.util.Log.d("PDF_DEBUG", "Heatmap input size (heatmapValues): ${data.heatmapValues.size}")
        if (data.heatmapValues.all { it <= 0.0 }) {
             android.util.Log.e("ERROR_DEBUG", "Heatmap might appear empty: all values are 0.0")
        }

        return y + h
    }

    private fun drawSection(canvas: Canvas, y: Float, title: String) {
        canvas.drawText(title.uppercase(), M, y, paint(C_TEXT2, 10f, true))
    }

    private fun drawTransactionTable(canvas: Canvas, y: Float, list: List<PTransaction>) {
        val colDate = M + 12
        val colMerchant = M + 70
        val colCat = M + 180
        val colAccount = M + 280
        val colAmt = PAGE_WIDTH - M - 12

        canvas.drawText("DATE", colDate, y, paint(C_TEXT2, 8f, true))
        canvas.drawText("MERCHANT", colMerchant, y, paint(C_TEXT2, 8f, true))
        canvas.drawText("CATEGORY", colCat, y, paint(C_TEXT2, 8f, true))
        canvas.drawText("ACCOUNT", colAccount, y, paint(C_TEXT2, 8f, true))
        canvas.drawText("AMOUNT", colAmt, y, paint(C_TEXT2, 8f, true, Paint.Align.RIGHT))

        val sorted = list.take(20) // Already prepared and mapped, using take(20) for table space
        var currentY = y + 15
        sorted.forEach { tx ->
            canvas.drawLine(M, currentY, PAGE_WIDTH - M, currentY, paint(C_DIVIDER, 0.5f))
            currentY += 24
            val midY = currentY - 8
            
            canvas.drawText(tx.date, colDate, midY, paint(C_TEXT2, 9f))
            canvas.drawText(ellipsize(tx.merchantName, 18), colMerchant, midY, paint(C_TEXT1, 10f, true))
            canvas.drawText(tx.category, colCat, midY, paint(C_TEXT2, 9f))
            canvas.drawText(tx.accountName, colAccount, midY, paint(C_TEXT2, 9f))
            
            val isNeg = tx.amount < 0
            val color = if (isNeg) C_EXPENSE else C_INCOME
            val prefix = if (isNeg) "-" else "+"
            canvas.drawText("$prefix₹${formatAmount(abs(tx.amount))}", colAmt, midY, paint(color, 11f, true, Paint.Align.RIGHT))
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val y = PAGE_HEIGHT - 40f
        canvas.drawLine(M, y, PAGE_WIDTH - M, y, paint(C_DIVIDER, 1f))
        canvas.drawText("ExpenseSense • expensesense.app • All data is private & encrypted", M, y + 20, paint(C_TEXT2, 8f))
        canvas.drawText("Page 1 of 1", PAGE_WIDTH - M, y + 20, paint(C_TEXT2, 8f, align = Paint.Align.RIGHT))
        val p = Paint().apply { color = C_BRAND }
        canvas.drawRect(M, (PAGE_HEIGHT - 6).toFloat(), PAGE_WIDTH - M,
            (PAGE_HEIGHT - 4).toFloat(), p)
    }

    private fun drawRoundRectCard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_CARD
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#15000000"))
        }
        canvas.drawRoundRect(x, y, x + w, y + h, 12f, 12f, p)
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = size
            this.textAlign = align
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun formatAmount(amount: Double): String {
        return String.format("%,.0f", amount)
    }

    private fun ellipsize(text: String, maxLength: Int): String {
        return if (text.length > maxLength) text.take(maxLength - 1) + "…" else text
    }

    private fun saveToDownloads(doc: PdfDocument, monthYear: String): File {
        val fileName = "ExpenseSense_Report_${monthYear.replace(" ", "_")}.pdf"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)!!
            context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        } else {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            FileOutputStream(file).use { doc.writeTo(it) }
            file
        }
    }
}