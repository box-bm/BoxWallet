package com.boxdev.boxwallet.util

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import java.math.BigDecimal
import java.util.Currency
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class ExcelReportGeneratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val gtq = Currency.getInstance("GTQ")

    private fun movement(minor: Long, date: LocalDateTime, type: TransactionType) = Money(minor, gtq).let {
        Transaction(
            accountId = 1, type = type, date = date,
            original = it, accountAmount = it, accountRate = ExchangeRate.IDENTITY,
            amount = it, mainRate = ExchangeRate.IDENTITY,
        )
    }

    private val loan = Loan(
        id = 7,
        name = "Car",
        totalAmount = Money(120_000, gtq),
        totalInstallments = 12,
        startDate = LocalDateTime.of(2026, 1, 1, 0, 0),
        paidInstallments = 1
    )

    private val transactions = listOf(
        movement(300_000, LocalDateTime.of(2026, 1, 5, 9, 0), TransactionType.INCOME).copy(categoryId = 1),
        movement(10_000, LocalDateTime.of(2026, 2, 3, 12, 30), TransactionType.EXPENSE)
            .copy(description = "Rent & <bills>", categoryId = 2, loanId = 7, installmentNumber = 1, isCleared = true)
    )

    private fun sheetNames(file: java.io.File): List<String> = ZipFile(file).use { zip ->
        val xml = zip.getInputStream(zip.getEntry("xl/workbook.xml")).readBytes()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val nodes = doc.getElementsByTagName("sheet")
        (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
    }

    @Test
    fun `creates every sheet for a multi-month range with loans`() {
        val file = ExcelReportGenerator.create(
            tmp.newFile("report.xlsx"), transactions, mapOf(1L to "Salary", 2L to "Home"), emptyList(),
            mapOf(7L to loan), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), gtq
        )

        assertEquals(
            listOf("Movements", "Summary", "By Category", "Monthly Overview", "Loans", "Loan Installments"),
            sheetNames(file)
        )
        ZipFile(file).use { zip ->
            val movements = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(movements.contains("Rent &amp; &lt;bills&gt;"))
            assertTrue(movements.contains("<v>3000.0</v>"))
        }
    }

    private fun sheetXml(file: java.io.File, name: String): String = ZipFile(file).use { zip ->
        val idx = sheetNames(file).indexOf(name) + 1
        zip.getInputStream(zip.getEntry("xl/worksheets/sheet$idx.xml")).readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `adjustments are excluded from totals and labelled`() {
        val adjustments = listOf(
            movement(500_000, LocalDateTime.of(2026, 1, 20, 9, 0), TransactionType.ADJUSTMENT_IN),
            movement(20_000, LocalDateTime.of(2026, 2, 10, 9, 0), TransactionType.ADJUSTMENT_OUT),
        )
        val range = LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 2, 28)
        val names = mapOf(1L to "Salary", 2L to "Home")
        val without = ExcelReportGenerator.create(
            tmp.newFile("without.xlsx"), transactions, names, emptyList(), mapOf(7L to loan), range.first, range.second, gtq
        )
        val with = ExcelReportGenerator.create(
            tmp.newFile("with.xlsx"), transactions + adjustments, names, emptyList(), mapOf(7L to loan), range.first, range.second, gtq
        )

        val movements = sheetXml(with, "Movements")
        assertTrue(movements.contains("Adjustment"))
        assertTrue(movements.contains("<v>5000.0</v>"))
        for (sheet in listOf("Summary", "By Category", "Monthly Overview")) {
            assertEquals(sheet, sheetXml(without, sheet), sheetXml(with, sheet))
        }
    }

    @Test
    fun `transfers are listed but not totaled`() {
        val rows = listOf(
            movement(10_000, LocalDateTime.of(2026, 1, 5, 9, 0), TransactionType.INCOME),
            movement(5_000, LocalDateTime.of(2026, 1, 6, 9, 0), TransactionType.TRANSFER_OUT),
            movement(5_000, LocalDateTime.of(2026, 1, 6, 9, 0), TransactionType.TRANSFER_IN),
        )
        val file = ExcelReportGenerator.create(
            tmp.newFile("transfers.xlsx"), rows, emptyMap(), emptyList(), emptyMap(),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), gtq
        )

        val movements = sheetXml(file, "Movements")
        assertEquals(3, Regex("<row ").findAll(movements).count() - 1)
        assertEquals(1, Regex(">Transfer out<").findAll(movements).count())
        assertEquals(1, Regex(">Transfer in<").findAll(movements).count())
        val summary = sheetXml(file, "Summary")
        assertTrue(summary.contains("<v>100.0</v>"))
        assertTrue(!summary.contains("<v>50.0</v>"))
    }

    @Test
    fun `skips monthly and loan sheets when not applicable`() {
        val file = ExcelReportGenerator.create(
            tmp.newFile("report.xlsx"), transactions.take(1), mapOf(1L to "Salary"), emptyList(),
            emptyMap(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), gtq
        )

        assertEquals(listOf("Movements", "Summary", "By Category"), sheetNames(file))
    }

    @Test
    fun `movement row carries original amount, currency, effective rate and main amount`() {
        val eur = Currency.getInstance("EUR")
        val foreign = Transaction(
            accountId = 1, type = TransactionType.EXPENSE, date = LocalDateTime.of(2026, 1, 5, 9, 0),
            original = Money(2_000, eur), accountAmount = Money(2_000, eur), accountRate = ExchangeRate.IDENTITY,
            amount = Money(16_579, gtq), mainRate = ExchangeRate(BigDecimal("8.2895"), RateSource.AUTO),
        )
        val file = ExcelReportGenerator.create(
            tmp.newFile("report.xlsx"), listOf(foreign), emptyMap(), emptyList(), emptyMap(),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), gtq
        )

        ZipFile(file).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(xml.contains("Original amount"))
            assertTrue(xml.contains("Amount in main currency"))
            assertTrue(xml.contains("<c r=\"L2\"><v>20.00</v></c>"))
            assertTrue(xml.contains("<t xml:space=\"preserve\">EUR</t>"))
            assertTrue(xml.contains("<c r=\"N2\"><v>8.289500</v></c>"))
            assertTrue(xml.contains("<c r=\"O2\"><v>165.79</v></c>"))
        }
    }

    @Test
    fun `loan portfolio remaining total uses main currency figures`() {
        val usdLoan = loan.copy(
            id = 8, name = "Abroad", totalAmount = Money(100_000, Currency.getInstance("USD")),
            paidInstallments = 0, remainingMainMinor = 770_000
        )
        val file = ExcelReportGenerator.create(
            tmp.newFile("report.xlsx"), emptyList(), emptyMap(), emptyList(),
            mapOf(7L to loan.copy(remainingMainMinor = 110_000), 8L to usdLoan),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), gtq
        )

        ZipFile(file).use { zip ->
            val idx = sheetNames(file).indexOf("Loans") + 1
            val xml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet$idx.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(xml.contains("<v>8800.0</v>"))
        }
    }

    private fun loansSheet(file: java.io.File): String = ZipFile(file).use { zip ->
        val idx = sheetNames(file).indexOf("Loans") + 1
        zip.getInputStream(zip.getEntry("xl/worksheets/sheet$idx.xml")).readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `loan totals note counts loans in other currencies only when there are some`() {
        val usdLoan = loan.copy(id = 8, name = "Abroad", totalAmount = Money(100_000, Currency.getInstance("USD")))
        val eurLoan = loan.copy(id = 9, name = "Euro", totalAmount = Money(50_000, Currency.getInstance("EUR")))
        val range = LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 1, 31)

        val mixed = ExcelReportGenerator.create(
            tmp.newFile("mixed.xlsx"), emptyList(), emptyMap(), emptyList(),
            mapOf(7L to loan, 8L to usdLoan, 9L to eurLoan), range.first, range.second, gtq
        )
        assertTrue(loansSheet(mixed).contains("Excludes 2 loans in other currencies"))

        val allMain = ExcelReportGenerator.create(
            tmp.newFile("main.xlsx"), emptyList(), emptyMap(), emptyList(),
            mapOf(7L to loan), range.first, range.second, gtq
        )
        assertTrue(!loansSheet(allMain).contains("Excludes"))
    }

    private val usd = Currency.getInstance("USD")
    private val banco = Account(id = 1, name = "Banco", type = AccountType.CHECKING, currency = gtq, createdAt = 0)
    private val dolares = Account(id = 2, name = "Dólares", type = AccountType.SAVINGS, currency = usd, createdAt = 0)
    private val accounts = listOf(AccountWithBalance(banco, Money(123_456, gtq)), AccountWithBalance(dolares, Money(10_092, usd)))

    private fun text(ref: String, value: String) = "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">$value</t></is></c>"

    @Test
    fun `each movement names its account and a transfer reads out and in`() {
        val out = movement(77_100, LocalDateTime.of(2026, 1, 6, 9, 0), TransactionType.TRANSFER_OUT)
        val into = out.copy(accountId = 2, type = TransactionType.TRANSFER_IN, accountAmount = Money(10_092, usd))
        val expense = movement(5_000, LocalDateTime.of(2026, 1, 7, 9, 0), TransactionType.EXPENSE)
        val file = ExcelReportGenerator.create(
            tmp.newFile("accounts.xlsx"), listOf(out, into, expense), emptyMap(), accounts, emptyMap(),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), gtq
        )

        val movements = sheetXml(file, "Movements")
        assertTrue(Regex("<c r=\"D1\"[^>]*><is><t xml:space=\"preserve\">Account</t>").containsMatchIn(movements))
        assertTrue(movements.contains(text("D2", "Banco")))
        assertTrue(movements.contains(text("E2", "Transfer out")))
        assertTrue(movements.contains(text("D3", "Dólares")))
        assertTrue(movements.contains(text("E3", "Transfer in")))
        assertTrue(movements.contains(text("D4", "Banco")))
        assertTrue(movements.contains(text("E4", "Expense")))
    }

    @Test
    fun `accounts sheet lists each account with its balance in its own currency`() {
        val file = ExcelReportGenerator.create(
            tmp.newFile("balances.xlsx"), transactions, emptyMap(), accounts, emptyMap(),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), gtq
        )

        assertTrue("Accounts" in sheetNames(file))
        val sheet = sheetXml(file, "Accounts")
        assertTrue(sheet.contains(text("A2", "Banco")))
        assertTrue(sheet.contains(text("B2", "GTQ")))
        assertTrue(sheet.contains("<c r=\"C2\"><v>1234.56</v></c>"))
        assertTrue(sheet.contains(text("A3", "Dólares")))
        assertTrue(sheet.contains(text("B3", "USD")))
        assertTrue(sheet.contains("<c r=\"C3\"><v>100.92</v></c>"))
    }

    @Test
    fun `a card's second currency is named after its card and the balance header fits cards`() {
        val visa = Account(id = 3, name = "Visa BAC", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0)
        val visaUsd = visa.copy(id = 4, currency = usd, parentAccountId = 3)
        val withCard = accounts + AccountWithBalance(visa, Money(-50_000, gtq)) + AccountWithBalance(visaUsd, Money(-1_000, usd))
        val expense = movement(1_000, LocalDateTime.of(2026, 1, 7, 9, 0), TransactionType.EXPENSE).copy(accountId = 4)
        val file = ExcelReportGenerator.create(
            tmp.newFile("cards.xlsx"), listOf(expense), emptyMap(), withCard, emptyMap(),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), gtq
        )

        val sheet = sheetXml(file, "Accounts")
        assertTrue(Regex("<c r=\"C1\"[^>]*><is><t xml:space=\"preserve\">Balance</t>").containsMatchIn(sheet))
        assertTrue(sheet.contains(text("A4", "Visa BAC")))
        assertTrue(sheet.contains(text("A5", "Visa BAC · USD")))
        assertTrue(sheetXml(file, "Movements").contains(text("D2", "Visa BAC · USD")))
    }

    @Test
    fun `rate column is the account rate times the main rate`() {
        val eur = Currency.getInstance("EUR")
        // 20 EUR spent from the USD account at 1.0852, then USD -> GTQ at 7.64.
        val foreign = Transaction(
            accountId = 2, type = TransactionType.EXPENSE, date = LocalDateTime.of(2026, 1, 5, 9, 0),
            original = Money(2_000, eur), accountAmount = Money(2_170, usd),
            accountRate = ExchangeRate(BigDecimal("1.0852"), RateSource.AUTO),
            amount = Money(16_579, gtq), mainRate = ExchangeRate(BigDecimal("7.64"), RateSource.AUTO),
        )
        val file = ExcelReportGenerator.create(
            tmp.newFile("rate.xlsx"), listOf(foreign), emptyMap(), accounts, emptyMap(),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), gtq
        )

        assertTrue(sheetXml(file, "Movements").contains("<c r=\"N2\"><v>8.290928</v></c>"))
    }
}
