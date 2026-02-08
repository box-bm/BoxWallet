package com.boxdev.boxwallet.ui.screens.settings

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Transaction
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ExportUiState(
    val startDate: LocalDate = LocalDate.now().minusMonths(1),
    val endDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
    val exportedFilePath: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val exportFolder: File
        get() {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val boxWalletDir = File(documentsDir, "BoxWallet/Reports")
            if (!boxWalletDir.exists()) {
                boxWalletDir.mkdirs()
            }
            return boxWalletDir
        }

    fun updateStartDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(startDate = date)
    }

    fun updateEndDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(endDate = date)
    }

    fun exportToExcel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)

            try {
                val transactions = transactionRepository.getTransactionsBetweenDates(
                    _uiState.value.startDate,
                    _uiState.value.endDate
                )

                val transactionList = transactions.toList().flatten()

                val filePath = withContext(Dispatchers.IO) {
                    createExcelFile(transactionList)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Excel report exported successfully",
                    isSuccess = true,
                    exportedFilePath = filePath
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Error exporting: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun exportToPdf() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)

            try {
                val transactions = transactionRepository.getTransactionsBetweenDates(
                    _uiState.value.startDate,
                    _uiState.value.endDate
                )

                val transactionsList = transactions.toList().flatten()


                val filePath = withContext(Dispatchers.IO) {
                    createPdfFile(transactionsList)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "PDF report exported successfully",
                    isSuccess = true,
                    exportedFilePath = filePath
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Error exporting: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    private fun createExcelFile(transactions: List<Transaction>): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportFolder, "BoxWallet_Report_$timestamp.xlsx")

        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Transactions")

            // Header row
            val headerRow = sheet.createRow(0)
            listOf("Date", "Type", "Amount", "Description", "Is Loan", "Installment").forEachIndexed { index, title ->
                headerRow.createCell(index).setCellValue(title)
            }

            // Data rows
            transactions.forEachIndexed { index, transaction ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(transaction.date.format(dateFormatter))
                row.createCell(1).setCellValue(transaction.type.name)
                row.createCell(2).setCellValue(transaction.amount)
                row.createCell(3).setCellValue(transaction.description)
                row.createCell(4).setCellValue(if (transaction.loanId != null) "Yes" else "No")
                row.createCell(5).setCellValue(
                    if (transaction.installmentNumber != null)
                        "${transaction.installmentNumber}"
                    else ""
                )
            }

            // Summary
            val summarySheet = workbook.createSheet("Summary")
            val incomeTotal = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expenseTotal = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

            summarySheet.createRow(0).createCell(0).setCellValue("Period")
            summarySheet.createRow(1).createCell(0).setCellValue(
                "${_uiState.value.startDate.format(dateFormatter)} to ${_uiState.value.endDate.format(dateFormatter)}"
            )
            summarySheet.createRow(3).createCell(0).setCellValue("Total Income")
            summarySheet.getRow(3).createCell(1).setCellValue(incomeTotal)
            summarySheet.createRow(4).createCell(0).setCellValue("Total Expense")
            summarySheet.getRow(4).createCell(1).setCellValue(expenseTotal)
            summarySheet.createRow(5).createCell(0).setCellValue("Net Balance")
            summarySheet.getRow(5).createCell(1).setCellValue(incomeTotal - expenseTotal)

            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
        }

        return file.absolutePath
    }

    private fun createPdfFile(transactions: List<Transaction>): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportFolder, "BoxWallet_Report_$timestamp.pdf")

        val pdfWriter = PdfWriter(file)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)

        // Title
        document.add(
            Paragraph("BoxWallet Financial Report")
                .setFontSize(20f)
                .setTextAlignment(TextAlignment.CENTER)
        )

        // Period
        document.add(
            Paragraph("Period: ${_uiState.value.startDate.format(dateFormatter)} to ${_uiState.value.endDate.format(dateFormatter)}")
                .setFontSize(12f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20f)
        )

        // Summary
        val incomeTotal = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expenseTotal = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        document.add(Paragraph("Summary").setFontSize(16f))
        document.add(Paragraph("Total Income: ${currencyFormat.format(incomeTotal)}"))
        document.add(Paragraph("Total Expense: ${currencyFormat.format(expenseTotal)}"))
        document.add(Paragraph("Net Balance: ${currencyFormat.format(incomeTotal - expenseTotal)}").setMarginBottom(20f))

        // Transactions Table
        document.add(Paragraph("Transactions").setFontSize(16f).setMarginBottom(10f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 1f, 2f, 3f)))
            .useAllAvailableWidth()

        // Table header
        listOf("Date", "Type", "Amount", "Description").forEach { header ->
            table.addHeaderCell(
                Cell().add(Paragraph(header))
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            )
        }

        // Table data
        transactions.forEach { transaction ->
            table.addCell(Cell().add(Paragraph(transaction.date.format(dateFormatter))))
            table.addCell(Cell().add(Paragraph(transaction.type.name)))
            table.addCell(Cell().add(
                Paragraph(currencyFormat.format(transaction.amount))
            ))
            table.addCell(Cell().add(Paragraph(transaction.description)))
        }

        document.add(table)

        // Footer
        document.add(
            Paragraph("Generated by BoxWallet on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
                .setFontSize(10f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(30f)
        )

        document.close()

        return file.absolutePath
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
