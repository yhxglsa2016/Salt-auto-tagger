package com.salt.autotagger.spw

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import com.xuncorp.spw.workshop.api.WorkshopApi
import org.pf4j.Extension

@Extension
class LyricsExtension : PlaybackExtensionPoint {
    override fun onBeforeLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        val settings = SaltAutoTaggerRuntime.settings
        if (!settings.enabled || settings.loadStage == LoadStage.AFTER) {
            DebugLog.info(
                "hook_skipped | hook=before | enabled=${settings.enabled} | loadStage=${settings.loadStage} | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}"
            )
            return null
        }

        DebugLog.info("hook_start | hook=before | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
        val result = LyricsResolver.resolve(mediaItem)
        if (result == null && settings.loadStage == LoadStage.BEFORE) {
            DebugLog.warn("hook_failed | hook=before | reason=no_lyrics | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
            maybeToast(I18n.text(TextKey.LyricsUnavailable))
        }

        return result?.also {
            DebugLog.info("hook_resolved | hook=before | origin=${it.origin} | source=${it.sourceId} | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
            when (it.origin) {
                LyricsOrigin.Sidecar,
                LyricsOrigin.Override,
                LyricsOrigin.EmbeddedTag -> maybeToast(I18n.text(TextKey.LocalLyricsLoaded))

                LyricsOrigin.Online -> maybeToast(I18n.text(TextKey.OnlineLyricsLoaded))
            }
        }?.lyrics
    }

    override fun onAfterLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        val settings = SaltAutoTaggerRuntime.settings
        if (!settings.enabled || settings.loadStage == LoadStage.BEFORE) {
            DebugLog.info(
                "hook_skipped | hook=after | enabled=${settings.enabled} | loadStage=${settings.loadStage} | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}"
            )
            return null
        }

        DebugLog.info("hook_start | hook=after | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
        val result = LyricsResolver.resolve(mediaItem)
        if (result == null) {
            DebugLog.warn("hook_failed | hook=after | reason=no_lyrics | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
            maybeToast(I18n.text(TextKey.LyricsUnavailable))
        }

        return result?.also { resolved ->
            DebugLog.info("hook_resolved | hook=after | origin=${resolved.origin} | source=${resolved.sourceId} | title=${DebugLog.field(mediaItem.title)} | artist=${DebugLog.field(mediaItem.artist)} | path=${DebugLog.field(mediaItem.path)}")
            when (result.origin) {
                LyricsOrigin.Sidecar,
                LyricsOrigin.Override,
                LyricsOrigin.EmbeddedTag -> maybeToast(I18n.text(TextKey.LocalLyricsLoaded))

                LyricsOrigin.Online -> maybeToast(I18n.text(TextKey.OnlineLyricsLoaded))
            }
        }?.lyrics
    }

    private fun maybeToast(text: String) {
        if (SaltAutoTaggerRuntime.settings.showToast) {
            WorkshopApi.ui.toast(text, WorkshopApi.Ui.ToastType.Success)
        }
    }
}
