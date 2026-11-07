package com.boxdev.boxwallet.util

import java.io.OutputStream
import java.io.Writer
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal `.xlsx` (Office Open XML spreadsheet) writer.
 *
 * Replaces Apache POI for the analytics export: an .xlsx file is a zip of a few XML parts, and the
 * report only needs text/number cells, four fixed styles and column widths. Avoiding POI removes
 * megabytes of reflection-heavy code (XMLBeans) that R8 could not shrink or optimize.
 *
 * Strings are written inline (`t="inlineStr"`), so no shared-strings table is needed.
 */
class XlsxWorkbook {

    /** Fixed cell styles; [index] is the position in `cellXfs` of styles.xml. */
    enum class Style(internal val index: Int) {
        NONE(0),
        BOLD(1),
        /** Bold on a light grey fill (column headers). */
        HEADER(2),
        /** Bold on a pale blue fill (section titles). */
        TITLE(3)
    }

    private val sheets = mutableListOf<Sheet>()

    fun createSheet(name: String): Sheet {
        val safe = sanitizeSheetName(name)
        require(sheets.none { it.name.equals(safe, ignoreCase = true) }) { "Duplicate sheet name: $safe" }
        return Sheet(safe).also { sheets += it }
    }

    class Sheet internal constructor(val name: String) {
        internal val rows = TreeMap<Int, Row>()
        internal val widths = TreeMap<Int, Double>()

        /** Returns the row at zero-based [index], creating it if needed. */
        fun createRow(index: Int): Row {
            require(index in 0 until MAX_ROWS) { "Row index out of range: $index" }
            return rows.getOrPut(index) { Row(index) }
        }

        /** Sets the width of zero-based column [column], in characters. */
        fun setColumnWidth(column: Int, chars: Int) {
            require(column in 0 until MAX_COLUMNS) { "Column index out of range: $column" }
            widths[column] = chars.toDouble()
        }
    }

    class Row internal constructor(internal val index: Int) {
        internal val cells = TreeMap<Int, Cell>()

        /** Returns the cell at zero-based [column], creating it if needed. */
        fun createCell(column: Int): Cell {
            require(column in 0 until MAX_COLUMNS) { "Column index out of range: $column" }
            return cells.getOrPut(column) { Cell() }
        }
    }

    class Cell internal constructor() {
        internal var value: Any? = null
        var cellStyle: Style = Style.NONE

        fun setCellValue(value: String) { this.value = value }
        fun setCellValue(value: Double) { this.value = value }
    }

    fun write(out: OutputStream) {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        val zip = ZipOutputStream(out)
        val writer = zip.bufferedWriter(Charsets.UTF_8)

        fun entry(path: String, body: Writer.() -> Unit) {
            zip.putNextEntry(ZipEntry(path))
            writer.body()
            writer.flush()
            zip.closeEntry()
        }

        entry("[Content_Types].xml") { write(contentTypes()) }
        entry("_rels/.rels") { write(ROOT_RELS) }
        entry("xl/workbook.xml") { write(workbookXml()) }
        entry("xl/_rels/workbook.xml.rels") { write(workbookRels()) }
        entry("xl/styles.xml") { write(STYLES) }
        sheets.forEachIndexed { i, sheet ->
            entry("xl/worksheets/sheet${i + 1}.xml") { writeSheet(sheet) }
        }
        zip.finish()
        zip.flush()
    }

    private fun contentTypes() = buildString {
        append(XML_HEADER)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        sheets.indices.forEach { i ->
            append("<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun workbookXml() = buildString {
        append(XML_HEADER)
        append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>")
        sheets.forEachIndexed { i, sheet ->
            append("<sheet name=\"").append(escapeXml(sheet.name)).append("\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels() = buildString {
        append(XML_HEADER)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        sheets.indices.forEach { i ->
            append("<Relationship Id=\"rId${i + 1}\" Type=\"$NS_REL/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheets.size + 1}\" Type=\"$NS_REL/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun Writer.writeSheet(sheet: Sheet) {
        write(XML_HEADER)
        write("<worksheet xmlns=\"$NS_MAIN\">")
        if (sheet.widths.isNotEmpty()) {
            write("<cols>")
            sheet.widths.forEach { (col, width) ->
                write("<col min=\"${col + 1}\" max=\"${col + 1}\" width=\"$width\" customWidth=\"1\"/>")
            }
            write("</cols>")
        }
        write("<sheetData>")
        sheet.rows.values.forEach { row ->
            val r = row.index + 1
            write("<row r=\"$r\">")
            row.cells.forEach { (col, cell) -> writeCell("${columnName(col)}$r", cell) }
            write("</row>")
        }
        write("</sheetData></worksheet>")
    }

    private fun Writer.writeCell(ref: String, cell: Cell) {
        val style = if (cell.cellStyle == Style.NONE) "" else " s=\"${cell.cellStyle.index}\""
        when (val v = cell.value) {
            null -> write("<c r=\"$ref\"$style/>")
            is Double ->
                if (v.isFinite()) write("<c r=\"$ref\"$style><v>$v</v></c>")
                else writeInlineString(ref, style, v.toString())
            else -> writeInlineString(ref, style, v.toString())
        }
    }

    private fun Writer.writeInlineString(ref: String, style: String, text: String) {
        write("<c r=\"$ref\"$style t=\"inlineStr\"><is><t xml:space=\"preserve\">")
        write(escapeXml(text))
        write("</t></is></c>")
    }

    internal companion object {
        const val MAX_ROWS = 1_048_576
        const val MAX_COLUMNS = 16_384
        private const val MAX_SHEET_NAME = 31

        private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
        private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        private const val ROOT_RELS = XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$NS_REL/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

        // Order of cellXfs must match Style.index. fills 0/1 (none, gray125) are required by Excel.
        private const val STYLES = XML_HEADER +
            "<styleSheet xmlns=\"$NS_MAIN\">" +
            "<fonts count=\"2\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" +
            "</fonts>" +
            "<fills count=\"4\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFC0C0C0\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF99CCFF\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"4\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"

        /** Zero-based column index to Excel letters: 0 → A, 25 → Z, 26 → AA. */
        fun columnName(index: Int): String {
            var n = index + 1
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.append('A' + rem)
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }

        /** Excel forbids `[]:*?/\` in sheet names and caps them at 31 characters. */
        fun sanitizeSheetName(name: String): String {
            val cleaned = name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(MAX_SHEET_NAME)
            return cleaned.ifEmpty { "Sheet" }
        }

        /** Escapes XML special characters and drops characters XML 1.0 cannot represent. */
        fun escapeXml(text: String): String {
            val sb = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '&' -> sb.append("&amp;")
                    c == '<' -> sb.append("&lt;")
                    c == '>' -> sb.append("&gt;")
                    c == '"' -> sb.append("&quot;")
                    c == '\t' || c == '\n' || c == '\r' -> sb.append(c)
                    c < ' ' || c == '￾' || c == '￿' -> Unit
                    Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                        sb.append(c).append(text[i + 1]); i++
                    }
                    Character.isSurrogate(c) -> Unit // unpaired surrogate
                    else -> sb.append(c)
                }
                i++
            }
            return sb.toString()
        }
    }
}
