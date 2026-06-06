@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)

package com.salt.autotagger.spw

private val sourceConfigValues = mapOf(
    "kugou" to LyricsSourceId.Kugou,
    "qmusic" to LyricsSourceId.QQMusic,
    "netease" to LyricsSourceId.Netease
)

object SourceOrderSupport {
    fun resolveSourceOrder(
        helper: com.xuncorp.spw.workshop.api.config.ConfigHelper,
        defaultSourceOrder: List<LyricsSourceId>
    ): List<LyricsSourceId> {
        val rankedValues = (1..defaultSourceOrder.size).map { rank ->
            helper.get("lyrics.source_order.rank_$rank", "auto")
                .trim()
                .lowercase()
        }

        val hasRankConfiguration = rankedValues.any { it != "auto" }
        if (!hasRankConfiguration) {
            return resolveLegacySourceOrder(helper, defaultSourceOrder)
        }

        val resolved = linkedSetOf<LyricsSourceId>()
        rankedValues.forEach { value ->
            val sourceId = sourceConfigValues[value] ?: return@forEach
            resolved.add(sourceId)
        }
        defaultSourceOrder.forEach(resolved::add)
        return resolved.toList().ifEmpty { defaultSourceOrder }
    }

    private fun resolveLegacySourceOrder(
        helper: com.xuncorp.spw.workshop.api.config.ConfigHelper,
        defaultSourceOrder: List<LyricsSourceId>
    ): List<LyricsSourceId> {
        return when (helper.get("lyrics.source_order", "default").trim().lowercase()) {
            "default" -> defaultSourceOrder
            else -> defaultSourceOrder
        }
    }
}
