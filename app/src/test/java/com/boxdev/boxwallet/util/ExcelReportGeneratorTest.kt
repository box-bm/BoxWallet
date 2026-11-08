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
            tmp.newFile("report.xlsx"), transactions, mapOf(1L to "Salary", 2L to "Home"),
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

    @Test
    fun `skips monthly and loan sheets when not applicable`() {
        val file = ExcelReportGenerator.create(
            tmp.newFile("report.xlsx"), transactions.take(1), mapOf(1L to "Salary"),
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
            amount = Money(16_579, gtq), mainRate = ExchangeRate.IDENTITY,
        )
        val file = ExcelReportGenerator.create(
            tmp.newFile("report.xlsx"), listOf(foreign), emptyMap(), emptyMap(),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), gtq
        )

        ZipFile(file).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(xml.contains("Original amount"))
            assertTrue(xml.contains("Amount in main currency"))
            assertTrue(xml.contains("<c r=\"K2\"><v>20.00</v></c>"))
            assertTrue(xml.contains("<t xml:space=\"preserve\">EUR</t>"))
            assertTrue(xml.contains("<c r=\"M2\"><v>8.289500</v></c>"))
            assertTrue(xml.contains("<c r=\"N2\"><v>165.79</v></c>"))
        }
    }

    @Test
    fun `loan portfolio remaining total uses main currency figures`() {
        val usdLoan = loan.copy(
            id = 8, name = "Abroad", totalAmount = Money(100_000, Currency.getInstance("USD")),
            paidInstallments = 0, remainingMainMinor = 770_000
        )
        val file = ExcelReportGenerator.create(
            tmp.newFile("report.xlsx"), emptyList(), emptyMap(),
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
            tmp.newFile("mixed.xlsx"), emptyList(), emptyMap(),
            mapOf(7L to loan, 8L to usdLoan, 9L to eurLoan), range.first, range.second, gtq
        )
        assertTrue(loansSheet(mixed).contains("Excludes 2 loans in other currencies"))

        val allMain = ExcelReportGenerator.create(
            tmp.newFile("main.xlsx"), emptyList(), emptyMap(),
            mapOf(7L to loan), range.first, range.second, gtq
        )
        assertTrue(!loansSheet(allMain).contains("Excludes"))
    }
}
