package com.salt.autotagger.spw

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceOrderSupportTest {
    private val defaults = listOf(
        LyricsSourceId.Kugou,
        LyricsSourceId.QQMusic,
        LyricsSourceId.Netease
    )

    @Test
    fun `all auto ranks keep default order`() {
        val order = SourceOrderSupport.resolveSourceOrderValues(
            rankedValues = listOf("auto", "auto", "auto"),
            legacyValue = "default",
            defaultSourceOrder = defaults
        )

        assertEquals(defaults, order)
    }

    @Test
    fun `explicit ranks are applied and missing ranks are filled by default order`() {
        val order = SourceOrderSupport.resolveSourceOrderValues(
            rankedValues = listOf("netease", "auto", "qmusic"),
            legacyValue = "default",
            defaultSourceOrder = defaults
        )

        assertEquals(
            listOf(LyricsSourceId.Netease, LyricsSourceId.QQMusic, LyricsSourceId.Kugou),
            order
        )
    }

    @Test
    fun `duplicate ranks are de duplicated`() {
        val order = SourceOrderSupport.resolveSourceOrderValues(
            rankedValues = listOf("qmusic", "qmusic", "kugou"),
            legacyValue = "default",
            defaultSourceOrder = defaults
        )

        assertEquals(
            listOf(LyricsSourceId.QQMusic, LyricsSourceId.Kugou, LyricsSourceId.Netease),
            order
        )
    }
}
