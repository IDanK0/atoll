package dev.atoll.island.domain.card

enum class NoteSpanStyle { BOLD, ITALIC, STRIKE, CODE, HEADING, BULLET }

data class NoteSpan(val start: Int, val end: Int, val style: NoteSpanStyle)

data class ParsedNote(val text: String, val spans: List<NoteSpan> = emptyList())

object NoteMarkdown {

    const val BULLET_PREFIX = "\u2022 "

    fun parse(source: String): ParsedNote {
        val normalized = source.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return ParsedNote("")

        val out = StringBuilder(normalized.length)
        val spans = ArrayList<NoteSpan>()
        val lines = normalized.split('\n')

        lines.forEachIndexed { index, rawLine ->
            if (index > 0) out.append('\n')
            val lineStart = out.length
            val heading = headingContent(rawLine)
            if (heading != null) {
                appendInline(heading, out, spans)
                if (out.length > lineStart) {
                    spans.add(NoteSpan(lineStart, out.length, NoteSpanStyle.HEADING))
                }
            } else {
                val bullet = bulletContent(rawLine)
                if (bullet != null) {
                    out.append(BULLET_PREFIX)
                    appendInline(bullet, out, spans)
                    if (out.length > lineStart) {
                        spans.add(NoteSpan(lineStart, out.length, NoteSpanStyle.BULLET))
                    }
                } else {
                    appendInline(rawLine, out, spans)
                }
            }
        }
        return ParsedNote(out.toString(), spans)
    }

    private fun headingContent(line: String): String? {
        val trimmed = line.trimStart()
        var hashes = 0
        while (hashes < trimmed.length && hashes < 6 && trimmed[hashes] == '#') {
            hashes++
        }
        if (hashes == 0 || hashes >= trimmed.length) return null
        if (trimmed[hashes] != ' ' && trimmed[hashes] != '\t') return null
        return trimmed.substring(hashes + 1).trimStart().takeIf { it.isNotEmpty() }
    }

    private fun bulletContent(line: String): String? {
        val trimmed = line.trimStart()
        if (trimmed.length < 2) return null
        if (trimmed[0] != '-' && trimmed[0] != '*') return null
        if (trimmed[1] != ' ' && trimmed[1] != '\t') return null
        return trimmed.substring(2).trimStart().takeIf { it.isNotEmpty() }
    }

    private fun appendInline(input: String, out: StringBuilder, spans: MutableList<NoteSpan>) {
        var i = 0
        while (i < input.length) {
            when {
                input[i] == '`' -> {
                    val close = input.indexOf('`', i + 1)
                    if (close > i + 1) {
                        val start = out.length
                        out.append(input, i + 1, close)
                        spans.add(NoteSpan(start, out.length, NoteSpanStyle.CODE))
                        i = close + 1
                        continue
                    }
                }

                input[i] == '[' -> {
                    val link = parseLink(input, i)
                    if (link != null) {
                        appendInline(link.label, out, spans)
                        i = link.nextIndex
                        continue
                    }
                }

                input.startsWith("**", i) -> {
                    val close = findPairClose(input, "**", i + 2)
                    if (close != -1) {
                        val start = out.length
                        appendInline(input.substring(i + 2, close), out, spans)
                        if (out.length > start) {
                            spans.add(NoteSpan(start, out.length, NoteSpanStyle.BOLD))
                        }
                        i = close + 2
                        continue
                    }
                }

                input.startsWith("~~", i) -> {
                    val close = findPairClose(input, "~~", i + 2)
                    if (close != -1) {
                        val start = out.length
                        appendInline(input.substring(i + 2, close), out, spans)
                        if (out.length > start) {
                            spans.add(NoteSpan(start, out.length, NoteSpanStyle.STRIKE))
                        }
                        i = close + 2
                        continue
                    }
                }

                input[i] == '*' && !input.startsWith("**", i) -> {
                    val close = findSingleItalicClose(input, '*', i + 1)
                    if (close != -1) {
                        val start = out.length
                        appendInline(input.substring(i + 1, close), out, spans)
                        if (out.length > start) {
                            spans.add(NoteSpan(start, out.length, NoteSpanStyle.ITALIC))
                        }
                        i = close + 1
                        continue
                    }
                }

                input[i] == '_' && (i == 0 || !input[i - 1].isLetterOrDigit()) -> {
                    val close = findSingleItalicClose(input, '_', i + 1, wordBoundary = true)
                    if (close != -1) {
                        val start = out.length
                        appendInline(input.substring(i + 1, close), out, spans)
                        if (out.length > start) {
                            spans.add(NoteSpan(start, out.length, NoteSpanStyle.ITALIC))
                        }
                        i = close + 1
                        continue
                    }
                }
            }
            out.append(input[i])
            i++
        }
    }

    private data class ParsedLink(val label: String, val nextIndex: Int)

    private fun parseLink(input: String, openBracket: Int): ParsedLink? {
        var i = openBracket + 1
        while (i < input.length && input[i] != ']' && input[i] != '[') i++
        if (i >= input.length || input[i] != ']' || i == openBracket + 1) return null
        if (i + 1 >= input.length || input[i + 1] != '(') return null
        var depth = 1
        var j = i + 2
        while (j < input.length && depth > 0) {
            when (input[j]) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth > 0) j++
        }
        if (depth != 0) return null
        val url = input.substring(i + 2, j).trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        return ParsedLink(label = input.substring(openBracket + 1, i), nextIndex = j + 1)
    }

    private fun findPairClose(input: String, marker: String, from: Int): Int {
        if (from >= input.length || input[from].isWhitespace()) return -1
        var idx = input.indexOf(marker, from)
        while (idx != -1) {
            if (idx > from && !input[idx - 1].isWhitespace()) return idx
            idx = input.indexOf(marker, idx + 1)
        }
        return -1
    }

    private fun findSingleItalicClose(
        input: String,
        marker: Char,
        from: Int,
        wordBoundary: Boolean = false
    ): Int {
        if (from >= input.length || input[from].isWhitespace()) return -1
        var i = from
        while (i < input.length) {
            if (input[i] == '`') {
                val endCode = input.indexOf('`', i + 1)
                if (endCode != -1) {
                    i = endCode + 1
                    continue
                }
            }
            if (input[i] == marker) {
                val doubled = (i > 0 && input[i - 1] == marker) || (i + 1 < input.length && input[i + 1] == marker)
                val endBoundaryOk = !wordBoundary || i + 1 >= input.length || !input[i + 1].isLetterOrDigit()
                if (!doubled && endBoundaryOk && i > from && !input[i - 1].isWhitespace()) {
                    return i
                }
            }
            i++
        }
        return -1
    }
}
