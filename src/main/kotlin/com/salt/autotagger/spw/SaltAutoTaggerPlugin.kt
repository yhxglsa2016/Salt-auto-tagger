@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)

package com.salt.autotagger.spw

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import java.util.function.Consumer

class SaltAutoTaggerPlugin(
    pluginContext: PluginContext
) : SpwPlugin(pluginContext) {
    private val configListener = Consumer<ConfigHelper> {
        SaltAutoTaggerRuntime.reload()
    }

    override fun start() {
        SaltAutoTaggerRuntime.initialize()
        SaltAutoTaggerRuntime.configManager.addConfigChangeListener("lyrics.json", configListener)
        DebugLog.info("plugin_started | version=${SaltAutoTaggerRuntime.pluginVersion()}")
        WorkshopApi.ui.toast(I18n.text(TextKey.PluginStarted), WorkshopApi.Ui.ToastType.Success)
    }

    override fun stop() {
        SaltAutoTaggerRuntime.configManager.removeConfigChangeListener(configListener)
        DebugLog.info("plugin_stopped | version=${SaltAutoTaggerRuntime.pluginVersion()}")
        WorkshopApi.ui.toast(I18n.text(TextKey.PluginStopped), WorkshopApi.Ui.ToastType.Warning)
    }

    override fun delete() {
        DebugLog.warn("plugin_deleted | version=${SaltAutoTaggerRuntime.pluginVersion()}")
        WorkshopApi.ui.toast(I18n.text(TextKey.PluginDeleted), WorkshopApi.Ui.ToastType.Error)
    }

    override fun update() {
        SaltAutoTaggerRuntime.reload()
        DebugLog.info("plugin_updated | version=${SaltAutoTaggerRuntime.pluginVersion()}")
        WorkshopApi.ui.toast(I18n.text(TextKey.PluginUpdated), WorkshopApi.Ui.ToastType.Success)
    }

    companion object {
        @JvmStatic
        @JvmName("copyOverridePath")
        fun copyOverridePath() {
            toast(PathActions.copyPath(SaltAutoTaggerRuntime.overrideDirectory(), I18n.text(TextKey.OverridePath)))
        }

        @JvmStatic
        @JvmName("openOverrideFolder")
        fun openOverrideFolder() {
            toast(PathActions.openDirectory(SaltAutoTaggerRuntime.overrideDirectory(), I18n.text(TextKey.OverridePath)))
        }

        @JvmStatic
        @JvmName("copyLogPath")
        fun copyLogPath() {
            toast(PathActions.copyPath(DebugLog.logPath(), I18n.text(TextKey.LogPath)))
        }

        @JvmStatic
        @JvmName("showRecentLog")
        fun showRecentLog() {
            WorkshopApi.ui.toast(
                I18n.recentLog(DebugLog.recentSummary()),
                WorkshopApi.Ui.ToastType.Success
            )
        }

        @JvmStatic
        @JvmName("clearLog")
        fun clearLog() {
            val success = DebugLog.clear()
            WorkshopApi.ui.toast(
                if (success) I18n.text(TextKey.LogCleared) else I18n.text(TextKey.LogClearFailed),
                if (success) WorkshopApi.Ui.ToastType.Success else WorkshopApi.Ui.ToastType.Error
            )
        }

        @JvmStatic
        @JvmName("showPluginVersion")
        fun showPluginVersion() {
            WorkshopApi.ui.toast(
                I18n.pluginVersion(SaltAutoTaggerRuntime.pluginVersion()),
                WorkshopApi.Ui.ToastType.Success
            )
        }

        @JvmStatic
        @JvmName("probeSources")
        fun probeSources() {
            when (SourceProbeRunner.startAsync(::showProbeFinished)) {
                ProbeStartResult.Started -> {
                    WorkshopApi.ui.toast(
                        I18n.sourceProbeStarted(),
                        WorkshopApi.Ui.ToastType.Success
                    )
                }

                ProbeStartResult.AlreadyRunning -> {
                    WorkshopApi.ui.toast(
                        I18n.text(TextKey.SourceProbeAlreadyRunning),
                        WorkshopApi.Ui.ToastType.Warning
                    )
                }
            }
        }

        private fun showProbeFinished(summary: SourceProbeSummary) {
            val hasResults = summary.total > 0
            WorkshopApi.ui.toast(
                if (hasResults) I18n.sourceProbeSummary(summary) else I18n.text(TextKey.SourceProbeNoEnabledSources),
                if (summary.failedCount == 0) WorkshopApi.Ui.ToastType.Success else WorkshopApi.Ui.ToastType.Warning
            )
        }

        private fun toast(result: PathActionResult) {
            when (result) {
                is PathActionResult.Success -> WorkshopApi.ui.toast(result.message, WorkshopApi.Ui.ToastType.Success)
                is PathActionResult.Failure -> WorkshopApi.ui.toast(result.message, WorkshopApi.Ui.ToastType.Error)
            }
        }
    }
}
