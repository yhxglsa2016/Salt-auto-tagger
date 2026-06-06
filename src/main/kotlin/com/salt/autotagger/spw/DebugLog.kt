@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)

package com.salt.autotagger.spw

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DebugLog {
    private val lock = ReentrantLock()
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private const val maxLines = 800

    fun info(message: String) = write("INFO", message)

    fun warn(message: String) = write("WARN", message)

    fun error(message: String) = write("ERROR", message)

    fun clear(): Boolean {
        return runCatching {
            lock.withLock {
                val path = logPath()
                if (Files.exists(path)) {
                    Files.delete(path)
                }
            }
        }.isSuccess
    }

    fun recentSummary(maxLines: Int = 10, maxChars: Int = 500): String {
        val fallback = I18n.text(TextKey.LogEmpty)
        val text = runCatching {
            lock.withLock {
                val path = logPath()
                if (!Files.exists(path)) {
                    return@withLock fallback
                }
                Files.readAllLines(path, StandardCharsets.UTF_8)
                    .takeLast(maxLines)
                    .joinToString("\n")
                    .trim()
                    .ifBlank { fallback }
            }
        }.getOrDefault(fallback)

        return if (text.length <= maxChars) {
            text
        } else {
            text.take(maxChars - 3) + "..."
        }
    }

    fun logPath(): Path {
        val configPath = SaltAutoTaggerRuntime.configManager.getConfig("lyrics.json").getConfigPath()
        Files.createDirectories(configPath.parent)
        return configPath.parent.resolve("salt-auto-tagger-debug.log")
    }

    private fun write(level: String, message: String) {
        if (!SaltAutoTaggerRuntime.isReady()) {
            return
        }
        if (!SaltAutoTaggerRuntime.settings.debugLogging) {
            return
        }

        runCatching {
            lock.withLock {
                val path = logPath()
                val line = buildString {
                    append(LocalDateTime.now().format(timestampFormatter))
                    append(" [")
                    append(level)
                    append("] ")
                    append(cleanMessage(message))
                    append('\n')
                }

                Files.writeString(
                    path,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                )

                trimIfNeeded(path)
            }
        }
    }

    private fun trimIfNeeded(path: Path) {
        val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
        if (lines.size <= maxLines) {
            return
        }

        Files.write(
            path,
            lines.takeLast(maxLines),
            StandardCharsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    private fun cleanMessage(message: String): String =
        message
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    fun field(value: Any?): String =
        value?.toString().orEmpty()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('|', '/')
            .replace(Regex("\\s+"), " ")
            .trim()
}
