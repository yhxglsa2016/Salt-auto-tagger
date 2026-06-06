@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)

package com.salt.autotagger.spw

import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

object SaltAutoTaggerRuntime {
    private val defaultSourceOrder = listOf(
        LyricsSourceId.Kugou,
        LyricsSourceId.QQMusic,
        LyricsSourceId.Netease
    )
    private val resolvedPluginVersion: String by lazy {
        SaltAutoTaggerRuntime::class.java.classLoader
            ?.getResourceAsStream("plugin_version.txt")
            ?.bufferedReader()
            ?.use { it.readText().trim() }
            ?.ifBlank { "unknown" }
            ?: "unknown"
    }

    lateinit var configManager: ConfigManager
        private set

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    var settings: SaltAutoTaggerSettings = SaltAutoTaggerSettings()
        private set

    fun initialize() {
        configManager = WorkshopApi.manager.createConfigManager()
        reload()
        initialized = true
    }

    fun reload() {
        val helper = configManager.getConfig("lyrics.json")
        helper.reload()
        settings = SaltAutoTaggerSettings.fromConfig(helper, defaultSourceOrder)
    }

    fun isReady(): Boolean = initialized

    fun pluginVersion(): String = resolvedPluginVersion

    fun overrideDirectory(): Path {
        val configPath = configManager.getConfig("lyrics.json").getConfigPath()
        val path = configPath.parent.resolve(settings.overrideFolderName)
        Files.createDirectories(path)
        return path
    }
}

data class SaltAutoTaggerSettings(
    val language: UiLanguage = UiLanguage.ZH_CN,
    val enabled: Boolean = true,
    val loadStage: LoadStage = LoadStage.BOTH,
    val preferSidecar: Boolean = true,
    val preferOverrideFolder: Boolean = true,
    val onlineEnabled: Boolean = true,
    val enabledSources: Set<LyricsSourceId> = setOf(
        LyricsSourceId.Kugou,
        LyricsSourceId.QQMusic,
        LyricsSourceId.Netease
    ),
    val sourceOrder: List<LyricsSourceId> = listOf(
        LyricsSourceId.Kugou,
        LyricsSourceId.QQMusic,
        LyricsSourceId.Netease
    ),
    val saveMode: SaveMode = SaveMode.DISPLAY_ONLY,
    val overrideFolderName: String = "overrides",
    val sourceProbeKeyword: String = "",
    val showToast: Boolean = false,
    val debugLogging: Boolean = false
) {
    fun enabledSourceOrder(): List<LyricsSourceId> = sourceOrder.filter { it in enabledSources }

    companion object {
        fun fromConfig(helper: ConfigHelper, defaultSourceOrder: List<LyricsSourceId>): SaltAutoTaggerSettings {
            val language = when (helper.get("ui.language", "zh-CN")) {
                "en-US" -> UiLanguage.EN_US
                else -> UiLanguage.ZH_CN
            }

            val loadStage = when (helper.get("lyrics.load_stage", "both")) {
                "before" -> LoadStage.BEFORE
                "after" -> LoadStage.AFTER
                else -> LoadStage.BOTH
            }

            val saveMode = when (helper.get("lyrics.save_mode", "display_only")) {
                "save_lrc" -> SaveMode.SAVE_LRC
                "write_tag" -> SaveMode.WRITE_TAG
                else -> SaveMode.DISPLAY_ONLY
            }

            val enabledSources = buildSet {
                if (helper.get("lyrics.source.kugou", true)) add(LyricsSourceId.Kugou)
                if (helper.get("lyrics.source.qmusic", true)) add(LyricsSourceId.QQMusic)
                if (helper.get("lyrics.source.netease", true)) add(LyricsSourceId.Netease)
            }

            val sourceOrder = SourceOrderSupport.resolveSourceOrder(helper, defaultSourceOrder)

            return SaltAutoTaggerSettings(
                language = language,
                enabled = helper.get("lyrics.enabled", true),
                loadStage = loadStage,
                preferSidecar = helper.get("lyrics.prefer_sidecar", true),
                preferOverrideFolder = helper.get("lyrics.prefer_override_folder", true),
                onlineEnabled = helper.get("lyrics.online_enabled", true),
                enabledSources = enabledSources,
                sourceOrder = sourceOrder,
                saveMode = saveMode,
                overrideFolderName = helper.get("lyrics.override_folder_name", "overrides")
                    .trim()
                    .ifBlank { "overrides" },
                sourceProbeKeyword = helper.get("debug.source_probe.keyword", "")
                    .trim(),
                showToast = helper.get("lyrics.show_toast", false),
                debugLogging = helper.get("debug.logging.enabled", false)
            )
        }
    }
}

enum class LoadStage {
    BEFORE,
    AFTER,
    BOTH
}

enum class SaveMode {
    DISPLAY_ONLY,
    SAVE_LRC,
    WRITE_TAG
}

enum class LyricsSourceId {
    Kugou,
    QQMusic,
    Netease
}
