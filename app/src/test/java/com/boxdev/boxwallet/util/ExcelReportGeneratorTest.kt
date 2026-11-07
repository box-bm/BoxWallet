package com.boxdev.boxwallet.util

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class ExcelReportGeneratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val loan = Loan(
        id = 7,
        name = "Car",
        totalAmount = 1200.0,
        totalInstallments = 12,
        startDate = LocalDateTime.of(2026, 1, 1, 0, 0),
        paidInstallments = 1
    )

    private val transactions = listOf(
        Transaction(amount = 3000.0, date = LocalDateTime.of(2026, 1, 5, 9, 0), type = TransactionType.INCOME, categoryId = 1),
        Transaction(amount = 100.0, description = "Rent & <bills>", date = LocalDateTime.of(2026, 2, 3, 12, 30), type = TransactionType.EXPENSE, categoryId = 2, loanId = 7, installmentNumber = 1, isCleared = true)
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
            mapOf(7L to loan), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)
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
            emptyMap(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        )

        assertEquals(listOf("Movements", "Summary", "By Category"), sheetNames(file))
    }
}
