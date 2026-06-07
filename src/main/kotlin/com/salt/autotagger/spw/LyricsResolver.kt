package com.salt.autotagger.spw

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

object LyricsResolver {
    private val cache = ConcurrentHashMap<String, ResolvedLyrics?>()
    private val failedOnlineLookups = ConcurrentHashMap<String, Long>()
    private val failedLookupTtlMillis = Duration.ofSeconds(15).toMillis()
    private val onlineResolver = OnlineLyricsResolver(
        LyricsSourcesRegistry.createDefaultSources()
    )

    fun resolve(mediaItem: PlaybackExtensionPoint.MediaItem): ResolvedLyrics? {
        val path = mediaItem.path
        DebugLog.info(
            "resolve_start | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(path)}"
        )
        cache[path]?.let {
            DebugLog.info(
                "cache_hit | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(path)}"
            )
            return it
        }

        resolveLocal(mediaItem)?.let {
            DebugLog.info(
                "local_hit | origin=${it.origin} | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(path)}"
            )
            cache[path] = it
            failedOnlineLookups.remove(path)
            return it
        }

        val settings = SaltAutoTaggerRuntime.settings
        if (!settings.onlineEnabled || settings.enabledSourceOrder().isEmpty()) {
            DebugLog.warn(
                "online_skipped | reason=disabled_or_no_sources | onlineEnabled=${settings.onlineEnabled} | enabledSources=${settings.enabledSourceOrder().size} | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(path)}"
            )
            return null
        }

        val query = SongQuery(
            title = mediaItem.title,
            artist = mediaItem.artist,
            album = mediaItem.album,
            albumArtist = mediaItem.albumArtist,
            filePath = mediaItem.path
        )

        if (shouldSkipRecentFailedLookup(path)) {
            DebugLog.info(
                "online_skipped | reason=recent_failed_lookup | title=${DebugLog.field(query.title)} | artist=${DebugLog.field(query.artist)} | path=${DebugLog.field(path)}"
            )
            return null
        }

        DebugLog.info(
            "online_start | title=${DebugLog.field(query.title)} | artist=${DebugLog.field(query.artist)} | album=${DebugLog.field(query.album)} | path=${DebugLog.field(query.filePath)}"
        )

        val online = onlineResolver.resolve(query)?.let { result ->
            persistOnlineLyrics(query, result)
            DebugLog.info(
                "online_success | source=${result.sourceId} | candidateTitle=${DebugLog.field(result.candidate.title)} | candidateArtist=${DebugLog.field(result.candidate.artist)} | title=${DebugLog.field(query.title)} | artist=${DebugLog.field(query.artist)} | path=${DebugLog.field(query.filePath)}"
            )
            ResolvedLyrics(
                lyrics = result.lyrics,
                origin = LyricsOrigin.Online,
                sourceId = result.sourceId
            )
        }

        if (online != null) {
            cache[path] = online
            failedOnlineLookups.remove(path)
        } else {
            failedOnlineLookups[path] = System.currentTimeMillis()
            DebugLog.warn(
                "online_failed | reason=no_lyrics | title=${DebugLog.field(query.title)} | artist=${DebugLog.field(query.artist)} | path=${DebugLog.field(query.filePath)}"
            )
        }
        return online
    }

    private fun shouldSkipRecentFailedLookup(path: String): Boolean {
        val lastFailedAt = failedOnlineLookups[path] ?: return false
        val age = System.currentTimeMillis() - lastFailedAt
        if (age <= failedLookupTtlMillis) {
            return true
        }
        failedOnlineLookups.remove(path, lastFailedAt)
        return false
    }

    private fun resolveLocal(mediaItem: PlaybackExtensionPoint.MediaItem): ResolvedLyrics? {
        val settings = SaltAutoTaggerRuntime.settings

        if (settings.preferSidecar) {
            DebugLog.info("local_start | type=sidecar | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
            loadSidecarLyrics(mediaItem.path)?.let {
                DebugLog.info("local_hit | type=sidecar | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
                return ResolvedLyrics(it, LyricsOrigin.Sidecar, null)
            }
            DebugLog.info("local_miss | type=sidecar | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
        } else {
            DebugLog.info("local_skipped | type=sidecar | reason=disabled")
        }

        if (settings.preferOverrideFolder) {
            DebugLog.info("local_start | type=override | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
            loadOverrideLyrics(mediaItem)?.let {
                DebugLog.info("local_hit | type=override | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
                return ResolvedLyrics(it, LyricsOrigin.Override, null)
            }
            DebugLog.info("local_miss | type=override | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
        } else {
            DebugLog.info("local_skipped | type=override | reason=disabled")
        }

        DebugLog.info("local_start | type=embedded_tag | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
        EmbeddedLyricsReader.read(mediaItem.path)?.let {
            DebugLog.info("local_hit | type=embedded_tag | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
            return ResolvedLyrics(it, LyricsOrigin.EmbeddedTag, null)
        }
        DebugLog.info("local_miss | type=embedded_tag | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
        DebugLog.warn("local_failed | reason=no_local_lyrics | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")

        return null
    }

    private fun persistOnlineLyrics(query: SongQuery, result: OnlineLyricsResult) {
        when (SaltAutoTaggerRuntime.settings.saveMode) {
            SaveMode.DISPLAY_ONLY -> {
                DebugLog.info(
                    "save_skipped | mode=display_only | reason=display_only | source=${result.sourceId} | title=${DebugLog.field(query.title)} | path=${DebugLog.field(query.filePath)}"
                )
            }
            SaveMode.SAVE_LRC -> {
                when (val outcome = LrcPersistence.saveIfAbsent(query.filePath, result.lyrics)) {
                    SaveOutcome.Saved -> {
                        DebugLog.info("save_success | mode=save_lrc | source=${result.sourceId} | path=${DebugLog.field(query.filePath)}")
                        maybeToast(I18n.text(TextKey.LrcSaved))
                    }
                    is SaveOutcome.SkippedExisting -> {
                        DebugLog.info("save_skipped | mode=save_lrc | reason=${outcome.reason} | source=${result.sourceId} | path=${DebugLog.field(query.filePath)}")
                        maybeToast(I18n.text(TextKey.LrcSkipped))
                    }
                    is SaveOutcome.Failed -> {
                        DebugLog.warn("save_failed | mode=save_lrc | reason=${outcome.reason} | source=${result.sourceId} | path=${DebugLog.field(query.filePath)}")
                        maybeToast(I18n.text(TextKey.TagSkipped))
                    }
                }
            }

            SaveMode.WRITE_TAG -> {
                when (val outcome = TagLyricsWriter.writeIfMissing(query.filePath, result.lyrics)) {
                    SaveOutcome.Saved -> {
                        DebugLog.info("save_success | mode=write_tag | source=${result.sourceId} | path=${DebugLog.field(query.filePath)}")
                        maybeToast(I18n.text(TextKey.TagWritten))
                    }
                    is SaveOutcome.SkippedExisting -> {
                        DebugLog.info("save_skipped | mode=write_tag | reason=${outcome.reason} | source=${result.sourceId} | path=${DebugLog.field(query.filePath)}")
                        maybeToast(I18n.text(TextKey.TagSkipped))
                    }
                    is SaveOutcome.Failed -> {
                        DebugLog.warn("save_failed | mode=write_tag | reason=${outcome.reason} | source=${result.sourceId} | path=${DebugLog.field(query.filePath)}")
                        maybeToast(I18n.text(TextKey.TagSkipped))
                    }
                }
            }
        }
    }

    private fun loadSidecarLyrics(songPath: String): String? {
        val source = Path.of(songPath)
        val baseName = fileNameWithoutExtension(source.fileName.toString())
        val parent = source.parent ?: return null

        return listOf(".lrc", ".txt").asSequence()
            .map { parent.resolve(baseName + it) }
            .firstNotNullOfOrNull(LocalLyricsFiles::readLyricsFile)
    }

    private fun loadOverrideLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        val root = SaltAutoTaggerRuntime.overrideDirectory()
        val fileNames = buildOverrideNames(mediaItem)

        return fileNames.asSequence()
            .flatMap { fileName -> listOf(".lrc", ".txt").asSequence().map { ext -> root.resolve(fileName + ext) } }
            .firstNotNullOfOrNull(LocalLyricsFiles::readLyricsFile)
    }

    private fun buildOverrideNames(mediaItem: PlaybackExtensionPoint.MediaItem): List<String> {
        val artistTitle = listOf(mediaItem.artist, mediaItem.title)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        val titleOnly = mediaItem.title
        val pathStem = fileNameWithoutExtension(Path.of(mediaItem.path).fileName.toString())

        return listOf(artistTitle, titleOnly, pathStem)
            .map(::sanitizeFileName)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun fileNameWithoutExtension(fileName: String): String =
        fileName.substringBeforeLast('.', fileName)

    private fun sanitizeFileName(input: String): String =
        input
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.getDefault())

    private fun maybeToast(text: String) {
        if (SaltAutoTaggerRuntime.settings.showToast) {
            com.xuncorp.spw.workshop.api.WorkshopApi.ui.toast(
                text,
                com.xuncorp.spw.workshop.api.WorkshopApi.Ui.ToastType.Success
            )
        }
    }
}

data class ResolvedLyrics(
    val lyrics: String,
    val origin: LyricsOrigin,
    val sourceId: LyricsSourceId?
)

enum class LyricsOrigin {
    Sidecar,
    Override,
    EmbeddedTag,
    Online
}
