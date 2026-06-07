package com.salt.autotagger.spw

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SearchTermsTest {
    @Test
    fun `keeps original title first and extracts likely title from noisy dash format`() {
        val terms = SearchTerms.build(query("歌手 - 歌名 - 无数据", artist = "歌手"))

        assertEquals("歌手 - 歌名 - 无数据", terms.first())
        assertContains(terms, "歌名")
    }

    @Test
    fun `adds fallback without live version marker`() {
        val terms = SearchTerms.build(query("歌名 (Live)"))

        assertEquals("歌名 (Live)", terms.first())
        assertContains(terms, "歌名")
    }

    @Test
    fun `cleans file separators and instrumental marker`() {
        val terms = SearchTerms.build(query("歌名_instrumental"))

        assertEquals("歌名_instrumental", terms.first())
        assertContains(terms, "歌名")
    }

    @Test
    fun `does not over modify normal title`() {
        val terms = SearchTerms.build(query("认真的雪", artist = "薛之谦"))

        assertEquals("认真的雪", terms.first())
        assertContains(terms, "认真的雪")
    }

    private fun query(title: String, artist: String = ""): SongQuery =
        SongQuery(
            title = title,
            artist = artist,
            album = "",
            albumArtist = "",
            filePath = ""
        )
}
