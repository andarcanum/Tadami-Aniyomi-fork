package tachiyomi.data.anixart

import java.io.InputStream

/**
 * Parser for CSV bookmark exports produced by the Anixart app
 * (Управление данными -> Экспорт закладок -> CSV).
 *
 * Design notes / why this is hand-rolled:
 *  - Anixart exports are "dirty": titles can contain commas and stray quotes,
 *    rows may have a varying number of fields, and the file is UTF-8 with a BOM.
 *  - Columns are located by HEADER NAME, not by position, so the parser keeps
 *    working if Anixart reorders or adds columns.
 *  - We deliberately avoid pulling in a CSV dependency for one importer.
 *
 * The tokenizer follows RFC-4180 quoting but is lenient ("lazy quotes"): a stray
 * quote inside an unquoted field is treated as data instead of throwing, mirroring
 * the reference Go implementation's reader.LazyQuotes = true.
 */
object AnixartCsvParser {

    /** Required header columns; parsing fails fast if any are missing. */
    private val REQUIRED_COLUMNS = listOf(
        "Русское название",
        "Статус просмотра",
    )

    private const val COL_INDEX = "#"
    private const val COL_RU = "Русское название"
    private const val COL_ORIGINAL = "Оригинальное название"
    private const val COL_ALT = "Альтернативные названия"
    private const val COL_FAVORITE = "Добавлено в избранное"
    private const val COL_STATUS = "Статус просмотра"
    private const val COL_RATING = "Моя оценка"

    class InvalidAnixartCsvException(message: String) : Exception(message)

    fun parse(input: InputStream): List<AnixartRow> =
        parse(input.readBytes().toString(Charsets.UTF_8))

    fun parse(text: String): List<AnixartRow> {
        val rows = tokenize(stripBom(text))
        if (rows.isEmpty()) {
            throw InvalidAnixartCsvException("Empty CSV: no header row found")
        }

        val header = rows.first().map { it.trim() }
        val index = header.withIndex().associate { (i, name) -> name to i }

        val missing = REQUIRED_COLUMNS.filterNot { index.containsKey(it) }
        if (missing.isNotEmpty()) {
            throw InvalidAnixartCsvException(
                "Not an Anixart CSV export. Missing columns: ${missing.joinToString()}",
            )
        }

        val result = ArrayList<AnixartRow>(rows.size - 1)
        var fallbackIndex = 0
        for (record in rows.drop(1)) {
            // Skip completely blank lines that the tokenizer may emit.
            if (record.all { it.isBlank() }) continue
            fallbackIndex++

            val parsedIndex = cell(record, index, COL_INDEX).trim().toIntOrNull() ?: fallbackIndex
            result += AnixartRow(
                index = parsedIndex,
                russianTitle = cell(record, index, COL_RU),
                originalTitle = cell(record, index, COL_ORIGINAL),
                alternativeTitles = cell(record, index, COL_ALT),
                favoriteRaw = cell(record, index, COL_FAVORITE),
                statusRaw = cell(record, index, COL_STATUS),
                ratingRaw = cell(record, index, COL_RATING),
            )
        }
        return result
    }

    /** Safe column access by name; returns "" when the column is absent or the row is short. */
    private fun cell(record: List<String>, index: Map<String, Int>, column: String): String {
        val i = index[column] ?: return ""
        return record.getOrNull(i)?.trim() ?: ""
    }

    private fun stripBom(text: String): String =
        if (text.isNotEmpty() && text[0] == '\uFEFF') text.substring(1) else text

    /**
     * Splits raw CSV text into records of fields. Handles:
     *  - quoted fields with embedded commas, quotes ("") and newlines;
     *  - both CRLF and LF line endings;
     *  - lenient handling of stray quotes in unquoted fields.
     */
    private fun tokenize(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var field = StringBuilder()
        var record = ArrayList<String>()
        var inQuotes = false
        var i = 0
        val n = text.length

        fun endField() {
            record.add(field.toString())
            field = StringBuilder()
        }
        fun endRecord() {
            endField()
            records.add(record)
            record = ArrayList()
        }

        while (i < n) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < n && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' && field.isEmpty() -> inQuotes = true
                c == ',' -> endField()
                c == '\r' -> {
                    if (i + 1 < n && text[i + 1] == '\n') i++
                    endRecord()
                }
                c == '\n' -> endRecord()
                else -> field.append(c)
            }
            i++
        }
        // Flush trailing field/record if the file doesn't end with a newline.
        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
        return records
    }
}
