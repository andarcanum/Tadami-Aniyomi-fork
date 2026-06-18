package eu.kanade.tachiyomi.ui.player.subtitle.translation

object SubtitleParser {
    fun parse(raw: String, hint: String? = null): SubtitleDocument {
        val normalizedHint = hint?.substringAfterLast('.')?.lowercase()
        return when {
            normalizedHint == "srt" -> SrtSubtitleCodec.parse(raw)
            normalizedHint == "vtt" || raw.trimStart().startsWith("WEBVTT") -> VttSubtitleCodec.parse(raw)
            normalizedHint == "ass" || normalizedHint == "ssa" || raw.contains("[Events]", ignoreCase = true) ->
                AssSubtitleCodec.parse(raw)
            raw.contains("-->") -> {
                if (raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim() == "WEBVTT") {
                    VttSubtitleCodec.parse(raw)
                } else {
                    SrtSubtitleCodec.parse(raw)
                }
            }
            else -> SubtitleDocument(SubtitleFormat.Unknown, emptyList())
        }
    }
}

object SubtitleWriter {
    fun write(document: SubtitleDocument): String {
        return when (document.format) {
            SubtitleFormat.Srt -> SrtSubtitleCodec.write(document)
            SubtitleFormat.Vtt, SubtitleFormat.Unknown -> VttSubtitleCodec.write(
                document.copy(format = SubtitleFormat.Vtt),
            )
            SubtitleFormat.Ass -> AssSubtitleCodec.write(document)
        }
    }
}

object SrtSubtitleCodec {
    private val timeRegex = Regex("""(\d{1,2}:\d{2}:\d{2},\d{1,3})\s*-->\s*(\d{1,2}:\d{2}:\d{2},\d{1,3})""")

    fun parse(raw: String): SubtitleDocument {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim('\uFEFF')
        val blocks = normalized.split(Regex("\n{2,}"))
        val cues = blocks.mapNotNull { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return@mapNotNull null
            val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIndex == -1) return@mapNotNull null
            val match = timeRegex.find(lines[timeLineIndex]) ?: return@mapNotNull null
            val index = lines.getOrNull(0)?.toIntOrNull() ?: 0
            val text = lines.drop(timeLineIndex + 1).joinToString("\n").trim()
            SubtitleCue(
                index = index,
                startMs = parseSrtTime(match.groupValues[1]),
                endMs = parseSrtTime(match.groupValues[2]),
                text = text,
            )
        }
        return SubtitleDocument(SubtitleFormat.Srt, cues)
    }

    fun write(document: SubtitleDocument): String {
        return buildString {
            document.cues.forEachIndexed { i, cue ->
                appendLine(i + 1)
                append(formatSrtTime(cue.startMs))
                append(" --> ")
                appendLine(formatSrtTime(cue.endMs))
                appendLine(cue.text)
                appendLine()
            }
        }.trimEnd() + "\n"
    }
}

object VttSubtitleCodec {
    private val timeRegex = Regex("""(\d{1,2}:)?\d{2}:\d{2}\.\d{1,3}\s*-->\s*((\d{1,2}:)?\d{2}:\d{2}\.\d{1,3})(.*)""")

    fun parse(raw: String): SubtitleDocument {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').trim('\uFEFF').lines()
        val header = mutableListOf<String>()
        val cues = mutableListOf<SubtitleCue>()
        var i = 0
        if (lines.firstOrNull()?.trim()?.startsWith("WEBVTT") == true) {
            header += lines.first()
            i = 1
        }
        var cueIndex = 1
        while (i < lines.size) {
            while (i < lines.size && lines[i].isBlank()) i++
            if (i >= lines.size) break
            var identifier: String? = null
            if (!lines[i].contains("-->")) {
                identifier = lines[i]
                i++
            }
            if (i >= lines.size || !lines[i].contains("-->")) {
                i++
                continue
            }
            val timeLine = lines[i++]
            val parts = timeLine.split("-->", limit = 2)
            val start = parseVttTime(parts[0].trim())
            val endAndSettings = parts[1].trim()
            val endToken = endAndSettings.substringBefore(' ')
            val settings = endAndSettings.substringAfter(' ', "").takeIf { it.isNotBlank() } ?: identifier
            val textLines = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                textLines += lines[i++]
            }
            cues += SubtitleCue(cueIndex++, start, parseVttTime(endToken), textLines.joinToString("\n"), settings)
        }
        return SubtitleDocument(SubtitleFormat.Vtt, cues, header.ifEmpty { listOf("WEBVTT") })
    }

    fun write(document: SubtitleDocument): String {
        return buildString {
            val header = document.headerLines.ifEmpty { listOf("WEBVTT") }
            header.forEach { appendLine(it) }
            appendLine()
            document.cues.forEach { cue ->
                append(formatVttTime(cue.startMs))
                append(" --> ")
                append(formatVttTime(cue.endMs))
                cue.settings?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
                appendLine()
                appendLine(cue.text)
                appendLine()
            }
        }.trimEnd() + "\n"
    }
}

object AssSubtitleCodec {
    fun parse(raw: String): SubtitleDocument {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').trim('\uFEFF').lines()
        val eventIndex = lines.indexOfFirst { it.trim().equals("[Events]", ignoreCase = true) }
        if (eventIndex == -1) return SubtitleDocument(SubtitleFormat.Ass, emptyList(), lines)
        val header = lines.take(eventIndex + 1).toMutableList()
        var format: List<String> = emptyList()
        val cues = mutableListOf<SubtitleCue>()
        lines.drop(eventIndex + 1).forEach { line ->
            if (line.startsWith("Format:", ignoreCase = true)) {
                header += line
                format = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                return@forEach
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true) || format.isEmpty()) {
                if (cues.isEmpty()) header += line
                return@forEach
            }
            val payload = line.substringAfter(':').trimStart()
            val fields = payload.split(',', limit = format.size)
            val startIdx = format.indexOf("start")
            val endIdx = format.indexOf("end")
            val textIdx = format.indexOf("text")
            if (startIdx == -1 || endIdx == -1 || textIdx == -1 || fields.size <= textIdx) return@forEach
            val text = fields[textIdx].replace("\\N", "\n")
            cues += SubtitleCue(
                index = cues.size + 1,
                startMs = parseAssTime(fields[startIdx].trim()),
                endMs = parseAssTime(fields[endIdx].trim()),
                text = text,
                settings = fields.take(textIdx).joinToString(","),
            )
        }
        return SubtitleDocument(SubtitleFormat.Ass, cues, header)
    }

    fun write(document: SubtitleDocument): String {
        return buildString {
            document.headerLines.forEach { appendLine(it) }
            document.cues.forEach { cue ->
                val prefix =
                    cue.settings ?: "0,${formatAssTime(cue.startMs)},${formatAssTime(cue.endMs)},Default,,0,0,0,,"
                append("Dialogue: ")
                append(prefix)
                if (!prefix.endsWith(',')) append(',')
                appendLine(cue.text.replace("\n", "\\N"))
            }
        }.trimEnd() + "\n"
    }
}

private fun parseSrtTime(value: String): Long {
    val parts = value.split(':', ',')
    return ((parts[0].toLong() * 60 * 60 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000) +
        parts[3].padEnd(3, '0').take(3).toLong()
}

private fun formatSrtTime(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    val s = (ms / 1_000) % 60
    val milli = ms % 1_000
    return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
}

private fun parseVttTime(value: String): Long {
    val parts = value.split(':')
    val h: Long
    val m: Long
    val sec: String
    if (parts.size == 3) {
        h = parts[0].toLong()
        m = parts[1].toLong()
        sec = parts[2]
    } else {
        h = 0
        m = parts[0].toLong()
        sec = parts[1]
    }
    val sParts = sec.split('.')
    return ((h * 60 * 60 + m * 60 + sParts[0].toLong()) * 1000) +
        sParts.getOrElse(1) { "0" }.padEnd(3, '0').take(3).toLong()
}

private fun formatVttTime(ms: Long): String = formatSrtTime(ms).replace(',', '.')

private fun parseAssTime(value: String): Long {
    val parts = value.split(':', '.')
    return ((parts[0].toLong() * 60 * 60 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000) +
        parts.getOrElse(3) { "0" }.padEnd(2, '0').take(2).toLong() * 10
}

private fun formatAssTime(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    val s = (ms / 1_000) % 60
    val cs = (ms % 1_000) / 10
    return "%d:%02d:%02d.%02d".format(h, m, s, cs)
}
