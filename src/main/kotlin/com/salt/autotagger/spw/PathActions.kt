package com.salt.autotagger.spw

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Path

object PathActions {
    fun copyPath(path: Path, label: String): PathActionResult {
        return runCatching {
            val absolutePath = path.toAbsolutePath().toString()
            val selection = StringSelection(absolutePath)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            PathActionResult.Success(I18n.pathCopied(label, absolutePath))
        }.getOrElse { error ->
            PathActionResult.Failure(I18n.pathCopyFailed(label, path.toAbsolutePath(), error.message))
        }
    }

    fun openDirectory(path: Path, label: String): PathActionResult {
        val absolutePath = path.toAbsolutePath()
        return runCatching {
            check(Desktop.isDesktopSupported()) { "Desktop API is not supported" }
            Desktop.getDesktop().open(absolutePath.toFile())
            PathActionResult.Success(I18n.pathOpened(label, absolutePath))
        }.getOrElse { error ->
            PathActionResult.Failure(I18n.pathOpenFailed(label, absolutePath, error.message))
        }
    }

    fun revealFile(path: Path, label: String): PathActionResult {
        val absolutePath = path.toAbsolutePath()
        return runCatching {
            if (tryRevealInExplorer(absolutePath)) {
                PathActionResult.Success(I18n.pathRevealed(label, absolutePath))
            } else {
                openDirectory(absolutePath.parent ?: absolutePath, label)
            }
        }.getOrElse { error ->
            PathActionResult.Failure(I18n.pathOpenFailed(label, absolutePath, error.message))
        }
    }

    private fun tryRevealInExplorer(path: Path): Boolean {
        val file = path.toFile()
        val target = if (file.exists()) file else file.parentFile ?: file
        if (!target.exists()) {
            return false
        }
        val process = ProcessBuilder(
            "explorer.exe",
            if (file.exists()) "/select,${file.absolutePath}" else target.absolutePath
        )
            .redirectErrorStream(true)
            .start()
        val exitCode = process.waitFor()
        return exitCode == 0
    }
}

sealed class PathActionResult {
    data class Success(val message: String) : PathActionResult()
    data class Failure(val message: String) : PathActionResult()
}
