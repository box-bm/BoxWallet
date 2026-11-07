package com.boxdev.boxwallet.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWorkbookTest {

    private fun XlsxWorkbook.unzip(): Map<String, String> {
        val bytes = ByteArrayOutputStream().also { write(it) }.toByteArray()
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return parts
    }

    private fun parse(xml: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun Document.cells(): List<Element> {
        val nodes = getElementsByTagName("c")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun `writes all package parts and every part is well-formed xml`() {
        val wb = XlsxWorkbook()
        wb.createSheet("One").createRow(0).createCell(0).setCellValue("a")
        wb.createSheet("Two").createRow(0).createCell(0).setCellValue(1.0)

        val parts = wb.unzip()

        assertEquals(
            setOf(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
                "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"
            ),
            parts.keys
        )
        parts.values.forEach { parse(it) }
        assertTrue(parts.getValue("xl/workbook.xml").contains("name=\"One\""))
        assertTrue(parts.getValue("xl/workbook.xml").contains("name=\"Two\""))
    }

    @Test
    fun `writes numbers as values and strings inline with style and cell references`() {
        val wb = XlsxWorkbook()
        val row = wb.createSheet("S").createRow(2)
        row.createCell(1).setCellValue(12.5)
        row.createCell(27).also { it.setCellValue("hi"); it.cellStyle = XlsxWorkbook.Style.HEADER }

        val cells = parse(wb.unzip().getValue("xl/worksheets/sheet1.xml")).cells()

        assertEquals("B3", cells[0].getAttribute("r"))
        assertEquals("12.5", cells[0].textContent)
        assertEquals("", cells[0].getAttribute("t"))
        assertEquals("AB3", cells[1].getAttribute("r"))
        assertEquals("inlineStr", cells[1].getAttribute("t"))
        assertEquals("2", cells[1].getAttribute("s"))
        assertEquals("hi", cells[1].textContent)
    }

    @Test
    fun `rows and cells are written in ascending order regardless of creation order`() {
        val wb = XlsxWorkbook()
        val sheet = wb.createSheet("S")
        sheet.createRow(5).createCell(3).setCellValue("late")
        sheet.createRow(0).apply {
            createCell(2).setCellValue("c")
            createCell(0).setCellValue("a")
        }

        val refs = parse(wb.unzip().getValue("xl/worksheets/sheet1.xml")).cells().map { it.getAttribute("r") }

        assertEquals(listOf("A1", "C1", "D6"), refs)
    }

    @Test
    fun `escapes xml characters and drops invalid control characters`() {
        val wb = XlsxWorkbook()
        wb.createSheet("S").createRow(0).createCell(0).setCellValue("Tom & \"Jerry\" <3\u0001 😀")

        val cell = parse(wb.unzip().getValue("xl/worksheets/sheet1.xml")).cells().single()

        assertEquals("Tom & \"Jerry\" <3 😀", cell.textContent)
    }

    @Test
    fun `non-finite numbers are written as text so the file stays valid`() {
        val wb = XlsxWorkbook()
        wb.createSheet("S").createRow(0).createCell(0).setCellValue(Double.NaN)

        val cell = parse(wb.unzip().getValue("xl/worksheets/sheet1.xml")).cells().single()

        assertEquals("inlineStr", cell.getAttribute("t"))
        assertEquals("NaN", cell.textContent)
    }

    @Test
    fun `column widths are written in characters`() {
        val wb = XlsxWorkbook()
        wb.createSheet("S").apply {
            setColumnWidth(0, 12)
            createRow(0).createCell(0).setCellValue("x")
        }

        val col = parse(wb.unzip().getValue("xl/worksheets/sheet1.xml")).getElementsByTagName("col").item(0) as Element

        assertEquals("1", col.getAttribute("min"))
        assertEquals("12.0", col.getAttribute("width"))
    }

    @Test
    fun `column names follow excel lettering`() {
        assertEquals("A", XlsxWorkbook.columnName(0))
        assertEquals("Z", XlsxWorkbook.columnName(25))
        assertEquals("AA", XlsxWorkbook.columnName(26))
        assertEquals("AZ", XlsxWorkbook.columnName(51))
        assertEquals("XFD", XlsxWorkbook.columnName(16_383))
    }

    @Test
    fun `sheet names are sanitized and capped at 31 characters`() {
        assertEquals("a b c", XlsxWorkbook.sanitizeSheetName("a/b:c"))
        assertEquals(31, XlsxWorkbook.sanitizeSheetName("x".repeat(40)).length)
        assertEquals("Sheet", XlsxWorkbook.sanitizeSheetName("[]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate sheet names are rejected`() {
        val wb = XlsxWorkbook()
        wb.createSheet("Data")
        wb.createSheet("data")
    }
}
