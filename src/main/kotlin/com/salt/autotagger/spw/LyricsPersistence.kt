package com.salt.autotagger.spw

import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.audio.exceptions.CannotWriteException
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object LocalLyricsFiles {
    fun readLyricsFile(path: Path): String? {
        return try {
            if (!Files.exists(path) || Files.isDirectory(path)) {
                null
            } else {
                readTextWithFallback(path)?.trim()?.ifBlank { null }
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun readTextWithFallback(path: Path): String? {
        val raw = Files.readAllBytes(path)
        val charsets = listOf(
            StandardCharsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.defaultCharset()
        ).distinct()

        return charsets.firstNotNullOfOrNull { charset ->
            runCatching { raw.toString(charset) }.getOrNull()
        }
    }
}

sealed class SaveOutcome {
    data object Saved : SaveOutcome()
    data class SkippedExisting(val reason: String) : SaveOutcome()
    data class Failed(val reason: String) : SaveOutcome()
}

object LrcPersistence {
    fun saveIfAbsent(songPath: String, lyrics: String): SaveOutcome {
        return try {
            val audioPath = Path.of(songPath)
            val parent = audioPath.parent ?: return SaveOutcome.Failed("no_parent_directory")
            val lrcPath = parent.resolve(audioPath.fileName.toString().substringBeforeLast('.') + ".lrc")
            if (Files.exists(lrcPath)) {
                SaveOutcome.SkippedExisting("file_exists")
            } else {
                Files.writeString(lrcPath, lyrics, StandardCharsets.UTF_8)
                SaveOutcome.Saved
            }
        } catch (_: Exception) {
            SaveOutcome.Failed("write_failed")
        }
    }
}

object EmbeddedLyricsReader {
    fun read(songPath: String): String? {
        return try {
            val audioFile = AudioFileIO.read(Path.of(songPath).toFile())
            val tag = audioFile.tag ?: return null
            tag.getFirst(FieldKey.LYRICS).trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}

object TagLyricsWriter {
    private val supportedExtensions = setOf("mp3", "flac", "m4a")

    fun writeIfMissing(songPath: String, lyrics: String): SaveOutcome {
        return try {
            val path = Path.of(songPath)
            val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
            if (extension !in supportedExtensions) {
                return SaveOutcome.Failed("unsupported_format")
            }

            val audioFile = AudioFileIO.read(path.toFile())
            val tag = audioFile.tagOrCreateAndSetDefault
            val existing = tag.getFirst(FieldKey.LYRICS).trim()
            if (existing.isNotBlank()) {
                SaveOutcome.SkippedExisting("tag_has_lyrics")
            } else {
                tag.setField(FieldKey.LYRICS, lyrics)
                audioFile.commit()
                SaveOutcome.Saved
            }
        } catch (_: CannotReadException) {
            SaveOutcome.Failed("tag_read_failed")
        } catch (_: CannotWriteException) {
            SaveOutcome.Failed("tag_commit_failed")
        } catch (_: Exception) {
            SaveOutcome.Failed("tag_write_failed")
        }
    }
}
