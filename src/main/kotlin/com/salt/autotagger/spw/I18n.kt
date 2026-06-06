package com.salt.autotagger.spw

import java.nio.file.Path

enum class UiLanguage {
    ZH_CN,
    EN_US
}

enum class TextKey {
    PluginStarted,
    PluginStopped,
    PluginDeleted,
    PluginUpdated,
    OverridePath,
    LogPath,
    PathCopied,
    PathCopyFailed,
    PathOpened,
    PathOpenFailed,
    PathRevealed,
    LogEmpty,
    LogCleared,
    LogClearFailed,
    LogConfigHint,
    LogRecentTitle,
    ConfigLanguageNote,
    LocalLyricsLoaded,
    OnlineLyricsLoaded,
    OnlineLyricsFailed,
    LyricsUnavailable,
    LrcSaved,
    LrcSkipped,
    TagWritten,
    TagSkipped,
    PluginVersion,
    SourceProbeStarted,
    SourceProbeAlreadyRunning,
    SourceProbeNoEnabledSources
}

object I18n {
    fun text(key: TextKey): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> english(key)
            UiLanguage.ZH_CN -> chinese(key)
        }
    }

    fun overridePath(path: Path): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Override folder: $path"
            UiLanguage.ZH_CN -> "覆盖目录：$path"
        }
    }

    fun logPath(path: Path): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Debug log file: $path"
            UiLanguage.ZH_CN -> "调试日志文件：$path"
        }
    }

    fun recentLog(text: String): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Recent logs:\n$text"
            UiLanguage.ZH_CN -> "最近日志：\n$text"
        }
    }

    fun pluginVersion(version: String): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Plugin version: $version"
            UiLanguage.ZH_CN -> "插件版本：$version"
        }
    }

    fun sourceProbeStarted(): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Source probe started. Results will be written to the debug log."
            UiLanguage.ZH_CN -> "已开始检测歌词源，详细结果会写入调试日志。"
        }
    }

    fun sourceProbeSummary(summary: SourceProbeSummary): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US ->
                "Probe finished: ${summary.total} sources, ${summary.successCount} succeeded, ${summary.failedCount} failed."

            UiLanguage.ZH_CN ->
                "检测完成：共 ${summary.total} 个源，成功 ${summary.successCount} 个，失败 ${summary.failedCount} 个。"
        }
    }

    fun pathCopied(label: String, path: String): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "$label copied: $path"
            UiLanguage.ZH_CN -> "已复制${label}：$path"
        }
    }

    fun pathCopyFailed(label: String, path: Path, reason: String?): String {
        val detail = reason?.takeIf { it.isNotBlank() } ?: text(TextKey.PathCopyFailed)
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Failed to copy $label: $path ($detail)"
            UiLanguage.ZH_CN -> "复制${label}失败：$path（$detail）"
        }
    }

    fun pathOpened(label: String, path: Path): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Opened $label: $path"
            UiLanguage.ZH_CN -> "已打开${label}：$path"
        }
    }

    fun pathRevealed(label: String, path: Path): String {
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Revealed $label: $path"
            UiLanguage.ZH_CN -> "已定位${label}：$path"
        }
    }

    fun pathOpenFailed(label: String, path: Path, reason: String?): String {
        val detail = reason?.takeIf { it.isNotBlank() } ?: text(TextKey.PathOpenFailed)
        return when (SaltAutoTaggerRuntime.settings.language) {
            UiLanguage.EN_US -> "Failed to open $label: $path ($detail)"
            UiLanguage.ZH_CN -> "打开${label}失败：$path（$detail）"
        }
    }

    private fun chinese(key: TextKey): String {
        return when (key) {
            TextKey.PluginStarted -> "Salt Auto Tagger 已启动"
            TextKey.PluginStopped -> "Salt Auto Tagger 已停止"
            TextKey.PluginDeleted -> "Salt Auto Tagger 已删除"
            TextKey.PluginUpdated -> "Salt Auto Tagger 已更新"
            TextKey.OverridePath -> "覆盖目录"
            TextKey.LogPath -> "调试日志文件"
            TextKey.PathCopied -> "路径已复制"
            TextKey.PathCopyFailed -> "复制路径失败"
            TextKey.PathOpened -> "路径已打开"
            TextKey.PathOpenFailed -> "打开路径失败"
            TextKey.PathRevealed -> "已定位文件"
            TextKey.LogEmpty -> "当前还没有调试日志"
            TextKey.LogCleared -> "调试日志已清空"
            TextKey.LogClearFailed -> "清空调试日志失败"
            TextKey.LogConfigHint -> "启用调试日志后，插件会把关键查询流程写入日志文件。"
            TextKey.LogRecentTitle -> "最近日志"
            TextKey.ConfigLanguageNote -> "语言切换仅影响运行时提示与日志提示；设置页文案为静态渲染，固定使用双语标题。"
            TextKey.LocalLyricsLoaded -> "已加载本地歌词"
            TextKey.OnlineLyricsLoaded -> "已通过内置歌词源加载歌词"
            TextKey.OnlineLyricsFailed -> "内置歌词源未返回歌词"
            TextKey.LyricsUnavailable -> "当前未获取到可显示歌词"
            TextKey.LrcSaved -> "已保存同目录 LRC"
            TextKey.LrcSkipped -> "同目录 LRC 已存在，已跳过保存"
            TextKey.TagWritten -> "已写回歌曲标签歌词"
            TextKey.TagSkipped -> "歌曲标签歌词未写入"
            TextKey.PluginVersion -> "插件版本"
            TextKey.SourceProbeStarted -> "已开始检测歌词源，详细结果会写入调试日志。"
            TextKey.SourceProbeAlreadyRunning -> "歌词源检测正在进行中，请稍后再试。"
            TextKey.SourceProbeNoEnabledSources -> "当前没有启用的在线歌词源可供检测。"
        }
    }

    private fun english(key: TextKey): String {
        return when (key) {
            TextKey.PluginStarted -> "Salt Auto Tagger started"
            TextKey.PluginStopped -> "Salt Auto Tagger stopped"
            TextKey.PluginDeleted -> "Salt Auto Tagger deleted"
            TextKey.PluginUpdated -> "Salt Auto Tagger updated"
            TextKey.OverridePath -> "Override folder"
            TextKey.LogPath -> "Debug log file"
            TextKey.PathCopied -> "Path copied"
            TextKey.PathCopyFailed -> "Failed to copy path"
            TextKey.PathOpened -> "Opened path"
            TextKey.PathOpenFailed -> "Failed to open path"
            TextKey.PathRevealed -> "Revealed file"
            TextKey.LogEmpty -> "No debug logs yet"
            TextKey.LogCleared -> "Debug log cleared"
            TextKey.LogClearFailed -> "Failed to clear debug log"
            TextKey.LogConfigHint -> "When debug logging is enabled, the plugin writes key lookup steps to the log file."
            TextKey.LogRecentTitle -> "Recent logs"
            TextKey.ConfigLanguageNote -> "Language switching affects runtime messages and log prompts only; the settings page is statically rendered and stays bilingual."
            TextKey.LocalLyricsLoaded -> "Loaded local lyrics"
            TextKey.OnlineLyricsLoaded -> "Loaded lyrics from built-in sources"
            TextKey.OnlineLyricsFailed -> "Built-in lyrics sources returned no lyrics"
            TextKey.LyricsUnavailable -> "No displayable lyrics were found"
            TextKey.LrcSaved -> "Saved sidecar LRC"
            TextKey.LrcSkipped -> "Sidecar LRC already exists, skipped"
            TextKey.TagWritten -> "Lyrics were written back to file tags"
            TextKey.TagSkipped -> "Lyrics were not written to file tags"
            TextKey.PluginVersion -> "Plugin version"
            TextKey.SourceProbeStarted -> "Source probe started. Results will be written to the debug log."
            TextKey.SourceProbeAlreadyRunning -> "A source probe is already running."
            TextKey.SourceProbeNoEnabledSources -> "No enabled online lyrics sources are available for probing."
        }
    }
}
