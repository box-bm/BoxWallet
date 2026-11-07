package com.boxdev.boxwallet.util

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class ExcelReportGeneratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val start = LocalDate.of(2026, 10, 1)
    private val end = LocalDate.of(2026, 10, 31)
    private val loan = Loan(id = 7, name = "Car", totalAmount = 1200.0, totalInstallments = 12, startDate = LocalDateTime.of(2026, 1, 1, 0, 0))
    private val movement = Transaction(id = 1, amount = 50.0, description = "Groceries", date = LocalDateTime.of(2026, 10, 3, 10, 0), type = TransactionType.EXPENSE)

    private fun installment(id: Long, number: Int, cleared: Boolean) = Transaction(
        id = id, amount = 100.0, date = LocalDateTime.of(2026, 10, number, 0, 0), type = TransactionType.EXPENSE,
        isCleared = cleared, loanId = loan.id, installmentNumber = number,
    )

    private fun export(installments: List<Transaction>): File =
        ExcelReportGenerator.create(
            tmp.newFile("report.xlsx"), listOf(movement), installments, emptyMap(), mapOf(loan.id to loan), start, end,
        )

    @Test
    fun `installments due in the range fill the Loan Installments sheet`() {
        val file = export(listOf(installment(10, 2, cleared = false), installment(11, 1, cleared = true)))
        XSSFWorkbook(file).use { wb ->
            val sheet = wb.getSheet("Loan Installments")
            assertNotNull(sheet)
            // Sorted by installment number within the loan.
            assertEquals("Car", sheet.getRow(1).getCell(0).stringCellValue)
            assertEquals(1.0, sheet.getRow(1).getCell(1).numericCellValue, 0.0)
            assertEquals("Paid", sheet.getRow(1).getCell(5).stringCellValue)
            assertEquals(2.0, sheet.getRow(2).getCell(1).numericCellValue, 0.0)
            assertEquals("Pending", sheet.getRow(2).getCell(5).stringCellValue)
            // Subtotal row after a blank line.
            assertEquals("Car — Subtotal", sheet.getRow(4).getCell(0).stringCellValue)
            assertEquals(200.0, sheet.getRow(4).getCell(3).numericCellValue, 0.0)
        }
    }

    @Test
    fun `installments stay out of the movement sheets`() {
        val file = export(listOf(installment(10, 2, cleared = false)))
        XSSFWorkbook(file).use { wb ->
            // Header plus the single movement.
            assertEquals(1, wb.getSheet("Movements").lastRowNum)
        }
    }

    @Test
    fun `no installments sheet when none fall due`() {
        XSSFWorkbook(export(emptyList())).use { wb -> assertNull(wb.getSheet("Loan Installments")) }
    }
}
