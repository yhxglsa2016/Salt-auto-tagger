package com.salt.autotagger.spw

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

object SourceProbeRunner {
    private val running = AtomicBoolean(false)
    private val sourcesById = LyricsSourcesRegistry.createDefaultSources().associateBy { it.id }
    private const val defaultProbeKeyword = "认真的雪"

    fun startAsync(onFinished: (SourceProbeSummary) -> Unit): ProbeStartResult {
        if (!running.compareAndSet(false, true)) {
            return ProbeStartResult.AlreadyRunning
        }

        val thread = Thread({
            try {
                val summary = runProbe()
                onFinished(summary)
            } finally {
                running.set(false)
            }
        }, "salt-auto-tagger-source-probe")
        thread.isDaemon = true
        thread.start()
        return ProbeStartResult.Started
    }

    private fun runProbe(): SourceProbeSummary {
        val probeQuery = resolveProbeQuery()
        val enabledSourceIds = SaltAutoTaggerRuntime.settings.enabledSourceOrder()
        if (enabledSourceIds.isEmpty()) {
            return SourceProbeSummary(
                query = probeQuery,
                total = 0,
                successCount = 0,
                failedCount = 0,
                skippedCount = 0,
                results = emptyList()
            )
        }

        DebugLog.info(
            "probe_start | keyword=${probeQuery.title} | fallbackUsed=${probeQuery.title == defaultProbeKeyword} | enabledSources=${enabledSourceIds.joinToString(",")}"
        )

        val results = enabledSourceIds.mapNotNull { sourceId ->
            val source = sourcesById[sourceId]
            if (source == null) {
                DebugLog.warn("probe_source_failed | source=$sourceId | reason=source_not_registered")
                null
            } else {
                probeSingleSource(source, probeQuery)
            }
        }

        val summary = SourceProbeSummary(
            query = probeQuery,
            total = results.size,
            successCount = results.count { it.status == ProbeStatus.Success },
            failedCount = results.count { it.status == ProbeStatus.Failed },
            skippedCount = max(0, enabledSourceIds.size - results.size),
            results = results
        )

        DebugLog.info(
            "probe_finished | keyword=${probeQuery.title} | total=${summary.total} | success=${summary.successCount} | failed=${summary.failedCount} | skipped=${summary.skippedCount}"
        )
        return summary
    }

    private fun resolveProbeQuery(): SongQuery {
        val configuredKeyword = SaltAutoTaggerRuntime.settings.sourceProbeKeyword.trim()
        val keyword = configuredKeyword.ifBlank { defaultProbeKeyword }
        return SongQuery(
            title = keyword,
            artist = "",
            album = "",
            albumArtist = "",
            filePath = ""
        )
    }

    private fun probeSingleSource(source: LyricsSource, query: SongQuery): SourceProbeResult {
        val startedAt = System.nanoTime()
        return try {
            val candidates = source.search(query)
            if (candidates.isEmpty()) {
                val elapsed = elapsedMillis(startedAt)
                DebugLog.warn(
                    "probe_source_failed | source=${source.id} | durationMs=$elapsed | candidates=0 | reason=no_candidates"
                )
                SourceProbeResult(
                    sourceId = source.id,
                    status = ProbeStatus.Failed,
                    durationMillis = elapsed,
                    candidateCount = 0,
                    failureReason = "no_candidates",
                    selectedCandidate = null
                )
            } else {
                val candidate = candidates
                    .map { it to LyricsMatch.score(query, it) }
                    .filter { (_, score) -> score.titleScore > 0 }
                    .sortedByDescending { (_, score) -> score.total }
                    .firstOrNull()
                    ?.first

                if (candidate == null) {
                    val elapsed = elapsedMillis(startedAt)
                    DebugLog.warn(
                        "probe_source_failed | source=${source.id} | durationMs=$elapsed | candidates=${candidates.size} | reason=no_matching_candidate"
                    )
                    SourceProbeResult(
                        sourceId = source.id,
                        status = ProbeStatus.Failed,
                        durationMillis = elapsed,
                        candidateCount = candidates.size,
                        failureReason = "no_matching_candidate",
                        selectedCandidate = null
                    )
                } else {
                    val lyrics = source.fetchLyrics(candidate)
                        ?.let { LyricsText.normalize(source.id, it) }

                    val elapsed = elapsedMillis(startedAt)
                    if (lyrics.isNullOrBlank()) {
                        DebugLog.warn(
                    "probe_source_failed | source=${source.id} | durationMs=$elapsed | candidates=${candidates.size} | reason=no_usable_lyrics | candidateTitle=${DebugLog.field(candidate.title)} | candidateArtist=${DebugLog.field(candidate.artist)}"
                        )
                        SourceProbeResult(
                            sourceId = source.id,
                            status = ProbeStatus.Failed,
                            durationMillis = elapsed,
                            candidateCount = candidates.size,
                            failureReason = "no_usable_lyrics",
                            selectedCandidate = candidate
                        )
                    } else {
                        DebugLog.info(
                            "probe_source_success | source=${source.id} | durationMs=$elapsed | candidates=${candidates.size} | candidateTitle=${DebugLog.field(candidate.title)} | candidateArtist=${DebugLog.field(candidate.artist)} | lyricsLength=${lyrics.length}"
                        )
                        SourceProbeResult(
                            sourceId = source.id,
                            status = ProbeStatus.Success,
                            durationMillis = elapsed,
                            candidateCount = candidates.size,
                            failureReason = null,
                            selectedCandidate = candidate
                        )
                    }
                }
            }
        } catch (error: Exception) {
            val elapsed = elapsedMillis(startedAt)
            val reason = DebugLog.field(error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName ?: "unknown_error")
            DebugLog.warn(
                "probe_source_failed | source=${source.id} | durationMs=$elapsed | candidates=0 | reason=$reason"
            )
            SourceProbeResult(
                sourceId = source.id,
                status = ProbeStatus.Failed,
                durationMillis = elapsed,
                candidateCount = 0,
                failureReason = reason,
                selectedCandidate = null
            )
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis()
}

sealed class ProbeStartResult {
    data object Started : ProbeStartResult()
    data object AlreadyRunning : ProbeStartResult()
}

enum class ProbeStatus {
    Success,
    Failed
}

data class SourceProbeResult(
    val sourceId: LyricsSourceId,
    val status: ProbeStatus,
    val durationMillis: Long,
    val candidateCount: Int,
    val failureReason: String?,
    val selectedCandidate: LyricsCandidate?
)

data class SourceProbeSummary(
    val query: SongQuery,
    val total: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val results: List<SourceProbeResult>
)

