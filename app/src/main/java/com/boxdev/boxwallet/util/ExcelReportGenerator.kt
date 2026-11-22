package com.boxdev.boxwallet.util

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.local.entity.isLoan
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.accountLabel
import com.boxdev.boxwallet.domain.model.inReports
import com.boxdev.boxwallet.domain.model.monthsBetween
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.util.XlsxWorkbook.Sheet
import com.boxdev.boxwallet.util.XlsxWorkbook.Style
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object ExcelReportGenerator {

    /** Localized titles of the currency columns; the caller resolves them from string resources. */
    data class CurrencyTitles(
        val originalAmount: String = "Original amount",
        val currency: String = "Currency",
        val rate: String = "Rate",
        val mainAmount: String = "Amount in main currency",
    )

    /**
     * Generates a multi-sheet Excel report and writes it to [file].
     *
     * Sheets:
     *  1. Transactions     — full detail with account, category and status; loan movements are left out
     *  2. Summary          — income/expense totals, averages, savings rate, top categories
     *  3. By Category      — per-category income/expense breakdown
     *  4. Monthly Overview — month-by-month summary (skipped for single-month ranges)
     *  5. Accounts         — each account's balance (as the app shows it) in its own currency, from [accounts]
     */
    fun create(
        file: File,
        transactions: List<Transaction>,
        categories: Map<Long, String>,
        accounts: List<AccountWithBalance>,
        startDate: LocalDate,
        endDate: LocalDate,
        mainCurrency: Currency,
        titles: CurrencyTitles = CurrencyTitles(),
    ): File {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy")

        val wb = XlsxWorkbook()
        val boldStyle = Style.BOLD
        val headerStyle = Style.HEADER
        val titleStyle = Style.TITLE

        // A card's second currency is named after its card ("Visa BAC · USD"), as in the app.
        val byId = accounts.associate { it.account.id to it.account }
        val accountNames = accounts.associate { (account) ->
            account.id to accountLabel(account, account.parentAccountId?.let(byId::get))
        }
        buildTransactionsSheet(wb, transactions.filterNot { it.type.isLoan }, categories, accountNames, dateFmt, timeFmt, headerStyle, titles)
        // Balance adjustments are listed on the Movements sheet only; every total and breakdown skips them.
        val reportable = transactions.inReports()
        buildSummarySheet(wb, reportable, categories, startDate, endDate, dateFmt, boldStyle, titleStyle)
        buildCategorySheet(wb, reportable, categories, headerStyle, boldStyle)
        buildMonthlySheet(wb, reportable, startDate, endDate, monthFmt, headerStyle, boldStyle)
        if (accounts.isNotEmpty()) {
            buildAccountsSheet(wb, accounts, accountNames, headerStyle)
        }

        FileOutputStream(file).buffered().use { wb.write(it) }
        return file
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 1: Transactions
    // ──────────────────────────────────────────────────────────────

    private fun buildTransactionsSheet(
        wb: XlsxWorkbook,
        transactions: List<Transaction>,
        categories: Map<Long, String>,
        accountNames: Map<Long, String>,
        dateFmt: DateTimeFormatter,
        timeFmt: DateTimeFormatter,
        headerStyle: Style,
        titles: CurrencyTitles
    ) {
        val sheet = wb.createSheet("Movements")

        val headers = listOf("#", "Date", "Time", "Account", "Type", "Category", "Description", "Amount", "Status") +
            listOf(titles.originalAmount, titles.currency, titles.rate, titles.mainAmount)
        sheet.header(headers, headerStyle)

        transactions.forEachIndexed { idx, tx ->
            val row = sheet.createRow(idx + 1)

            row.createCell(0).setCellValue((idx + 1).toDouble())
            row.createCell(1).setCellValue(tx.date.format(dateFmt))
            row.createCell(2).setCellValue(tx.date.format(timeFmt))
            row.createCell(3).setCellValue(accountNames[tx.accountId] ?: "")
            row.createCell(4).setCellValue(when (tx.type) {
                TransactionType.INCOME -> "Income"
                TransactionType.EXPENSE -> "Expense"
                TransactionType.ADJUSTMENT_IN, TransactionType.ADJUSTMENT_OUT -> "Adjustment"
                TransactionType.TRANSFER_OUT -> "Transfer out"
                TransactionType.TRANSFER_IN -> "Transfer in"
                TransactionType.LOAN_IN -> "Loan in"
                TransactionType.LOAN_OUT -> "Loan out"
            })
            row.createCell(5).setCellValue(tx.categoryId?.let { categories[it] } ?: "Uncategorized")
            row.createCell(6).setCellValue(tx.description)
            row.createCell(7).setCellValue(tx.amount.cell())
            row.createCell(8).setCellValue(if (tx.isCleared) "Cleared" else "Pending")
            row.createCell(9).setCellValue(tx.original.toMajor())
            row.createCell(10).setCellValue(tx.original.currency.currencyCode)
            row.createCell(11).setCellValue(effectiveRate(tx))
            row.createCell(12).setCellValue(tx.amount.toMajor())
        }

        sheet.widths(4, 12, 7, 20, 12, 20, 32, 14, 10, 16, 10, 12, 24)
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 5: Accounts
    // ──────────────────────────────────────────────────────────────

    /**
     * One row per account (named by [names]) with the balance the app shows, in the account's own currency:
     * cleared movements, or for a card every movement dated through today.
     */
    private fun buildAccountsSheet(
        wb: XlsxWorkbook,
        accounts: List<AccountWithBalance>,
        names: Map<Long, String>,
        headerStyle: Style,
    ) {
        val sheet = wb.createSheet("Accounts")
        sheet.header(listOf("Account", "Currency", "Balance"), headerStyle)
        accounts.forEachIndexed { idx, (account, balance) ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(names[account.id] ?: account.name)
            row.createCell(1).setCellValue(account.currency.currencyCode)
            row.createCell(2).setCellValue(balance.toMajor())
        }
        sheet.widths(24, 10, 18)
    }

    // ──────────────────────────────────────────────────────────────
    // Sheet 2: Summary
    // ──────────────────────────────────────────────────────────────

    private fun buildSummarySheet(
        wb: XlsxWorkbook,
        transactions: List<Transaction>,
        categories: Map<Long, String>,
        startDate: LocalDate,
        endDate: LocalDate,
        dateFmt: DateTimeFormatter,
        boldStyle: Style,
        titleStyle: Style
    ) {
        val sheet = wb.createSheet("Summary")

        val income = transactions.filter { it.type == TransactionType.INCOME }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }
        val totalIncome = income.sumMajor()
        val totalExpense = expense.sumMajor()
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
        rowNum("Cleared", income.filter { it.isCleared }.sumMajor())
        rowNum("Pending", income.filter { !it.isCleared }.sumMajor())
        r++

        // Expenses
        title("EXPENSES")
        rowNum("Total Expenses", totalExpense, bold = true)
        rowNum("Movements", expense.size.toDouble())
        rowNum("Average per Movement", if (expense.isNotEmpty()) totalExpense / expense.size else 0.0)
        rowNum("Cleared", expense.filter { it.isCleared }.sumMajor())
        rowNum("Pending", expense.filter { !it.isCleared }.sumMajor())
        r++

        // Balance
        title("BALANCE")
        rowNum("Net Balance", netBalance, bold = true)
        row("Savings Rate", "${String.format("%.1f", savingsRate)}%", bold = true)
        r++

        // Top 5 expense categories
        val topCategories = expense
            .groupBy { it.categoryId?.let { id -> categories[id] } ?: "Uncategorized" }
            .mapValues { (_, txs) -> txs.sumMajor() }
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
        wb: XlsxWorkbook,
        transactions: List<Transaction>,
        categories: Map<Long, String>,
        headerStyle: Style,
        boldStyle: Style
    ) {
        val sheet = wb.createSheet("By Category")

        val headers = listOf("Category", "Income", "Income Txns", "Expenses", "Expense Txns", "Net", "% of Expenses")
        sheet.header(headers, headerStyle)

        val totalExpense = transactions.totalMajor(TransactionType.EXPENSE)

        // groupBy keeps first-seen order, matching the previous distinct() ordering
        val byCategory = transactions.groupBy { it.categoryId }
        byCategory.entries.forEachIndexed { idx, (catId, catTxs) ->
            val catName = catId?.let { categories[it] } ?: "Uncategorized"
            val incTxs = catTxs.filter { it.type == TransactionType.INCOME }
            val expTxs = catTxs.filter { it.type == TransactionType.EXPENSE }
            val incTotal = incTxs.sumMajor()
            val expTotal = expTxs.sumMajor()
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
        val totalIncome = transactions.totalMajor(TransactionType.INCOME)
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
        wb: XlsxWorkbook,
        transactions: List<Transaction>,
        startDate: LocalDate,
        endDate: LocalDate,
        monthFmt: DateTimeFormatter,
        headerStyle: Style,
        boldStyle: Style
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
            val inc = monthTxs.totalMajor(TransactionType.INCOME)
            val exp = monthTxs.totalMajor(TransactionType.EXPENSE)
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(ym.atDay(1).format(monthFmt))
            row.createCell(1).setCellValue(inc)
            row.createCell(2).setCellValue(exp)
            row.createCell(3).setCellValue(inc - exp)
            row.createCell(4).setCellValue(monthTxs.size.toDouble())
        }

        val totalIncome = transactions.totalMajor(TransactionType.INCOME)
        val totalExpense = transactions.totalMajor(TransactionType.EXPENSE)
        val totalsRow = sheet.createRow(months.size + 2)
        totalsRow.createCell(0).also { it.setCellValue("TOTAL"); it.cellStyle = boldStyle }
        totalsRow.createCell(1).also { it.setCellValue(totalIncome); it.cellStyle = boldStyle }
        totalsRow.createCell(2).also { it.setCellValue(totalExpense); it.cellStyle = boldStyle }
        totalsRow.createCell(3).also { it.setCellValue(totalIncome - totalExpense); it.cellStyle = boldStyle }
        totalsRow.createCell(4).also { it.setCellValue(transactions.size.toDouble()); it.cellStyle = boldStyle }

        sheet.widths(14, 14, 14, 14, 14)
    }

    private fun Sheet.header(headers: List<String>, style: Style) {
        val row = createRow(0)
        headers.forEachIndexed { i, h -> row.createCell(i).also { it.setCellValue(h); it.cellStyle = style } }
    }

    /** Column widths in characters, from the first column on. */
    private fun Sheet.widths(vararg chars: Int) =
        chars.forEachIndexed { i, c -> setColumnWidth(i, c) }

    /** Effective original -> main rate: account rate × main rate, 6 decimals. */
    private fun effectiveRate(tx: Transaction): BigDecimal =
        tx.accountRate.value.multiply(tx.mainRate.value).setScale(6, RoundingMode.HALF_EVEN)

    // Amounts are exact until the cell: sums run on BigDecimal major units, then become a cell number.
    private fun Money.cell(): Double = toMajor().toDouble()

    private fun List<Transaction>.sumMajor(): Double = sumOf { it.amount.toMajor() }.toDouble()

    private fun List<Transaction>.totalMajor(type: TransactionType): Double = filter { it.type == type }.sumMajor()
}
