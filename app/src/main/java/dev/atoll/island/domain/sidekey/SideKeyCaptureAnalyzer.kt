package dev.atoll.island.domain.sidekey

import dev.atoll.island.data.model.SideKeyConfig

object SideKeyCaptureAnalyzer {

    data class CaptureCandidate(
        val tag: String,
        val token: String,
        val regexPattern: String,
        val sampleLine: String,
        val matchCount: Int,
        val hasDownAndUp: Boolean
    )

    private val INTERESTING_KEYWORDS = Regex("""KEYLOG|KeyEvent|scanCode=|keyCode=""", RegexOption.IGNORE_CASE)
    private val KEYCODE_TOKEN = Regex("""KEYCODE_[A-Z0-9_]+""")
    private val SCANCODE_TOKEN = Regex("""\bscanCode=\d+""")
    private val NUMERIC_KEYCODE_TOKEN = Regex("""\bkeyCode=\d+""")
    private val IGNORED_TOKENS = setOf("KEYCODE_UNKNOWN", "KEYCODE_0", "scanCode=0", "keyCode=0")

    fun isCandidateLine(line: String): SideKeyLogParser.RawLogLine? {
        val raw = SideKeyLogParser.parseRawUidLine(line) ?: return null
        if (!raw.isSystemUid) return null
        if (!INTERESTING_KEYWORDS.containsMatchIn(line)) return null
        return raw
    }

    fun analyze(lines: List<String>): CaptureCandidate? {
        data class Bucket(
            val tag: String,
            val token: String,
            val lines: MutableList<String> = mutableListOf(),
            var sawDown: Boolean = false,
            var sawUp: Boolean = false
        )

        val buckets = linkedMapOf<Pair<String, String>, Bucket>()
        for (line in lines) {
            val raw = isCandidateLine(line) ?: continue
            val token = extractToken(raw.message) ?: continue
            val key = raw.tag to token
            val bucket = buckets.getOrPut(key) { Bucket(raw.tag, token) }
            bucket.lines += raw.rawLine
            if (line.contains("ACTION_DOWN")) bucket.sawDown = true
            if (line.contains("ACTION_UP")) bucket.sawUp = true
        }

        val best = buckets.values
            .filter { it.lines.size >= 2 }
            .maxWithOrNull(
                compareBy<Bucket>(
                    { if (it.sawDown && it.sawUp) 1 else 0 },
                    { if (it.tag.startsWith("KEYLOG_")) 1 else 0 },
                    { it.lines.size }
                )
            ) ?: buckets.values.maxByOrNull { it.lines.size } ?: return null

        val escapedTag = Regex.escape(best.tag)
        val escapedToken = Regex.escape(best.token)
        val regex = if (best.token == "KEYCODE_ACTION_BUTTON_CLICK" || best.token == "scanCode=735") {
            SideKeyConfig.DEFAULT_FILTER_PATTERN
        } else {
            "$escapedTag.*$escapedToken"
        }

        return CaptureCandidate(
            tag = best.tag,
            token = best.token,
            regexPattern = regex,
            sampleLine = best.lines.last(),
            matchCount = best.lines.size,
            hasDownAndUp = best.sawDown && best.sawUp
        )
    }

    private fun extractToken(message: String): String? {
        val keyCode = KEYCODE_TOKEN.find(message)?.value?.takeIf { it !in IGNORED_TOKENS }
        if (keyCode != null) return keyCode
        val scanCode = SCANCODE_TOKEN.find(message)?.value?.takeIf { it !in IGNORED_TOKENS }
        if (scanCode != null) return scanCode
        return NUMERIC_KEYCODE_TOKEN.find(message)?.value?.takeIf { it !in IGNORED_TOKENS }
    }
}
