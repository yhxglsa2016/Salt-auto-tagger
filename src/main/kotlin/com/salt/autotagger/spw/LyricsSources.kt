package com.salt.autotagger.spw

import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import kotlin.math.max

data class SongQuery(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val filePath: String
)

data class LyricsCandidate(
    val sourceId: LyricsSourceId,
    val id: String,
    val title: String,
    val artist: String,
    val album: String
)

data class OnlineLyricsResult(
    val sourceId: LyricsSourceId,
    val candidate: LyricsCandidate,
    val lyrics: String
)

interface LyricsSource {
    val id: LyricsSourceId
    fun search(query: SongQuery): List<LyricsCandidate>
    fun fetchLyrics(candidate: LyricsCandidate): String?
}

object LyricsSourcesRegistry {
    fun createDefaultSources(): List<LyricsSource> = listOf(
        KugouLyricsSource(),
        QqMusicLyricsSource(),
        NeteaseLyricsSource()
    )
}

class OnlineLyricsResolver(
    sources: List<LyricsSource>
) {
    private val sourcesById = sources.associateBy { it.id }

    fun resolve(query: SongQuery): OnlineLyricsResult? {
        for (sourceId in SaltAutoTaggerRuntime.settings.enabledSourceOrder()) {
            val source = sourcesById[sourceId] ?: continue
            val sourceStartedAt = System.nanoTime()
            DebugLog.info(
                "source_start | source=$sourceId | title=${DebugLog.field(query.title)} | artist=${DebugLog.field(query.artist)}"
            )
            val result = runCatching {
                val candidate = searchCandidates(source, query)
                    .map { it to LyricsMatch.score(query, it) }
                    .filter { (_, score) -> score.titleScore > 0 }
                    .sortedByDescending { (_, score) -> score.total }
                    .firstOrNull()
                    ?.first
                    ?: run {
                        DebugLog.warn(
                            "source_failed | source=$sourceId | reason=no_matching_candidate | durationMs=${elapsedMillis(sourceStartedAt)} | title=${DebugLog.field(query.title)}"
                        )
                        return@runCatching null
                    }

                DebugLog.info(
                    "candidate_selected | source=$sourceId | candidateTitle=${DebugLog.field(candidate.title)} | candidateArtist=${DebugLog.field(candidate.artist)}"
                )
                val lyrics = source.fetchLyrics(candidate)
                    ?.let { LyricsText.normalize(sourceId, it) }
                    ?: run {
                        val reason = HttpSupport.consumeLastFailureReason() ?: "no_usable_lyrics"
                        DebugLog.warn(
                            "source_failed | source=$sourceId | reason=$reason | durationMs=${elapsedMillis(sourceStartedAt)} | candidateId=${DebugLog.field(candidate.id)}"
                        )
                        return@runCatching null
                    }
                OnlineLyricsResult(sourceId, candidate, lyrics)
            }.onFailure { error ->
                val reason = DebugLog.field(error.message ?: error::class.simpleName ?: "unknown_error")
                DebugLog.warn("source_failed | source=$sourceId | reason=$reason | durationMs=${elapsedMillis(sourceStartedAt)}")
            }.getOrNull()

            if (result != null) {
                DebugLog.info(
                    "source_success | source=$sourceId | durationMs=${elapsedMillis(sourceStartedAt)} | lyricsLength=${result.lyrics.length}"
                )
                return result
            }

            DebugLog.warn("source_failed | source=$sourceId | reason=no_result | durationMs=${elapsedMillis(sourceStartedAt)}")
        }

        return null
    }

    private fun searchCandidates(source: LyricsSource, query: SongQuery): List<LyricsCandidate> {
        for (searchTitle in SearchTerms.build(query)) {
            val startedAt = System.nanoTime()
            val candidates = source.search(query.copy(title = searchTitle))
            val reason = HttpSupport.consumeLastFailureReason()
            val suffix = if (reason == null) "" else " | reason=$reason"
            DebugLog.info(
                "search_attempt | source=${source.id} | keyword=${DebugLog.field(searchTitle)} | candidates=${candidates.size} | durationMs=${elapsedMillis(startedAt)}$suffix"
            )
            if (candidates.isNotEmpty()) {
                return candidates
            }
        }
        DebugLog.warn(
            "source_failed | source=${source.id} | reason=no_candidates | title=${DebugLog.field(query.title)} | artist=${DebugLog.field(query.artist)}"
        )
        return emptyList()
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis()
}

private object SearchTerms {
    private val trailingNoise = Regex(
        "\\s*[-_\\s]*(无数据|unknown|n/?a|live|ver\\.?|version|inst\\.?|instrumental|伴奏)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val bracketNoise = Regex(
        "\\s*[\\(\\[（【](\\s*(无数据|unknown|n/?a|live|ver\\.?|version|inst\\.?|instrumental|伴奏)\\s*)[\\)\\]）】]\\s*",
        RegexOption.IGNORE_CASE
    )
    private val bracketContent = Regex("\\s*[\\(\\[（【].*?[\\)\\]）】]\\s*")
    private val fileSeparators = Regex("[_]+")
    private val repeatedWhitespace = Regex("\\s+")
    private val edgePunctuation = Regex("^[\\s\\p{Punct}，。；：、]+|[\\s\\p{Punct}，。；：、]+$")

    fun build(query: SongQuery): List<String> {
        val baseTitle = query.title.trim()
        val cleanedTitle = normalizeKeyword(baseTitle)
        val titleWithoutBracketContent = normalizeKeyword(removeBracketContent(baseTitle))
        val extractedTitle = extractLikelyTitle(baseTitle)
        val extractedSimplifiedTitle = normalizeKeyword(extractedTitle)
        val artistJoined = listOf(extractedTitle.ifBlank { cleanedTitle }, normalizeKeyword(query.artist))
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return listOf(baseTitle, cleanedTitle, titleWithoutBracketContent, extractedTitle, extractedSimplifiedTitle, artistJoined)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalizeKeyword(text: String): String {
        return text
            .replace(bracketNoise, " ")
            .replace(fileSeparators, " ")
            .replace(trailingNoise, "")
            .replace(repeatedWhitespace, " ")
            .replace(edgePunctuation, "")
            .trim()
    }

    private fun extractLikelyTitle(text: String): String {
        val segments = text.replace(fileSeparators, " ")
            .split(Regex("\\s+-\\s+"))
            .map(::normalizeKeyword)
            .filter { it.isNotBlank() }

        if (segments.size < 2) {
            return normalizeKeyword(text)
        }

        val tail = segments.last()
        if (isNoiseOnly(tail)) {
            return segments.drop(1).dropLast(1).joinToString(" - ")
                .ifBlank { segments.dropLast(1).lastOrNull().orEmpty() }
        }

        return segments.drop(1).joinToString(" - ")
    }

    private fun removeBracketContent(text: String): String =
        text.replace(bracketContent, " ")

    private fun isNoiseOnly(text: String): Boolean =
        normalizeKeyword(text).isBlank()
}

internal data class MatchScore(
    val titleScore: Int,
    val artistScore: Int,
    val albumScore: Int
) {
    val total: Int get() = titleScore + artistScore + albumScore
}

internal object LyricsMatch {
    fun score(query: SongQuery, candidate: LyricsCandidate): MatchScore {
        var title = matchScore(query.title, candidate.title)
        var artist = if (query.artist.isBlank()) 0 else matchArtist(query.artist, candidate.artist)
        val album = if (query.album.isBlank()) 0 else matchScore(query.album, candidate.album)

        if (query.artist.isNotBlank() && artist == 0) {
            artist = -2
        }

        if (query.artist.isBlank() && artist >= 1 && title >= 1) {
            title = max(title, 2)
        }

        return MatchScore(title, artist, album)
    }

    private fun matchArtist(expected: String, actual: String): Int {
        val artistParts = actual.split(Regex("\\s*[,/&、，]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (artistParts.isEmpty()) {
            return matchScore(expected, actual)
        }

        return artistParts.sumOf { matchScore(expected, it) }
    }

    private fun matchScore(expected: String, actual: String): Int {
        val left = normalize(expected)
        val right = normalize(actual)
        if (left.isBlank() || right.isBlank()) return 0
        return when {
            left == right -> 2
            left in right || right in left -> 1
            else -> 0
        }
    }

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("[\\s\\p{Punct}、，（）【】\\[\\]_]+"), "")
            .trim()
    }
}

private object SourceParsers {
    fun cleanText(text: String?): String =
        text.orEmpty()
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .trim()

    fun nodeText(node: JsonNode?, field: String): String = cleanText(node?.path(field)?.asText(""))
}

object LyricsText {
    fun normalize(sourceId: LyricsSourceId, raw: String): String? {
        val cleaned = raw
            .replace("\uFEFF", "")
            .replace("\u0000", "")
            .trim()

        if (cleaned.isBlank() || looksLikeHtml(cleaned)) {
            return null
        }

        val normalized = when (sourceId) {
            LyricsSourceId.Kugou -> normalizeKugou(cleaned)
            LyricsSourceId.Netease -> normalizeNetease(cleaned)
            else -> cleaned
        }.lines()
            .map { it.trimEnd() }
            .filterNot { it.isBlank() }
            .joinToString("\n")
            .trim()

        return normalized.takeIf(::isUsableLyrics)
    }

    private fun normalizeKugou(text: String): String {
        return text.lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isBlank() -> null
                    trimmed.startsWith("[id:", ignoreCase = true) -> null
                    trimmed.startsWith("[hash:", ignoreCase = true) -> null
                    trimmed.startsWith("[sign:", ignoreCase = true) -> null
                    trimmed.startsWith("[qq:", ignoreCase = true) -> null
                    trimmed.startsWith("[total:", ignoreCase = true) -> null
                    trimmed.startsWith("[offset:", ignoreCase = true) -> null
                    trimmed.startsWith("[language:", ignoreCase = true) -> null
                    else -> trimmed.replace(Regex("<\\d+,\\d+,\\d+>"), "")
                }
            }
            .joinToString("\n")
    }

    private fun normalizeNetease(text: String): String {
        val payload = JsonSupport.parse(text) ?: return text
        return payload.path("lrc").path("lyric").asText("").trim().ifBlank { text }
    }

    private fun looksLikeHtml(text: String): Boolean {
        val head = text.take(256).lowercase()
        return "<!doctype html" in head || "<html" in head || "<head" in head || "<body" in head
    }

    private fun isUsableLyrics(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return false
        }

        val hasVisibleText = lines.any { line ->
            line.replace(Regex("\\[[^\\]]*]"), "")
                .replace(Regex("<\\d+,\\d+,\\d+>"), "")
                .trim()
                .any { !it.isWhitespace() }
        }

        return hasVisibleText
    }
}

class KugouLyricsSource : LyricsSource {
    override val id: LyricsSourceId = LyricsSourceId.Kugou

    override fun search(query: SongQuery): List<LyricsCandidate> {
        val millis = System.currentTimeMillis().toString()
        val signatureSource =
            "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt" +
                "bitrate=0" +
                "clienttime=$millis" +
                "clientver=2000" +
                "dfid=-" +
                "inputtype=0" +
                "iscorrection=1" +
                "isfuzzy=0" +
                "keyword=${query.title}" +
                "mid=$millis" +
                "page=1" +
                "pagesize=10" +
                "platform=WebFilter" +
                "privilege_filter=0" +
                "srcappid=2919" +
                "tag=em" +
                "userid=-1" +
                "uuid=$millis" +
                "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
        val signature = Hashing.md5(signatureSource).uppercase()
        val url =
            "https://complexsearch.kugou.com/v2/search/song" +
                "?keyword=${HttpSupport.encode(query.title)}" +
                "&page=1&pagesize=10&bitrate=0&isfuzzy=0&tag=em&inputtype=0&platform=WebFilter" +
                "&userid=-1&clientver=2000&iscorrection=1&privilege_filter=0&srcappid=2919" +
                "&clienttime=$millis&mid=$millis&uuid=$millis&dfid=-&signature=$signature"

        val response = HttpSupport.getJson(url) ?: return emptyList()
        return response.path("data").path("lists")
            .map {
                LyricsCandidate(
                    sourceId = id,
                    id = SourceParsers.nodeText(it, "FileHash"),
                    title = SourceParsers.nodeText(it, "SongName"),
                    artist = SourceParsers.nodeText(it, "SingerName"),
                    album = SourceParsers.nodeText(it, "AlbumName")
                )
            }
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    override fun fetchLyrics(candidate: LyricsCandidate): String? {
        return HttpSupport.getText(
            "http://m.kugou.com/app/i/krc.php?cmd=100&timelength=999999&hash=${HttpSupport.encode(candidate.id)}"
        )?.trim()?.ifBlank { null }
    }
}

class QqMusicLyricsSource : LyricsSource {
    override val id: LyricsSourceId = LyricsSourceId.QQMusic

    override fun search(query: SongQuery): List<LyricsCandidate> {
        val payload = mapOf(
            "comm" to mapOf(
                "wid" to "",
                "tmeAppID" to "qqmusic",
                "authst" to "",
                "uid" to "",
                "gray" to "0",
                "OpenUDID" to "2d484d3157d4ed482e406e6c5fdcf8c3d3275deb",
                "ct" to "6",
                "patch" to "2",
                "psrf_qqopenid" to "",
                "sid" to "",
                "psrf_access_token_expiresAt" to "",
                "cv" to "80600",
                "gzip" to "0",
                "qq" to "",
                "nettype" to "2",
                "psrf_qqunionid" to "",
                "psrf_qqaccess_token" to "",
                "tmeLoginType" to "2"
            ),
            "music.search.SearchCgiService.DoSearchForQQMusicDesktop" to mapOf(
                "module" to "music.search.SearchCgiService",
                "method" to "DoSearchForQQMusicDesktop",
                "param" to mapOf(
                    "num_per_page" to 10,
                    "page_num" to 1,
                    "remoteplace" to "txt.mac.search",
                    "search_type" to 0,
                    "query" to query.title,
                    "grp" to 1,
                    "searchid" to Randoms.uuid(),
                    "nqc_flag" to 0
                )
            )
        )

        val response = HttpSupport.postJson(
            url = "https://u.y.qq.com/cgi-bin/musicu.fcg",
            body = JsonSupport.write(payload),
            headers = mapOf(
                "Referer" to "https://y.qq.com/portal/profile.html",
                "Content-Type" to "json/application;charset=utf-8",
                "User-Agent" to "QQMusic/desktop"
            )
        ) ?: return emptyList()

        return response
            .path("music.search.SearchCgiService.DoSearchForQQMusicDesktop")
            .path("data")
            .path("body")
            .path("song")
            .path("list")
            .map {
                LyricsCandidate(
                    sourceId = id,
                    id = SourceParsers.nodeText(it, "mid"),
                    title = SourceParsers.nodeText(it, "title"),
                    artist = it.path("singer").joinToString(",") { singer ->
                        SourceParsers.nodeText(singer, "name")
                    },
                    album = SourceParsers.nodeText(it.path("album"), "title")
                )
            }
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    override fun fetchLyrics(candidate: LyricsCandidate): String? {
        val response = HttpSupport.getJson(
            "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
                "?g_tk=5381&format=json&inCharset=utf-8&outCharset=utf-8&notice=0" +
                "&platform=h5&needNewCode=1&ct=121&cv=0&songmid=${HttpSupport.encode(candidate.id)}",
            headers = mapOf(
                "Referer" to "http://y.qq.com",
                "User-Agent" to "QQMusic/desktop"
            )
        ) ?: return null

        val encoded = response.path("lyric").asText("")
        if (encoded.isBlank()) return null

        return runCatching {
            String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                .trim()
                .ifBlank { null }
        }.getOrNull()
    }
}

class NeteaseLyricsSource : LyricsSource {
    override val id: LyricsSourceId = LyricsSourceId.Netease

    override fun search(query: SongQuery): List<LyricsCandidate> {
        val response = HttpSupport.getJson(
            "http://music.163.com/api/search/get/" +
                "?s=${HttpSupport.encode(query.title)}&limit=10&type=1&offset=0",
            headers = mapOf(
                "User-Agent" to HttpSupport.browserAgent,
                "Referer" to "https://music.163.com/"
            )
        ) ?: return emptyList()

        return response.path("result").path("songs")
            .map { song ->
                val artistNodes = when {
                    song.path("artists").isArray -> song.path("artists")
                    song.path("ar").isArray -> song.path("ar")
                    else -> null
                }
                val artists = artistNodes?.joinToString(",") { artist ->
                    SourceParsers.nodeText(artist, "name")
                }.orEmpty()
                val albumNode = when {
                    song.path("album").isObject -> song.path("album")
                    song.path("al").isObject -> song.path("al")
                    else -> null
                }
                LyricsCandidate(
                    sourceId = id,
                    id = song.path("id").asText(""),
                    title = SourceParsers.nodeText(song, "name"),
                    artist = artists,
                    album = SourceParsers.nodeText(albumNode, "name")
                )
            }
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    override fun fetchLyrics(candidate: LyricsCandidate): String? {
        fetchText(
            "http://music.163.com/api/song/lyric?os=osx&id=${HttpSupport.encode(candidate.id)}&lv=-1&kv=-1&tv=-1"
        )?.takeIf { LyricsText.normalize(id, it) != null }?.let { return it }

        return fetchText(
            "http://music.163.com/api/song/media?id=${HttpSupport.encode(candidate.id)}"
        )?.takeIf { LyricsText.normalize(id, it) != null }
    }

    private fun fetchText(url: String): String? {
        return HttpSupport.getText(
            url,
            headers = mapOf(
                "User-Agent" to HttpSupport.browserAgent,
                "Referer" to "https://music.163.com/"
            )
        )?.trim()?.ifBlank { null }
    }
}
