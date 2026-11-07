package com.boxdev.boxwallet.util

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.monthsBetween
import com.boxdev.boxwallet.domain.model.total
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object ExcelReportGenerator {

    /**
     * Generates a multi-sheet Excel report and writes it to [file].
     *
     * Sheets:
     *  1. Transactions     — full detail with category, status, loan name, installment
     *  2. Summary          — income/expense totals, averages, savings rate, top categories
     *  3. By Category      — per-category income/expense breakdown
     *  4. Monthly Overview — month-by-month summary (skipped for single-month ranges)
     *  5. Loan Installments— only when loan transactions exist in the range
     */
    fun create(
        file: File,
        transactions: List<Transaction>,
        categories: Map<Long, String>,
        loans: Map<Long, Loan>,
        startDate: LocalDate,
        endDate: LocalDate
    ): File {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy")

        XSSFWorkbook().use { wb ->
            val boldStyle = wb.createCellStyle().apply {
                val f = wb.createFont(); f.bold = true; setFont(f)
            }
            val headerStyle = wb.createCellStyle().apply {
                val f = wb.createFont(); f.bold = true; setFont(f)
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }
            val titleStyle = wb.createCellStyle().apply {
                val f = wb.createFont(); f.bold = true; setFont(f)
                fillForegroundColor = IndexedColors.PALE_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            buildTransactionsSheet(wb, transactions, categories, loans, dateFmt, timeFmt, headerStyle)
            buildSummarySheet(wb, transactions, categories, startDate, endDate, dateFmt, boldStyle, titleStyle)
            buildCategorySheet(wb, transactions, categories, headerStyle, boldStyle)
            buildMonthlySheet(wb, transactions, startDate, endDate, monthFmt, headerStyle, boldStyle)
            if (loans.isNotEmpty()) {
                buildLoanOverviewSheet(wb, loans, dateFmt, headerStyle, boldStyle, titleStyle)
            }
            if (transactions.any { it.loanId != null }) {
                buildLoansSheet(wb, transactions, loans, dateFmt, headerStyle, boldStyle)
            }

            FileOutputStream(file).use { wb.write(it) }
        }
        return file
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 1: Transactions
    // ──────────────────────────────────────────────────────────────

    private fun buildTransactionsSheet(
        wb: XSSFWorkbook,
        transactions: List<Transaction>,
        categories: Map<Long, String>,
        loans: Map<Long, Loan>,
        dateFmt: DateTimeFormatter,
        timeFmt: DateTimeFormatter,
        headerStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Movements")

        val headers = listOf("#", "Date", "Time", "Type", "Category", "Description", "Amount", "Status", "Loan", "Installment")
        sheet.header(headers, headerStyle)

        transactions.forEachIndexed { idx, tx ->
            val row = sheet.createRow(idx + 1)
            val loan = tx.loanId?.let { loans[it] }
            val installmentLabel = if (tx.installmentNumber != null && loan != null) {
                "${tx.installmentNumber}/${loan.totalInstallments}"
            } else ""

            row.createCell(0).setCellValue((idx + 1).toDouble())
            row.createCell(1).setCellValue(tx.date.format(dateFmt))
            row.createCell(2).setCellValue(tx.date.format(timeFmt))
            row.createCell(3).setCellValue(if (tx.type == TransactionType.INCOME) "Income" else "Expense")
            row.createCell(4).setCellValue(tx.categoryId?.let { categories[it] } ?: "Uncategorized")
            row.createCell(5).setCellValue(tx.description)
            row.createCell(6).setCellValue(tx.amount)
            row.createCell(7).setCellValue(if (tx.isCleared) "Cleared" else "Pending")
            row.createCell(8).setCellValue(loan?.name ?: "")
            row.createCell(9).setCellValue(installmentLabel)
        }

        sheet.widths(4, 12, 7, 10, 20, 32, 14, 10, 24, 13)
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 2: Summary
    // ──────────────────────────────────────────────────────────────

    private fun buildSummarySheet(
        wb: XSSFWorkbook,
        transactions: List<Transaction>,
        categories: Map<Long, String>,
        startDate: LocalDate,
        endDate: LocalDate,
        dateFmt: DateTimeFormatter,
        boldStyle: XSSFCellStyle,
        titleStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Summary")

        val income = transactions.filter { it.type == TransactionType.INCOME }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }
        val totalIncome = income.sumOf { it.amount }
        val totalExpense = expense.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1
        val savingsRate = if (totalIncome > 0) ((totalIncome - totalExpense) / totalIncome) * 100 else 0.0

        var r = 0

        fun title(label: String) {
            sheet.createRow(r++).createCell(0).also { it.setCellValue(label); it.cellStyle = titleStyle }
        }
        fun row(label: String, value: String, bold: Boolean = false) {
            sheet.createRow(r++).apply {
                createCell(0).setCellValue(label)
                createCell(1).also { it.setCellValue(value); if (bold) it.cellStyle = boldStyle }
            }
        }
        fun rowNum(label: String, value: Double, bold: Boolean = false) {
            sheet.createRow(r++).apply {
                createCell(0).setCellValue(label)
                createCell(1).also { it.setCellValue(value); if (bold) it.cellStyle = boldStyle }
            }
        }

        // Period
        title("PERIOD")
        row("Date Range", "${startDate.format(dateFmt)} to ${endDate.format(dateFmt)}")
        rowNum("Total Days", totalDays.toDouble())
        rowNum("Total Movements", transactions.size.toDouble())
        r++

        // Income
        title("INCOME")
        rowNum("Total Income", totalIncome, bold = true)
        rowNum("Movements", income.size.toDouble())
        rowNum("Average per Movement", if (income.isNotEmpty()) totalIncome / income.size else 0.0)
        rowNum("Cleared", income.filter { it.isCleared }.sumOf { it.amount })
        rowNum("Pending", income.filter { !it.isCleared }.sumOf { it.amount })
        r++

        // Expenses
        title("EXPENSES")
        rowNum("Total Expenses", totalExpense, bold = true)
        rowNum("Movements", expense.size.toDouble())
        rowNum("Average per Movement", if (expense.isNotEmpty()) totalExpense / expense.size else 0.0)
        rowNum("Cleared", expense.filter { it.isCleared }.sumOf { it.amount })
        rowNum("Pending", expense.filter { !it.isCleared }.sumOf { it.amount })
        r++

        // Balance
        title("BALANCE")
        rowNum("Net Balance", netBalance, bold = true)
        row("Savings Rate", "${String.format("%.1f", savingsRate)}%", bold = true)
        r++

        // Top 5 expense categories
        val topCategories = expense
            .groupBy { it.categoryId?.let { id -> categories[id] } ?: "Uncategorized" }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .take(5)

        if (topCategories.isNotEmpty()) {
            title("TOP EXPENSE CATEGORIES")
            topCategories.forEach { (cat, amt) -> rowNum(cat, amt) }
        }

        sheet.widths(30, 20)
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 3: By Category
    // ──────────────────────────────────────────────────────────────

    private fun buildCategorySheet(
        wb: XSSFWorkbook,
        transactions: List<Transaction>,
        categories: Map<Long, String>,
        headerStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("By Category")

        val headers = listOf("Category", "Income", "Income Txns", "Expenses", "Expense Txns", "Net", "% of Expenses")
        sheet.header(headers, headerStyle)

        val totalExpense = transactions.total(TransactionType.EXPENSE)

        // groupBy keeps first-seen order, matching the previous distinct() ordering
        val byCategory = transactions.groupBy { it.categoryId }
        byCategory.entries.forEachIndexed { idx, (catId, catTxs) ->
            val catName = catId?.let { categories[it] } ?: "Uncategorized"
            val incTxs = catTxs.filter { it.type == TransactionType.INCOME }
            val expTxs = catTxs.filter { it.type == TransactionType.EXPENSE }
            val incTotal = incTxs.sumOf { it.amount }
            val expTotal = expTxs.sumOf { it.amount }
            val pct = if (totalExpense > 0) (expTotal / totalExpense) * 100 else 0.0

            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(catName)
            row.createCell(1).setCellValue(incTotal)
            row.createCell(2).setCellValue(incTxs.size.toDouble())
            row.createCell(3).setCellValue(expTotal)
            row.createCell(4).setCellValue(expTxs.size.toDouble())
            row.createCell(5).setCellValue(incTotal - expTotal)
            row.createCell(6).setCellValue(String.format("%.1f%%", pct))
        }

        // Totals row
        val totalIncome = transactions.total(TransactionType.INCOME)
        val totalsRow = sheet.createRow(byCategory.size + 2)
        totalsRow.createCell(0).also { it.setCellValue("TOTAL"); it.cellStyle = boldStyle }
        totalsRow.createCell(1).also { it.setCellValue(totalIncome); it.cellStyle = boldStyle }
        totalsRow.createCell(3).also { it.setCellValue(totalExpense); it.cellStyle = boldStyle }
        totalsRow.createCell(5).also { it.setCellValue(totalIncome - totalExpense); it.cellStyle = boldStyle }

        sheet.widths(22, 14, 13, 14, 14, 14, 15)
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 4: Monthly Overview (skipped for single-month ranges)
    // ──────────────────────────────────────────────────────────────

    private fun buildMonthlySheet(
        wb: XSSFWorkbook,
        transactions: List<Transaction>,
        startDate: LocalDate,
        endDate: LocalDate,
        monthFmt: DateTimeFormatter,
        headerStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle
    ) {
        val startYM = YearMonth.from(startDate)
        val endYM = YearMonth.from(endDate)
        if (startYM == endYM) return

        val sheet = wb.createSheet("Monthly Overview")

        val headers = listOf("Month", "Income", "Expenses", "Net Balance", "Movements")
        sheet.header(headers, headerStyle)

        val months = monthsBetween(startYM, endYM)
        val byMonth = transactions.groupBy { YearMonth.from(it.date) }

        months.forEachIndexed { idx, ym ->
            val monthTxs = byMonth[ym].orEmpty()
            val inc = monthTxs.total(TransactionType.INCOME)
            val exp = monthTxs.total(TransactionType.EXPENSE)
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(ym.atDay(1).format(monthFmt))
            row.createCell(1).setCellValue(inc)
            row.createCell(2).setCellValue(exp)
            row.createCell(3).setCellValue(inc - exp)
            row.createCell(4).setCellValue(monthTxs.size.toDouble())
        }

        val totalIncome = transactions.total(TransactionType.INCOME)
        val totalExpense = transactions.total(TransactionType.EXPENSE)
        val totalsRow = sheet.createRow(months.size + 2)
        totalsRow.createCell(0).also { it.setCellValue("TOTAL"); it.cellStyle = boldStyle }
        totalsRow.createCell(1).also { it.setCellValue(totalIncome); it.cellStyle = boldStyle }
        totalsRow.createCell(2).also { it.setCellValue(totalExpense); it.cellStyle = boldStyle }
        totalsRow.createCell(3).also { it.setCellValue(totalIncome - totalExpense); it.cellStyle = boldStyle }
        totalsRow.createCell(4).also { it.setCellValue(transactions.size.toDouble()); it.cellStyle = boldStyle }

        sheet.widths(14, 14, 14, 14, 14)
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 5: Loans Overview
    // ──────────────────────────────────────────────────────────────

    private fun buildLoanOverviewSheet(
        wb: XSSFWorkbook,
        loans: Map<Long, Loan>,
        dateFmt: DateTimeFormatter,
        headerStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle,
        titleStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Loans")

        val headers = listOf(
            "Loan", "Description", "Type", "Total Amount",
            "Installments", "Paid Amount", "Remaining Amount", "Progress",
            "Installment Amount", "Start Date", "Next Due Date", "Status"
        )
        sheet.header(headers, headerStyle)

        val sortedLoans = loans.values.sortedBy { it.name }

        sortedLoans.forEachIndexed { idx, loan ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(loan.name)
            row.createCell(1).setCellValue(loan.description)
            row.createCell(2).setCellValue(loan.loanType.name.lowercase().replaceFirstChar { it.uppercase() })
            row.createCell(3).setCellValue(loan.totalAmount)
            row.createCell(4).setCellValue("${loan.paidInstallments}/${loan.totalInstallments}")
            row.createCell(5).setCellValue(loan.settledAmount)
            row.createCell(6).setCellValue(loan.remainingAmount)
            row.createCell(7).setCellValue(String.format("%.1f%%", loan.progressFraction * 100))
            row.createCell(8).setCellValue(loan.installmentAmount)
            row.createCell(9).setCellValue(loan.startDate.format(dateFmt))
            row.createCell(10).setCellValue(loan.nextDueDate?.format(dateFmt) ?: "—")
            row.createCell(11).setCellValue(if (loan.isComplete) "Complete" else "In Progress")
        }

        // Portfolio summary section
        val summaryStartRow = sortedLoans.size + 2
        var r = summaryStartRow

        sheet.createRow(r++).createCell(0).also { it.setCellValue("PORTFOLIO SUMMARY"); it.cellStyle = titleStyle }

        val totalBorrowed = sortedLoans.sumOf { it.totalAmount }
        val totalPaid = sortedLoans.sumOf { it.settledAmount }
        val totalRemaining = sortedLoans.sumOf { it.remainingAmount }
        val activeCount = sortedLoans.count { !it.isComplete }
        val completedCount = sortedLoans.count { it.isComplete }

        sheet.createRow(r++).apply {
            createCell(0).setCellValue("Total Borrowed")
            createCell(1).also { it.setCellValue(totalBorrowed); it.cellStyle = boldStyle }
        }
        sheet.createRow(r++).apply {
            createCell(0).setCellValue("Total Paid")
            createCell(1).also { it.setCellValue(totalPaid); it.cellStyle = boldStyle }
        }
        sheet.createRow(r++).apply {
            createCell(0).setCellValue("Total Remaining")
            createCell(1).also { it.setCellValue(totalRemaining); it.cellStyle = boldStyle }
        }
        sheet.createRow(r++).apply {
            createCell(0).setCellValue("Active Loans")
            createCell(1).setCellValue(activeCount.toDouble())
        }
        sheet.createRow(r).apply {
            createCell(0).setCellValue("Completed Loans")
            createCell(1).setCellValue(completedCount.toDouble())
        }

        sheet.widths(24, 28, 10, 14, 10, 14, 16, 18, 11, 18, 12, 14, 13)
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 6: Loan Installments
    // ──────────────────────────────────────────────────────────────

    private fun buildLoansSheet(
        wb: XSSFWorkbook,
        transactions: List<Transaction>,
        loans: Map<Long, Loan>,
        dateFmt: DateTimeFormatter,
        headerStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Loan Installments")

        val headers = listOf("Loan", "Installment", "of Total", "Amount", "Date", "Status")
        sheet.header(headers, headerStyle)

        val loanTxs = transactions
            .filter { it.loanId != null }
            .sortedWith(compareBy({ loans[it.loanId]?.name ?: "" }, { it.installmentNumber ?: 0 }))

        loanTxs.forEachIndexed { idx, tx ->
            val loan = tx.loanId?.let { loans[it] }
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(loan?.name ?: "Unknown")
            row.createCell(1).setCellValue((tx.installmentNumber ?: 0).toDouble())
            row.createCell(2).setCellValue((loan?.totalInstallments ?: 0).toDouble())
            row.createCell(3).setCellValue(tx.amount)
            row.createCell(4).setCellValue(tx.date.format(dateFmt))
            row.createCell(5).setCellValue(if (tx.isCleared) "Paid" else "Pending")
        }

        // Per-loan subtotals
        if (loanTxs.isNotEmpty()) {
            var subtotalRow = loanTxs.size + 2
            loanTxs.groupBy { it.loanId }
                .forEach { (loanId, txs) ->
                    val loan = loanId?.let { loans[it] } ?: return@forEach
                    val paid = txs.filter { it.isCleared }.sumOf { it.amount }
                    val pending = txs.filter { !it.isCleared }.sumOf { it.amount }
                    val row = sheet.createRow(subtotalRow++)
                    row.createCell(0).also { it.setCellValue("${loan.name} — Subtotal"); it.cellStyle = boldStyle }
                    row.createCell(3).also { it.setCellValue(paid + pending); it.cellStyle = boldStyle }
                    row.createCell(5).also { it.setCellValue("Paid: ${"%.2f".format(paid)} / Pending: ${"%.2f".format(pending)}") }
                }
        }

        sheet.widths(24, 13, 10, 14, 12, 32)
    }
    private fun Sheet.header(headers: List<String>, style: XSSFCellStyle) {
        val row = createRow(0)
        headers.forEachIndexed { i, h -> row.createCell(i).also { it.setCellValue(h); it.cellStyle = style } }
    }

    /** Column widths in characters, from the first column on. */
    private fun Sheet.widths(vararg chars: Int) =
        chars.forEachIndexed { i, c -> setColumnWidth(i, 256 * c) }
}
