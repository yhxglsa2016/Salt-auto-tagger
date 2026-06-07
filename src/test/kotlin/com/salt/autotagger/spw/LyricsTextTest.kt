package com.salt.autotagger.spw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsTextTest {
    @Test
    fun `rejects blank and html lyrics`() {
        assertNull(LyricsText.normalize(LyricsSourceId.QQMusic, "   "))
        assertNull(LyricsText.normalize(LyricsSourceId.QQMusic, "<html><body>blocked</body></html>"))
    }

    @Test
    fun `removes kugou metadata and inline timing markers`() {
        val raw = """
            [id:123]
            [00:01.00]<0,100,0>第一句
            [00:02.00]第二句
        """.trimIndent()

        assertEquals(
            "[00:01.00]第一句\n[00:02.00]第二句",
            LyricsText.normalize(LyricsSourceId.Kugou, raw)
        )
    }

    @Test
    fun `extracts netease lrc json payload`() {
        val raw = """{"lrc":{"lyric":"[00:01.00]第一句\n[00:02.00]第二句"}}"""

        assertEquals(
            "[00:01.00]第一句\n[00:02.00]第二句",
            LyricsText.normalize(LyricsSourceId.Netease, raw)
        )
    }
}
