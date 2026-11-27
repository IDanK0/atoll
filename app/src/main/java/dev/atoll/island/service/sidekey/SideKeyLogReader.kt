package dev.atoll.island.service.sidekey

import android.os.SystemClock
import android.util.Log
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.domain.sidekey.SideKeyCaptureAnalyzer
import dev.atoll.island.domain.sidekey.SideKeyEventDeduplicator
import dev.atoll.island.domain.sidekey.SideKeyLogParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.util.Locale

internal class SideKeyLogReader {

    enum class ExitReason { CONSENT_REQUIRED, PERMISSION_DENIED, FAILED }

    suspend fun streamEvents(
        patternProvider: () -> String,
        onListening: suspend () -> Unit,
        onEvent: suspend (SideKeyLogParser.ParsedKeyEvent) -> Unit
    ): Pair<ExitReason, String?> = withContext(Dispatchers.IO) {
        var sawStreamStart = false
        val deduplicator = SideKeyEventDeduplicator(SideKeyConfig.DEDUP_WINDOW_MS)
        var lastPattern: String? = null
        var compiledRegex: Regex? = null

        try {
            withLogcat { process, reader ->
                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    if (!sawStreamStart) {
                        if (isErrorLine(line)) return@withLogcat ExitReason.CONSENT_REQUIRED to line
                        val raw = SideKeyLogParser.parseRawUidLine(line) ?: continue
                        if (!raw.isSystemUid) continue
                        sawStreamStart = true
                        onListening()
                    }
                    val pattern = patternProvider()
                    if (pattern != lastPattern) {
                        lastPattern = pattern
                        compiledRegex = SideKeyLogParser.compilePattern(pattern)
                    }

                    val nowMs = SystemClock.elapsedRealtime()
                    val event = SideKeyLogParser.parseLine(
                        line = line,
                        filterPattern = pattern,
                        compiledCustomRegex = compiledRegex,
                        monotonicNowMs = nowMs
                    ) ?: continue
                    if (deduplicator.accept(event, arrivalMs = nowMs)) onEvent(event)
                }
                val exitCode = runCatching { process.exitValue() }.getOrNull()
                if (!sawStreamStart || exitCode != 0) ExitReason.CONSENT_REQUIRED to null
                else ExitReason.FAILED to null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing READ_LOGS permission", error)
            ExitReason.PERMISSION_DENIED to error.message
        } catch (error: Exception) {
            Log.w(TAG, "Logcat stream ended", error)
            if (sawStreamStart) ExitReason.FAILED to error.message
            else ExitReason.CONSENT_REQUIRED to error.message
        }
    }

    suspend fun captureCandidate(
        onCandidateLine: suspend (count: Int, preview: String) -> Unit
    ): SideKeyCaptureAnalyzer.CaptureCandidate? = withContext(Dispatchers.IO) {
        val collected = mutableListOf<String>()
        var pressCount = 0

        try {
            withLogcat { _, reader ->
                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    if (isErrorLine(line)) break
                    val raw = SideKeyCaptureAnalyzer.isCandidateLine(line) ?: continue
                    collected += raw.rawLine
                    if (raw.message.contains("ACTION_DOWN")) pressCount += 1
                    onCandidateLine(pressCount.coerceAtLeast((collected.size + 1) / 2), "${raw.tag}: ${raw.message}")
                    if (pressCount >= 3 && collected.size >= 4) {
                        val candidate = SideKeyCaptureAnalyzer.analyze(collected)
                        if (candidate != null) return@withLogcat candidate
                    }
                }
                SideKeyCaptureAnalyzer.analyze(collected)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Detect Key capture failed", error)
            SideKeyCaptureAnalyzer.analyze(collected)
        }
    }

    private suspend fun <T> withLogcat(read: suspend (Process, BufferedReader) -> T): T = coroutineScope {
        val now = System.currentTimeMillis()
        val startTime = String.format(Locale.ROOT, "%d.%03d", now / 1_000, now % 1_000)
        val process = ProcessBuilder("logcat", "-v", "uid", "--uid=1000", "-T", startTime)
            .redirectErrorStream(true)
            .start()
        // readLine() è bloccante: chiudiamo il processo anche se la coroutine di lettura non torna
        val cleanup = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                process.destroyForcibly()
            }
        }
        try {
            process.inputStream.bufferedReader().use { read(process, it) }
        } finally {
            cleanup.cancel()
        }
    }

    private fun isErrorLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("permission denied") ||
            lower.contains("read failure") ||
            lower.contains("failed to open") ||
            lower.contains("unexpected eof")
    }

    private companion object {
        const val TAG = "AtollSideKeyLog"
    }
}
