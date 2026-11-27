package dev.atoll.island.domain.sidekey

import dev.atoll.island.data.model.SideKeyConfig

object SideKeyLogParser {

    enum class KeyEdge { DOWN, UP }

    data class ParsedKeyEvent(
        val uid: String,
        val tag: String,
        val edge: KeyEdge,
        val timestampMs: Long,
        val hasEventTime: Boolean,
        val rawLine: String
    )

    data class RawLogLine(
        val uid: String,
        val tag: String,
        val message: String,
        val rawLine: String
    ) {
        val isSystemUid: Boolean
            get() = uid == UID_SYSTEM_NUMERIC || uid == UID_SYSTEM_NAME
    }

    private val LOGCAT_UID_LINE = Regex(
        """^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+(\S+)\s+\d+\s+\d+\s+[VDIWEFA]\s+([^:]+):\s*(.*)$"""
    )
    private val EDGE_DOWN = Regex("""(?:action\s*=\s*|\b)ACTION_DOWN\b""")
    private val EDGE_UP = Regex("""(?:action\s*=\s*|\b)ACTION_UP\b""")
    private val EVENT_TIME = Regex("""\beventTime\s*[=:]\s*(\d+)""")

    private val DEFAULT_TAGS = setOf(
        "KEYLOG_PhoneWindowManagerExtImpl",
        "KEYLOG_OplusKeyEventUtil"
    )
    private val DEFAULT_KEY_MARKERS = Regex("""KEYCODE_ACTION_BUTTON_CLICK|\bscanCode=735\b""")

    fun parseRawUidLine(line: String): RawLogLine? {
        val match = LOGCAT_UID_LINE.matchEntire(line) ?: return null
        val uid = match.groupValues[1].trim()
        val tag = match.groupValues[2].trim()
        val message = match.groupValues[3]
        return RawLogLine(uid = uid, tag = tag, message = message, rawLine = line)
    }

    fun compilePattern(customPattern: String): Regex? {
        val trimmed = customPattern.trim()
        if (trimmed.isEmpty() || trimmed == SideKeyConfig.DEFAULT_FILTER_PATTERN) return null
        return runCatching { Regex(trimmed) }.getOrNull()
    }

    fun isValidRegex(pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return true
        return runCatching { Regex(trimmed) }.isSuccess
    }

    fun parseLine(
        line: String,
        filterPattern: String = SideKeyConfig.DEFAULT_FILTER_PATTERN,
        compiledCustomRegex: Regex? = compilePattern(filterPattern),
        monotonicNowMs: Long = 0L
    ): ParsedKeyEvent? {
        val raw = parseRawUidLine(line) ?: return null
        // Rifiuta qualsiasi UID non di sistema: evita eventi finti da app di terze parti.
        if (!raw.isSystemUid) return null

        val trimmedFilter = filterPattern.trim()
        val isDefault = trimmedFilter.isEmpty() || trimmedFilter == SideKeyConfig.DEFAULT_FILTER_PATTERN
        val matchesTarget = if (isDefault) {
            raw.tag in DEFAULT_TAGS && DEFAULT_KEY_MARKERS.containsMatchIn(raw.message)
        } else {
            val regex = compiledCustomRegex ?: return null
            regex.containsMatchIn("${raw.tag}: ${raw.message}")
        }
        if (!matchesTarget) return null

        val edge = when {
            EDGE_DOWN.containsMatchIn(raw.message) -> KeyEdge.DOWN
            EDGE_UP.containsMatchIn(raw.message) -> KeyEdge.UP
            else -> return null
        }

        val extractedTime = EVENT_TIME.find(raw.message)?.groupValues?.get(1)?.toLongOrNull()
        return ParsedKeyEvent(
            uid = raw.uid,
            tag = raw.tag,
            edge = edge,
            timestampMs = extractedTime ?: monotonicNowMs,
            hasEventTime = extractedTime != null,
            rawLine = line
        )
    }

    private const val UID_SYSTEM_NUMERIC = "1000"
    private const val UID_SYSTEM_NAME = "system"
}

class SideKeyEventDeduplicator(
    private val windowMs: Long = SideKeyConfig.DEDUP_WINDOW_MS
) {
    private data class EdgeRecord(
        val eventTimeMs: Long,
        val hasEventTime: Boolean,
        val arrivalMs: Long
    )

    private var lastDown: EdgeRecord? = null
    private var lastUp: EdgeRecord? = null

    fun accept(event: SideKeyLogParser.ParsedKeyEvent, arrivalMs: Long): Boolean {
        val previous = when (event.edge) {
            SideKeyLogParser.KeyEdge.DOWN -> lastDown
            SideKeyLogParser.KeyEdge.UP -> lastUp
        }
        if (previous != null) {
            val sameHardwareWindow = event.hasEventTime && previous.hasEventTime &&
                kotlin.math.abs(event.timestampMs - previous.eventTimeMs) < windowMs
            val sameArrivalWindow = (arrivalMs - previous.arrivalMs) in 0 until windowMs
            if (sameHardwareWindow || sameArrivalWindow) return false
        }
        val next = EdgeRecord(event.timestampMs, event.hasEventTime, arrivalMs)
        when (event.edge) {
            SideKeyLogParser.KeyEdge.DOWN -> lastDown = next
            SideKeyLogParser.KeyEdge.UP -> lastUp = next
        }
        return true
    }

    fun reset() {
        lastDown = null
        lastUp = null
    }
}
