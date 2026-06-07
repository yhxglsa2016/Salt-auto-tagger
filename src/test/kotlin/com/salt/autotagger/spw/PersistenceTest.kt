package com.salt.autotagger.spw

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PersistenceTest {
    @Test
    fun `lrc persistence saves once and skips existing file`() {
        val dir = Files.createTempDirectory("salt-auto-tagger-test")
        val audioPath = dir.resolve("song.mp3")
        Files.writeString(audioPath, "placeholder")

        assertEquals(SaveOutcome.Saved, LrcPersistence.saveIfAbsent(audioPath.toString(), "[00:00.00]line"))
        assertIs<SaveOutcome.SkippedExisting>(LrcPersistence.saveIfAbsent(audioPath.toString(), "[00:00.00]line"))
    }

    @Test
    fun `tag writer rejects unsupported format before touching file`() {
        val dir = Files.createTempDirectory("salt-auto-tagger-test")
        val audioPath = dir.resolve("song.wav")
        Files.writeString(audioPath, "placeholder")

        assertEquals(
            SaveOutcome.Failed("unsupported_format"),
            TagLyricsWriter.writeIfMissing(audioPath.toString(), "[00:00.00]line")
        )
    }
}
