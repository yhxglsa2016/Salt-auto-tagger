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

        return resolveSourceOrderValues(
            rankedValues = rankedValues,
            legacyValue = helper.get("lyrics.source_order", "default"),
            defaultSourceOrder = defaultSourceOrder
        )
    }

    internal fun resolveSourceOrderValues(
        rankedValues: List<String>,
        legacyValue: String,
        defaultSourceOrder: List<LyricsSourceId>
    ): List<LyricsSourceId> {
        val normalizedRanks = rankedValues.map { it.trim().lowercase() }
        val hasRankConfiguration = normalizedRanks.any { it != "auto" }
        if (!hasRankConfiguration) {
            return resolveLegacySourceOrder(legacyValue, defaultSourceOrder)
        }

        val resolved = linkedSetOf<LyricsSourceId>()
        normalizedRanks.forEach { value ->
            val sourceId = sourceConfigValues[value] ?: return@forEach
            resolved.add(sourceId)
        }
        defaultSourceOrder.forEach(resolved::add)
        return resolved.toList().ifEmpty { defaultSourceOrder }
    }

    private fun resolveLegacySourceOrder(
        value: String,
        defaultSourceOrder: List<LyricsSourceId>
    ): List<LyricsSourceId> {
        return when (value.trim().lowercase()) {
            "default" -> defaultSourceOrder
            else -> defaultSourceOrder
        }
    }
}
